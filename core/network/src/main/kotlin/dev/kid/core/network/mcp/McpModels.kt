package dev.kid.core.network.mcp

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

/** JSON-RPC 2.0 request for MCP protocol. */
@Serializable
data class McpRequest(
    val jsonrpc: String = "2.0",
    val id: Int,
    val method: String,
    val params: Map<String, JsonElement>? = null,
)

/** JSON-RPC 2.0 response from MCP server. */
@Serializable
data class McpResponse(
    val jsonrpc: String = "2.0",
    val id: Int,
    val result: JsonElement? = null,
    val error: McpError? = null,
)

@Serializable
data class McpError(
    val code: Int,
    val message: String,
)

/** Tool definition as exposed by MCP server. */
@Serializable
data class McpTool(
    val name: String,
    val description: String,
)

/** Result of a tool invocation. */
data class McpToolResult(
    val toolName: String,
    val output: String,
    val isError: Boolean = false,
)
