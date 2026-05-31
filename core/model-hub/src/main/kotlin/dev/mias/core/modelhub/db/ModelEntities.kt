package dev.mias.core.modelhub.db

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
    val source: String,
    val runtime: String,
    val defaultRolePriority: Int,
    val installedAt: Long,
    val lastUsedAt: Long,
    val assignedRole: String?,
)

/**
 * One row per [dev.mias.core.modelhub.model.ModelRole] (PK = role name).
 * Replaces the single-valued `installed_models.assignedRole` column so a
 * model capable of multiple roles can actually be assigned to all of them.
 *
 * `isUserPinned` distinguishes a deliberate user choice (Settings → "Use
 * this model for CHAT") from the auto-selected default. Auto-assignment
 * may overwrite an unpinned row; it must not overwrite a pinned one.
 */
@Entity(tableName = "role_assignments")
data class RoleAssignmentEntity(
    @PrimaryKey val role: String,
    val modelId: String,
    val isUserPinned: Boolean,
    val assignedAt: Long,
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
