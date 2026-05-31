package dev.mias.core.modelhub.db

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface ModelDao {

    // ── Installed Models ───────────────────────────────────────────

    @Query("SELECT * FROM installed_models ORDER BY lastUsedAt DESC")
    fun observeAll(): Flow<List<InstalledModelEntity>>

    @Query("SELECT * FROM installed_models WHERE id = :id")
    suspend fun getById(id: String): InstalledModelEntity?

    @Query("SELECT * FROM installed_models WHERE roles LIKE '%' || :role || '%' ORDER BY defaultRolePriority DESC, contextLength DESC")
    suspend fun getCapableOfRole(role: String): List<InstalledModelEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(model: InstalledModelEntity)

    @Query("DELETE FROM installed_models WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("UPDATE installed_models SET lastUsedAt = :timestamp WHERE id = :id")
    suspend fun updateLastUsed(id: String, timestamp: Long)

    @Query("SELECT SUM(sizeBytes) FROM installed_models")
    suspend fun totalStorageUsed(): Long?

    // ── Role assignments (role_assignments table) ──────────────────

    @Query("SELECT * FROM role_assignments")
    fun observeRoleAssignments(): Flow<List<RoleAssignmentEntity>>

    @Query("SELECT * FROM role_assignments WHERE role = :role LIMIT 1")
    suspend fun getRoleAssignment(role: String): RoleAssignmentEntity?

    @Query(
        "SELECT m.* FROM installed_models m " +
            "INNER JOIN role_assignments r ON r.modelId = m.id " +
            "WHERE r.role = :role LIMIT 1",
    )
    suspend fun getByAssignedRole(role: String): InstalledModelEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertRoleAssignment(assignment: RoleAssignmentEntity)

    @Query("DELETE FROM role_assignments WHERE role = :role")
    suspend fun clearRoleAssignment(role: String)

    @Query("DELETE FROM role_assignments WHERE modelId = :modelId")
    suspend fun clearAllAssignmentsForModel(modelId: String)

    @Query("SELECT * FROM role_assignments WHERE isUserPinned = 1")
    suspend fun getUserPinnedAssignments(): List<RoleAssignmentEntity>

    // ── Download Queue ────────────────────────────────────────────

    @Query("SELECT * FROM download_queue ORDER BY createdAt ASC")
    fun observeDownloads(): Flow<List<DownloadQueueEntity>>

    @Query("SELECT * FROM download_queue WHERE modelId = :modelId")
    suspend fun getDownload(modelId: String): DownloadQueueEntity?

    @Query("SELECT * FROM download_queue WHERE status IN ('QUEUED', 'DOWNLOADING', 'PAUSED') ORDER BY createdAt ASC")
    suspend fun getPendingDownloads(): List<DownloadQueueEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertDownload(download: DownloadQueueEntity)

    @Query("DELETE FROM download_queue WHERE modelId = :modelId")
    suspend fun deleteDownload(modelId: String)

    @Query("UPDATE download_queue SET status = :status, bytesDownloaded = :bytes, updatedAt = :time WHERE modelId = :modelId")
    suspend fun updateDownloadProgress(modelId: String, status: String, bytes: Long, time: Long)

    @Query("UPDATE download_queue SET status = 'PAUSED', updatedAt = :time WHERE modelId = :modelId")
    suspend fun pauseDownload(modelId: String, time: Long = System.currentTimeMillis())

    @Query("UPDATE download_queue SET status = :status, lastError = :error, retryCount = retryCount + 1, updatedAt = :time WHERE modelId = :modelId")
    suspend fun markDownloadFailed(modelId: String, status: String = "FAILED", error: String, time: Long = System.currentTimeMillis())
}
