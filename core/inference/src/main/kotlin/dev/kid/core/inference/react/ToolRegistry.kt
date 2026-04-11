package dev.kid.core.inference.react

import javax.inject.Inject
import javax.inject.Singleton

/** Registry of tools the ReAct loop can invoke. */
@Singleton
class ToolRegistry @Inject constructor() {

    private val tools = mutableMapOf<String, ToolHandler>()

    fun register(name: String, handler: ToolHandler) {
        tools[name] = handler
    }

    fun get(name: String): ToolHandler? = tools[name]

    fun availableTools(): List<String> = tools.keys.toList()

    fun isRegistered(name: String): Boolean = name in tools
}

/** A single executable tool action. */
fun interface ToolHandler {
    suspend fun execute(input: Map<String, String>): String
}
