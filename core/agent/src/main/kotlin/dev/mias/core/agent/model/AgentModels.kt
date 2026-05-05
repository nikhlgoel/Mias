package dev.mias.core.agent.model

import kotlinx.serialization.Serializable

/** A unit of work the agent can execute. */
@Serializable
data class AgentTask(
    val id: String,
    val tool: String,
    val parameters: Map<String, String>,
    val priority: AgentPriority = AgentPriority.NORMAL,
    val requiredPermissions: List<String> = emptyList(),
    val createdAt: Long = System.currentTimeMillis(),
)

enum class AgentPriority { CRITICAL, HIGH, NORMAL, LOW, BACKGROUND }

enum class AgentMode {
    AUTO,
    CHAT,
    RESEARCH,
    FILES,
    DEVICE,
    CREATIVE,
    SAFE,
}

/** Result of a completed agent task with audit trail. */
@Serializable
data class AgentTaskResult(
    val taskId: String,
    val tool: String,
    val success: Boolean,
    val output: String,
    val errorMessage: String? = null,
    val durationMs: Long = 0,
    val completedAt: Long = System.currentTimeMillis(),
)

/** Live state of the agent — what Mias is currently doing. */
data class AgentStatus(
    val isRunning: Boolean = false,
    val currentTool: String? = null,
    val currentTask: String? = null,
    val mode: AgentMode = AgentMode.AUTO,
    val tasksCompleted: Int = 0,
    val tasksFailed: Int = 0,
)

/** Description of a tool for LLM prompt injection. */
data class ToolDescription(
    val name: String,
    val description: String,
    val parametersJson: String,
)
