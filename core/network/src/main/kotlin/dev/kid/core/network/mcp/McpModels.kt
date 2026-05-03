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

/** JSON-RPC 2.0 notification (no id field). */
@Serializable
data class McpNotification(
    val jsonrpc: String = "2.0",
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

// ── MCP 2024-11 Initialization Models ─────────────────────────────────────

/** Server info returned during MCP initialization. */
@Serializable
data class McpServerInfo(
    val name: String,
    val version: String,
)

/** Result from the MCP initialize handshake. */
@Serializable
data class McpInitializeResult(
    val protocolVersion: String,
    val serverInfo: McpServerInfo,
    val capabilities: McpServerCapabilities,
)

/** Capabilities advertised by the MCP server. */
@Serializable
data class McpServerCapabilities(
    val tools: McpToolCapability? = null,
)

/** Tool-specific capabilities. */
@Serializable
data class McpToolCapability(
    val listChanged: Boolean = false,
)

/** Typed representation of MCP capability categories. */
sealed interface McpCapability {
    /** Server supports tool invocation. */
    data class Tools(val listChanged: Boolean) : McpCapability

    /** Server supports resource access. */
    data object Resources : McpCapability

    /** Server supports prompt templates. */
    data object Prompts : McpCapability
}
