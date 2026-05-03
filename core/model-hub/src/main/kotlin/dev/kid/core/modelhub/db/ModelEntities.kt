package dev.kid.core.modelhub.db

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "installed_models")
data class InstalledModelEntity(
    @PrimaryKey val id: String,
    val name: String,
    val author: String,
    val description: String,
    val format: String,
    val quantization: String,
    val sizeBytes: Long,
    val localPath: String,
    val downloadUrl: String,
    val sha256: String,
    val roles: String,
    val contextLength: Int,
    val parameterCount: String,
    val license: String,
    val minRamMb: Int,
    val npuCompatible: Boolean,
    val installedAt: Long,
    val lastUsedAt: Long,
    val assignedRole: String?,
)

@Entity(tableName = "download_queue")
data class DownloadQueueEntity(
    @PrimaryKey val modelId: String,
    val downloadUrl: String,
    val totalBytes: Long,
    val bytesDownloaded: Long,
    val status: String,
    val tempFilePath: String,
    val sha256: String,
    val createdAt: Long,
    val updatedAt: Long,
    val retryCount: Int = 0,
    val lastError: String? = null,
)
