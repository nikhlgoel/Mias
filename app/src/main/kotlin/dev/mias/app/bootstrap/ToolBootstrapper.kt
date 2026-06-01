package dev.mias.app.bootstrap

import dev.mias.core.agent.AgentCapability
import dev.mias.core.common.MiasResult
import dev.mias.core.inference.react.ToolRegistry
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Registers every [AgentCapability] (datetime, calculator, web fetch, …) into
 * the ReAct [ToolRegistry] at startup.
 *
 * Why this lives in the app layer: the capabilities are multibound in
 * `core/agent` and the registry lives in `core/inference`, and those two
 * modules don't depend on each other. The app module depends on both, so it
 * is the natural place to bridge them. Without this, the ReAct loop's tool
 * registry is empty and every `action` the model picks fails — the model
 * just drops its JSON and stops.
 */
@Singleton
class ToolBootstrapper @Inject constructor(
    private val capabilities: Set<@JvmSuppressWildcards AgentCapability>,
    private val toolRegistry: ToolRegistry,
) {
    fun register() {
        capabilities.forEach { capability ->
            toolRegistry.register(capability.name, capability.description) { input ->
                when (val result = capability.execute(input)) {
                    is MiasResult.Success -> result.data
                    is MiasResult.Error -> "Error: ${result.message}"
                }
            }
        }
    }
}
