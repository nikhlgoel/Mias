package dev.mias.core.modelhub.bootstrap

import dev.mias.core.common.MiasResult
import dev.mias.core.common.di.IoDispatcher
import dev.mias.core.common.runCatchingMias
import dev.mias.core.modelhub.manager.ModelManager
import dev.mias.core.modelhub.model.ModelRole
import dev.mias.core.modelhub.registry.CuratedModelRegistry
import dev.mias.core.resilience.DeviceHealthMonitor
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ModelBootstrapper @Inject constructor(
    private val modelManager: ModelManager,
    private val healthMonitor: DeviceHealthMonitor,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) {
    suspend fun prepareFirstRunModels(autoDownload: Boolean = false): MiasResult<BootstrapReport> =
        withContext(ioDispatcher) {
            runCatchingMias {
                modelManager.resumePendingDownloads()
                val assignments = modelManager.autoAssignRoles().let { result ->
                    (result as? MiasResult.Success)?.data.orEmpty()
                }

                if (!autoDownload || assignments.isNotEmpty()) {
                    return@runCatchingMias BootstrapReport(assignments, emptyList())
                }

                val health = healthMonitor.refresh()
                val queued = mutableListOf<String>()
                val essentialRoles = listOf(ModelRole.SURVIVAL, ModelRole.CHAT, ModelRole.EMBEDDING)

                for (role in essentialRoles) {
                    val card = CuratedModelRegistry.getRecommendedForRole(role, health.availableRamMb)
                        ?: continue
                    if (modelManager.getModelPathForRole(role) != null) continue
                    val result = modelManager.installModel(card)
                    if (result is MiasResult.Success) queued += card.id
                }

                BootstrapReport(assignments, queued)
            }
        }
}

data class BootstrapReport(
    val assignedModels: Map<ModelRole, String>,
    val queuedDownloads: List<String>,
)
