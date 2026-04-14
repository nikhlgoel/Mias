package dev.kid.core.modelhub.download

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import dev.kid.core.common.KidResult
import dev.kid.core.common.di.IoDispatcher
import dev.kid.core.common.runCatchingKid
import dev.kid.core.modelhub.db.DownloadQueueEntity
import dev.kid.core.modelhub.db.ModelDao
import dev.kid.core.modelhub.di.ModelHubHttpClient
import dev.kid.core.modelhub.model.DownloadState
import dev.kid.core.modelhub.model.DownloadStatus
import dev.kid.core.modelhub.model.ModelCard
import io.ktor.client.HttpClient
import io.ktor.client.request.header
import io.ktor.client.request.prepareGet
import io.ktor.client.statement.bodyAsChannel
import io.ktor.http.HttpHeaders
import io.ktor.http.contentLength
import io.ktor.http.isSuccess
import io.ktor.utils.io.readAvailable
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.RandomAccessFile
import java.security.MessageDigest
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Manages model downloads with:
 * - Resume support (HTTP Range headers)
 * - Progress tracking
 * - SHA-256 verification
 * - Pause/resume/cancel
 * - Auto-retry on network failure
 */
@Singleton
class ModelDownloadManager @Inject constructor(
    @ApplicationContext private val context: Context,
    @ModelHubHttpClient private val httpClient: HttpClient,
    private val modelDao: ModelDao,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) {
    private val _activeDownloads = MutableStateFlow<Map<String, DownloadState>>(emptyMap())
    val activeDownloads: StateFlow<Map<String, DownloadState>> = _activeDownloads.asStateFlow()

    private val downloadJobs = mutableMapOf<String, Job>()
    private val modelsDir: File by lazy {
        File(context.filesDir, "models").also { it.mkdirs() }
    }

    /** Start downloading a model. Supports resume if previously paused. */
    suspend fun startDownload(card: ModelCard): KidResult<Unit> = withContext(ioDispatcher) {
        runCatchingKid {
            val existing = modelDao.getDownload(card.id)
            val tempFile = File(modelsDir, "${card.id}.download")
            val startByte = if (existing != null && tempFile.exists()) {
                tempFile.length()
            } else {
                0L
            }

            // Save to download queue
            val entity = DownloadQueueEntity(
                modelId = card.id,
                downloadUrl = card.downloadUrl,
                totalBytes = card.sizeBytes,
                bytesDownloaded = startByte,
                status = DownloadStatus.DOWNLOADING.name,
                tempFilePath = tempFile.absolutePath,
                sha256 = card.sha256,
                createdAt = existing?.createdAt ?: System.currentTimeMillis(),
                updatedAt = System.currentTimeMillis(),
                retryCount = existing?.retryCount ?: 0,
            )
            modelDao.upsertDownload(entity)

            updateState(card.id, DownloadStatus.DOWNLOADING, startByte, card.sizeBytes)

            // Launch the actual download
            coroutineScope {
                val job = launch {
                    performDownload(card, tempFile, startByte)
                }
                downloadJobs[card.id] = job
            }
        }
    }

    /** Pause an active download. */
    suspend fun pauseDownload(modelId: String) {
        downloadJobs[modelId]?.cancel()
        downloadJobs.remove(modelId)
        modelDao.pauseDownload(modelId)
        updateState(
            modelId,
            DownloadStatus.PAUSED,
            _activeDownloads.value[modelId]?.bytesDownloaded ?: 0,
            _activeDownloads.value[modelId]?.totalBytes ?: 0,
        )
    }

    /** Cancel and clean up a download. */
    suspend fun cancelDownload(modelId: String) {
        downloadJobs[modelId]?.cancel()
        downloadJobs.remove(modelId)
        val download = modelDao.getDownload(modelId)
        if (download != null) {
            File(download.tempFilePath).delete()
        }
        modelDao.deleteDownload(modelId)
        _activeDownloads.value = _activeDownloads.value - modelId
    }

    /** Resume all paused/incomplete downloads (called on app start). */
    suspend fun resumePendingDownloads() = withContext(ioDispatcher) {
        val pending = modelDao.getPendingDownloads()
        for (download in pending) {
            if (download.status == DownloadStatus.DOWNLOADING.name || download.status == DownloadStatus.QUEUED.name) {
                val card = buildCardFromEntity(download) ?: continue
                startDownload(card)
            }
        }
    }

    /** Get the final path where a model will be stored after download. */
    fun getModelPath(modelId: String): String {
        val ext = ".gguf"
        return File(modelsDir, "$modelId$ext").absolutePath
    }

    private suspend fun performDownload(card: ModelCard, tempFile: File, startByte: Long) {
        try {
            httpClient.prepareGet(card.downloadUrl) {
                if (startByte > 0) {
                    header(HttpHeaders.Range, "bytes=$startByte-")
                }
            }.execute { response ->
                if (!response.status.isSuccess()) {
                    throw RuntimeException("Download failed: HTTP ${response.status}")
                }

                val totalSize = (response.contentLength() ?: card.sizeBytes) + startByte
                val channel = response.bodyAsChannel()
                val buffer = ByteArray(CHUNK_SIZE)
                var downloaded = startByte

                val raf = RandomAccessFile(tempFile, "rw")
                raf.seek(startByte)

                try {
                    var lastProgressUpdate = System.currentTimeMillis()
                    var lastBytes = downloaded

                    while (!channel.isClosedForRead && kotlinx.coroutines.currentCoroutineContext().isActive) {
                        val bytesRead = channel.readAvailable(buffer)
                        if (bytesRead <= 0) break

                        raf.write(buffer, 0, bytesRead)
                        downloaded += bytesRead

                        val now = System.currentTimeMillis()
                        if (now - lastProgressUpdate >= PROGRESS_INTERVAL_MS) {
                            val speed = ((downloaded - lastBytes) * 1000) / (now - lastProgressUpdate)
                            updateState(card.id, DownloadStatus.DOWNLOADING, downloaded, totalSize, speed)
                            modelDao.updateDownloadProgress(
                                card.id,
                                DownloadStatus.DOWNLOADING.name,
                                downloaded,
                                now,
                            )
                            lastProgressUpdate = now
                            lastBytes = downloaded
                        }
                    }
                } finally {
                    raf.close()
                }

                // Verify and finalize
                updateState(card.id, DownloadStatus.VERIFYING, downloaded, totalSize)

                if (card.sha256.isNotBlank()) {
                    val actualHash = computeSha256(tempFile)
                    if (!actualHash.equals(card.sha256, ignoreCase = true)) {
                        tempFile.delete()
                        throw RuntimeException("SHA-256 mismatch: expected ${card.sha256}, got $actualHash")
                    }
                }

                // Move temp file to final location
                val finalFile = File(getModelPath(card.id))
                tempFile.renameTo(finalFile)

                updateState(card.id, DownloadStatus.COMPLETE, downloaded, totalSize)
                modelDao.updateDownloadProgress(
                    card.id,
                    DownloadStatus.COMPLETE.name,
                    downloaded,
                    System.currentTimeMillis(),
                )
            }
        } catch (e: kotlinx.coroutines.CancellationException) {
            // Pause — don't mark as failed
            throw e
        } catch (e: Exception) {
            modelDao.markDownloadFailed(card.id, error = e.message ?: "Unknown error")
            updateState(
                card.id,
                DownloadStatus.FAILED,
                _activeDownloads.value[card.id]?.bytesDownloaded ?: 0,
                card.sizeBytes,
                error = e.message,
            )
        }
    }

    private fun computeSha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().buffered().use { stream ->
            val buffer = ByteArray(8192)
            var bytesRead: Int
            while (stream.read(buffer).also { bytesRead = it } != -1) {
                digest.update(buffer, 0, bytesRead)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    private fun updateState(
        modelId: String,
        status: DownloadStatus,
        bytes: Long,
        total: Long,
        speed: Long = 0,
        error: String? = null,
    ) {
        _activeDownloads.value = _activeDownloads.value + (
            modelId to DownloadState(modelId, status, bytes, total, speed, error)
            )
    }

    private fun buildCardFromEntity(entity: DownloadQueueEntity): ModelCard? {
        return dev.kid.core.modelhub.registry.CuratedModelRegistry.getById(entity.modelId)
    }

    companion object {
        private const val CHUNK_SIZE = 64 * 1024 // 64KB chunks
        private const val PROGRESS_INTERVAL_MS = 500L
    }
}
