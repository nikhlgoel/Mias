package dev.mias.core.agent.orchestrator

import dev.mias.core.agent.AgentCapability
import dev.mias.core.agent.model.AgentStatus
import dev.mias.core.agent.model.AgentMode
import dev.mias.core.agent.model.AgentTask
import dev.mias.core.agent.model.AgentTaskResult
import dev.mias.core.agent.model.ToolDescription
import dev.mias.core.common.MiasResult
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * AgentOrchestrator — the central dispatcher for all agent capabilities.
 *
 * Maps tool names (used in ReAct JSON `"action": "..."`) to their
 * corresponding [AgentCapability] implementations. This is the bridge
 * between the inference layer (ReAct engine) and the device layer
 * (files, web, clipboard, etc.).
 *
 * Used by the ReAct engine to execute tool calls.
 */
@Singleton
class AgentOrchestrator @Inject constructor(
    capabilities: Set<@JvmSuppressWildcards AgentCapability>,
) {
    private val registry: Map<String, AgentCapability> =
        capabilities.associateBy { it.name }

    private val _status = MutableStateFlow(AgentStatus())
    val status: StateFlow<AgentStatus> = _status.asStateFlow()

    private val _recentResults = MutableStateFlow<List<AgentTaskResult>>(emptyList())
    val recentResults: StateFlow<List<AgentTaskResult>> = _recentResults.asStateFlow()

    /** All registered tool names — passed to models so they know what's available. */
    val availableTools: List<String> get() = registry.keys.sorted()

    val currentMode: AgentMode get() = _status.value.mode

    /** Full tool descriptions for LLM system prompt injection. */
    fun getToolDescriptions(): List<ToolDescription> = registry.values.map { cap ->
        val paramsJson = buildJsonObject {
            putJsonObject("type") { put("type", "object") }
            putJsonObject("properties") {
                cap.parameters.forEach { param ->
                    putJsonObject(param.name) {
                        put("type", param.type.name.lowercase())
                        put("description", param.description)
                    }
                }
            }
            put(
                "required",
                Json.parseToJsonElement(
                    "[${cap.parameters.filter { it.required }.joinToString(",") { "\"${it.name}\"" }}]",
                ),
            )
        }.toString()

        ToolDescription(
            name = cap.name,
            description = cap.description,
            parametersJson = paramsJson,
        )
    }

    /**
     * Execute a tool call from the ReAct engine.
     * Returns a JSON-formatted observation string for the model to consume.
     */
    suspend fun execute(toolName: String, input: Map<String, String>): MiasResult<String> {
        val capability = registry[toolName]
            ?: return MiasResult.Error("Unknown tool: $toolName. Available: ${availableTools.joinToString()}")

        val taskId = UUID.randomUUID().toString()
        val startTime = System.currentTimeMillis()

        _status.value = AgentStatus(
            isRunning = true,
            currentTool = toolName,
            currentTask = input.values.firstOrNull()?.take(50),
            mode = chooseMode(input.values.joinToString(" "), toolName),
            tasksCompleted = _status.value.tasksCompleted,
        )

        val result: MiasResult<String> = capability.execute(input)

        val duration = System.currentTimeMillis() - startTime
        val taskResult = AgentTaskResult(
            taskId = taskId,
            tool = toolName,
            success = result is MiasResult.Success,
            output = (result as? MiasResult.Success)?.data ?: "",
            errorMessage = (result as? MiasResult.Error)?.message,
            durationMs = duration,
        )

        _recentResults.value = (_recentResults.value + taskResult).takeLast(MAX_HISTORY)
        _status.value = AgentStatus(
            isRunning = false,
            mode = _status.value.mode,
            tasksCompleted = _status.value.tasksCompleted + if (taskResult.success) 1 else 0,
            tasksFailed = _status.value.tasksFailed + if (!taskResult.success) 1 else 0,
        )

        return result
    }

    /** Check if a specific tool is available. */
    fun hasCapability(toolName: String): Boolean = toolName in registry

    fun setMode(mode: AgentMode) {
        _status.value = _status.value.copy(mode = mode)
    }

    fun chooseMode(taskText: String, preferredTool: String? = null): AgentMode {
        if (_status.value.mode != AgentMode.AUTO) return _status.value.mode
        val text = "$preferredTool $taskText".lowercase()
        return when {
            listOf("file", "read", "write", "delete", "folder", "document").any { it in text } -> AgentMode.FILES
            listOf("web", "url", "research", "search", "http").any { it in text } -> AgentMode.RESEARCH
            listOf("open app", "launch", "clipboard", "date", "time", "calculate", "calculator").any { it in text } -> AgentMode.DEVICE
            listOf("story", "poem", "creative", "write").any { it in text } -> AgentMode.CREATIVE
            else -> AgentMode.CHAT
        }
    }

    companion object {
        private const val MAX_HISTORY = 50
    }
}
