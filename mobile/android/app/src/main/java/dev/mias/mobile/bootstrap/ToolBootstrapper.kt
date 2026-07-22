package dev.mias.mobile.bootstrap

import dev.mias.core.agent.AgentCapability
import dev.mias.core.common.MiasResult
import dev.mias.core.inference.react.ToolRegistry
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Registers every [AgentCapability] (datetime, calculator, web fetch, …) into
 * the ReAct [ToolRegistry] at startup. Relocated from the deleted Compose app at
 * the S7 cutover.
 *
 * The capabilities are multibound in `core/agent` and the registry lives in
 * `core/inference`, and those modules don't depend on each other — the RN app
 * depends on both, so it bridges them. Without this the ReAct tool registry is
 * empty and every `action` the model picks fails.
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
