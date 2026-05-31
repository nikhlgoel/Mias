package dev.mias.core.modelhub.manager

import dev.mias.core.common.MiasResult
import dev.mias.core.common.di.IoDispatcher
import dev.mias.core.common.runCatchingMias
import dev.mias.core.modelhub.db.InstalledModelEntity
import dev.mias.core.modelhub.db.ModelDao
import dev.mias.core.modelhub.download.ModelDownloadManager
import dev.mias.core.modelhub.model.DownloadState
import dev.mias.core.modelhub.model.InstalledModel
import dev.mias.core.modelhub.model.ModelCard
import dev.mias.core.modelhub.model.ModelRole
import dev.mias.core.modelhub.model.RoleAssignment
import dev.mias.core.modelhub.registry.CuratedModelRegistry
import dev.mias.core.resilience.DeviceHealthMonitor
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Central orchestrator for the Model Hub.
 * Manages model lifecycle: browse → download → install → assign roles → uninstall.
 */
@Singleton
class ModelManager @Inject constructor(
    private val modelDao: ModelDao,
    private val downloadManager: ModelDownloadManager,
    private val healthMonitor: DeviceHealthMonitor,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) {
    init {
        downloadManager.onComplete = { card ->
            onDownloadComplete(card)
            autoAssignRoles()
        }
    }

    /** Observe all installed models reactively. */
    val installedModels: Flow<List<InstalledModel>> =
        modelDao.observeAll().map { entities -> entities.map { it.toDomain() } }

    /** Observe active downloads. */
    val activeDownloads: StateFlow<Map<String, DownloadState>> =
        downloadManager.activeDownloads

    // ── Browse ──────────────────────────────────────────────────────

    /** Get all curated models, marking which are already installed. */
    suspend fun browseCurated(): List<BrowseItem> = withContext(ioDispatcher) {
        val installed = modelDao.observeAll().map { it.map { e -> e.id } }
        CuratedModelRegistry.models.map { card ->
            BrowseItem(card = card, isInstalled = modelDao.getById(card.id) != null)
        }
    }

    // ── Install / Download ──────────────────────────────────────────

    /** Start downloading and installing a model. */
    suspend fun installModel(card: ModelCard): MiasResult<Unit> {
        if (!healthMonitor.hasStorageFor(card.sizeBytes)) {
            return MiasResult.Error("Not enough storage. Need ${card.sizeBytes / 1_000_000}MB free.")
        }
        return downloadManager.startDownload(card)
    }

    /** Called by download manager when download completes — persist to installed_models. */
    suspend fun onDownloadComplete(card: ModelCard) = withContext(ioDispatcher) {
        val entity = InstalledModelEntity(
            id = card.id,
            name = card.name,
            author = card.author,
            description = card.description,
            format = card.format.name,
            quantization = card.quantization,
            sizeBytes = card.sizeBytes,
            localPath = downloadManager.getModelPath(card),
            downloadUrl = card.downloadUrl,
            sha256 = card.sha256,
            roles = card.roles.joinToString(",") { it.name },
            contextLength = card.contextLength,
            parameterCount = card.parameterCount,
            license = card.license,
            minRamMb = card.minRamMb,
            npuCompatible = card.npuCompatible,
            source = card.source.name,
            runtime = card.runtime.name,
            defaultRolePriority = card.defaultRolePriority,
            installedAt = System.currentTimeMillis(),
            lastUsedAt = System.currentTimeMillis(),
            assignedRole = null,
        )
        modelDao.upsert(entity)
    }

    // ── Uninstall ───────────────────────────────────────────────────

    /** Delete a model from disk and database. */
    suspend fun uninstallModel(modelId: String): MiasResult<Unit> = withContext(ioDispatcher) {
        runCatchingMias {
            val entity = modelDao.getById(modelId) ?: throw IllegalStateException("Model not found")
            File(entity.localPath).delete()
            modelDao.deleteById(modelId)
        }
    }

    // ── Role Assignment ─────────────────────────────────────────────

    /** Manually assign a model to a brain role. */
    suspend fun assignRole(modelId: String, role: ModelRole): MiasResult<Unit> =
        withContext(ioDispatcher) {
            runCatchingMias {
                // Clear any existing assignment for this role
                modelDao.clearRole(role.name)
                modelDao.assignRole(modelId, role.name)
            }
        }

    /** Auto-assign best available models to all roles based on device capabilities. */
    suspend fun autoAssignRoles(): MiasResult<Map<ModelRole, String>> = withContext(ioDispatcher) {
        runCatchingMias {
            val health = healthMonitor.refresh()
            val assignments = mutableMapOf<ModelRole, String>()

            for (role in ModelRole.entries) {
                val candidates = modelDao.getCapableOfRole(role.name)
                    .filter { it.minRamMb <= health.availableRamMb }
                    .sortedWith(compareByDescending<InstalledModelEntity> { it.defaultRolePriority }.thenByDescending { it.contextLength })

                val best = candidates.firstOrNull() ?: continue
                modelDao.clearRole(role.name)
                modelDao.assignRole(best.id, role.name)
                assignments[role] = best.id
            }

            assignments
        }
    }

    /** Get current role assignments. */
    suspend fun getRoleAssignments(): List<RoleAssignment> = withContext(ioDispatcher) {
        ModelRole.entries.map { role ->
            val assigned = modelDao.getByRole(role.name)
            RoleAssignment(
                role = role,
                modelId = assigned?.id,
                isAutoSelected = true,
            )
        }
    }

    /**
     * Get the model path for a given role (for inference engine).
     *
     * First tries an explicit assignment (`assignedRole = role`); if none,
     * falls back to any installed model whose declared `roles` capability
     * list includes this role. The fallback is necessary because
     * `assignedRole` is a single-valued field — one model can only be
     * "assigned" to one role at a time even when it's capable of several,
     * so role lookup can't depend on assignment alone.
     */
    suspend fun getModelPathForRole(role: ModelRole): String? = withContext(ioDispatcher) {
        modelDao.getByRole(role.name)?.localPath
            ?: modelDao.getCapableOfRole(role.name).firstOrNull()?.localPath
    }

    suspend fun getModelForRole(role: ModelRole): InstalledModel? = withContext(ioDispatcher) {
        (modelDao.getByRole(role.name) ?: modelDao.getCapableOfRole(role.name).firstOrNull())
            ?.toDomain()
    }

    suspend fun markUsed(modelId: String) = withContext(ioDispatcher) {
        modelDao.updateLastUsed(modelId, System.currentTimeMillis())
    }

    /** Get total storage used by all models. */
    suspend fun totalStorageUsed(): Long = withContext(ioDispatcher) {
        modelDao.totalStorageUsed() ?: 0L
    }

    /** Pause a download. */
    suspend fun pauseDownload(modelId: String) = downloadManager.pauseDownload(modelId)

    /** Cancel a download. */
    suspend fun cancelDownload(modelId: String) = downloadManager.cancelDownload(modelId)

    /** Resume pending downloads on app start. */
    suspend fun resumePendingDownloads() = downloadManager.resumePendingDownloads()

    /** Resume a single paused/failed download. */
    suspend fun resumeDownload(modelId: String): MiasResult<Unit> =
        downloadManager.resumeDownload(modelId)
}

/** A browsable model card with installation status. */
data class BrowseItem(
    val card: ModelCard,
    val isInstalled: Boolean,
)

// ── Entity ↔ Domain Mapping ─────────────────────────────────────────

private fun InstalledModelEntity.toDomain(): InstalledModel {
    return InstalledModel(
        id = id,
        card = ModelCard(
            id = id,
            name = name,
            author = author,
            description = description,
            sizeBytes = sizeBytes,
            quantization = quantization,
            format = dev.mias.core.modelhub.model.ModelFormat.valueOf(format),
            roles = roles.split(",").mapNotNull {
                try { ModelRole.valueOf(it) } catch (_: Exception) { null }
            },
            contextLength = contextLength,
            parameterCount = parameterCount,
            downloadUrl = downloadUrl,
            sha256 = sha256,
            license = license,
            minRamMb = minRamMb,
            npuCompatible = npuCompatible,
            source = runCatching { dev.mias.core.modelhub.model.ModelSource.valueOf(source) }.getOrDefault(dev.mias.core.modelhub.model.ModelSource.CURATED),
            runtime = runCatching { dev.mias.core.modelhub.model.ModelRuntime.valueOf(runtime) }.getOrDefault(dev.mias.core.modelhub.model.ModelRuntime.LLAMA_CPP),
            defaultRolePriority = defaultRolePriority,
        ),
        localPath = localPath,
        installedAt = installedAt,
        lastUsedAt = lastUsedAt,
        sizeOnDisk = sizeBytes,
        isActive = assignedRole != null,
        assignedRole = assignedRole?.let {
            try { ModelRole.valueOf(it) } catch (_: Exception) { null }
        },
    )
}
