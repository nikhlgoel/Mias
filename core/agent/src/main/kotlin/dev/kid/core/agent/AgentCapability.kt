package dev.kid.core.agent

import dev.kid.core.common.KidResult
import kotlinx.serialization.Serializable

/**
 * Contract for an agent capability that Kid can invoke as a tool.
 * Each capability registers itself with the ReAct ToolRegistry.
 */
interface AgentCapability {
    /** Unique name (used in ReAct JSON: "action": "web_fetch"). */
    val name: String

    /** Human-readable description for the model's tool prompt. */
    val description: String

    /** Parameter schema the model must provide. */
    val parameters: List<ToolParameter>

    /** Execute this capability with the given input. */
    suspend fun execute(input: Map<String, String>): KidResult<String>
}

@Serializable
data class ToolParameter(
    val name: String,
    val description: String,
    val required: Boolean = true,
    val type: ParameterType = ParameterType.STRING,
)

@Serializable
enum class ParameterType {
    STRING,
    INT,
    BOOLEAN,
    JSON,
}

/**
 * Result of an agent action — structured for the ReAct observation step.
 */
@Serializable
data class ActionResult(
    val success: Boolean,
    val output: String,
    val metadata: Map<String, String> = emptyMap(),
)
