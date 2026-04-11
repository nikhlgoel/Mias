package dev.kid.core.modelhub.db

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

    @Query("SELECT * FROM installed_models WHERE assignedRole = :role LIMIT 1")
    suspend fun getByRole(role: String): InstalledModelEntity?

    @Query("SELECT * FROM installed_models WHERE roles LIKE '%' || :role || '%'")
    suspend fun getCapableOfRole(role: String): List<InstalledModelEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(model: InstalledModelEntity)

    @Query("DELETE FROM installed_models WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("UPDATE installed_models SET lastUsedAt = :timestamp WHERE id = :id")
    suspend fun updateLastUsed(id: String, timestamp: Long)

    @Query("UPDATE installed_models SET assignedRole = :role WHERE id = :id")
    suspend fun assignRole(id: String, role: String?)

    @Query("UPDATE installed_models SET assignedRole = NULL WHERE assignedRole = :role")
    suspend fun clearRole(role: String)

    @Query("SELECT SUM(sizeBytes) FROM installed_models")
    suspend fun totalStorageUsed(): Long?

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
