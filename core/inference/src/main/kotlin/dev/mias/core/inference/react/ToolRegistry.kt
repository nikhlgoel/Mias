package dev.mias.core.inference.react

import javax.inject.Inject
import javax.inject.Singleton

/** Registry of tools the ReAct loop can invoke. */
@Singleton
class ToolRegistry @Inject constructor() {

    private data class Entry(val description: String, val handler: ToolHandler)

    private val tools = mutableMapOf<String, Entry>()

    fun register(name: String, description: String, handler: ToolHandler) {
        tools[name] = Entry(description, handler)
    }

    /** Back-compat overload (no description). */
    fun register(name: String, handler: ToolHandler) = register(name, "", handler)

    fun get(name: String): ToolHandler? = tools[name]?.handler

    fun availableTools(): List<String> = tools.keys.toList()

    fun isRegistered(name: String): Boolean = name in tools

    /** Tool catalogue for the model's prompt, e.g. "- datetime: Get current…". */
    fun describeForPrompt(): String =
        tools.entries.joinToString("\n") { (name, e) ->
            if (e.description.isBlank()) "- $name" else "- $name: ${e.description}"
        }

    /**
     * Resolve a model-supplied action to a registered tool name. Handles the
     * common case where a weak model emits a phrase ("Respond with the current
     * time") instead of the exact id ("datetime"): exact match first, then a
     * case-insensitive substring match against known tool names.
     */
    fun resolve(action: String): String? {
        if (action in tools) return action
        val lower = action.lowercase()
        return tools.keys.firstOrNull { it.lowercase() in lower }
    }
}

/** A single executable tool action. */
fun interface ToolHandler {
    suspend fun execute(input: Map<String, String>): String
}
