package dev.mias.core.modelhub.manager

import dev.mias.core.common.MiasResult
import dev.mias.core.common.di.IoDispatcher
import dev.mias.core.common.runCatchingMias
import dev.mias.core.modelhub.db.InstalledModelEntity
import dev.mias.core.modelhub.db.ModelDao
import dev.mias.core.modelhub.db.RoleAssignmentEntity
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

    /**
     * Observe role assignments reactively as a `role → modelId` map.
     *
     * Backed by the normalized `role_assignments` table. A model capable of
     * multiple roles will appear multiple times here (different keys, same
     * value), which is exactly what the old `installed_models.assignedRole`
     * single-string column could not express.
     */
    val roleAssignments: Flow<Map<ModelRole, String>> =
        modelDao.observeRoleAssignments().map { rows ->
            rows.mapNotNull { row ->
                val role = runCatching { ModelRole.valueOf(row.role) }.getOrNull()
                role?.let { it to row.modelId }
            }.toMap()
        }

    /** Observe active downloads. */
    val activeDownloads: StateFlow<Map<String, DownloadState>> =
        downloadManager.activeDownloads

    // ── Browse ──────────────────────────────────────────────────────

    /** Get all curated models, marking which are already installed. */
    suspend fun browseCurated(): List<BrowseItem> = withContext(ioDispatcher) {
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
            assignedRole = null, // Legacy column — no longer the source of truth.
        )
        modelDao.upsert(entity)
    }

    // ── Uninstall ───────────────────────────────────────────────────

    /** Delete a model from disk and database, plus any role assignments. */
    suspend fun uninstallModel(modelId: String): MiasResult<Unit> = withContext(ioDispatcher) {
        runCatchingMias {
            val entity = modelDao.getById(modelId) ?: throw IllegalStateException("Model not found")
            File(entity.localPath).delete()
            modelDao.clearAllAssignmentsForModel(modelId)
            modelDao.deleteById(modelId)
        }
    }

    // ── Role Assignment ─────────────────────────────────────────────

    /**
     * Manually assign a model to a brain role. Marks the assignment as
     * user-pinned so [autoAssignRoles] won't overwrite it.
     */
    suspend fun assignRole(modelId: String, role: ModelRole): MiasResult<Unit> =
        withContext(ioDispatcher) {
            runCatchingMias {
                modelDao.upsertRoleAssignment(
                    RoleAssignmentEntity(
                        role = role.name,
                        modelId = modelId,
                        isUserPinned = true,
                        assignedAt = System.currentTimeMillis(),
                    ),
                )
            }
        }

    /** Clear a role's assignment (e.g. user reset). */
    suspend fun clearRoleAssignment(role: ModelRole): MiasResult<Unit> =
        withContext(ioDispatcher) {
            runCatchingMias { modelDao.clearRoleAssignment(role.name) }
        }

    /**
     * Auto-assign best available models to all roles based on device
     * capabilities. **Respects user pins** — a role explicitly pinned via
     * [assignRole] is never overwritten by auto-assignment.
     */
    suspend fun autoAssignRoles(): MiasResult<Map<ModelRole, String>> = withContext(ioDispatcher) {
        runCatchingMias {
            val health = healthMonitor.refresh()
            val pinnedRoles = modelDao.getUserPinnedAssignments().map { it.role }.toSet()
            val assignments = mutableMapOf<ModelRole, String>()
            val now = System.currentTimeMillis()

            for (role in ModelRole.entries) {
                if (role.name in pinnedRoles) {
                    // User has pinned this role — record current pin and move on.
                    modelDao.getRoleAssignment(role.name)?.let { existing ->
                        assignments[role] = existing.modelId
                    }
                    continue
                }
                val best = modelDao.getCapableOfRole(role.name)
                    .filter { it.minRamMb <= health.availableRamMb }
                    .sortedWith(
                        compareByDescending<InstalledModelEntity> { it.defaultRolePriority }
                            .thenByDescending { it.contextLength },
                    )
                    .firstOrNull()
                if (best == null) {
                    // No capable model for this role anymore — drop any stale auto-assignment.
                    modelDao.clearRoleAssignment(role.name)
                    continue
                }
                modelDao.upsertRoleAssignment(
                    RoleAssignmentEntity(
                        role = role.name,
                        modelId = best.id,
                        isUserPinned = false,
                        assignedAt = now,
                    ),
                )
                assignments[role] = best.id
            }
            assignments
        }
    }

    /** Get current role assignments (one entry per role, even if unassigned). */
    suspend fun getRoleAssignments(): List<RoleAssignment> = withContext(ioDispatcher) {
        ModelRole.entries.map { role ->
            val assigned = modelDao.getRoleAssignment(role.name)
            RoleAssignment(
                role = role,
                modelId = assigned?.modelId,
                isAutoSelected = assigned?.isUserPinned != true,
            )
        }
    }

    /**
     * Get the model path for a given role.
     *
     * Resolution order:
     *   1. Explicit assignment in `role_assignments` (user-pinned or auto).
     *   2. Any installed model whose declared `roles` capability includes
     *      this role — picked by `defaultRolePriority` then `contextLength`.
     *
     * The fallback exists so freshly-installed-but-not-yet-assigned models
     * still serve the moment a request comes in, without the user having
     * to wait for `autoAssignRoles` to fire.
     */
    suspend fun getModelPathForRole(role: ModelRole): String? = withContext(ioDispatcher) {
        modelDao.getByAssignedRole(role.name)?.localPath
            ?: modelDao.getCapableOfRole(role.name).firstOrNull()?.localPath
    }

    suspend fun getModelForRole(role: ModelRole): InstalledModel? = withContext(ioDispatcher) {
        (modelDao.getByAssignedRole(role.name) ?: modelDao.getCapableOfRole(role.name).firstOrNull())
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
        // `isActive` is no longer derived from the legacy column — it's
        // computed externally by joining InstalledModel with roleAssignments.
        isActive = false,
    )
}
