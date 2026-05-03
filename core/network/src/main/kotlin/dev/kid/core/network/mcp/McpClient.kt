package dev.kid.core.network.mcp

import dev.kid.core.common.KidResult
import dev.kid.core.common.di.IoDispatcher
import dev.kid.core.common.runCatchingKid
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import java.util.concurrent.atomic.AtomicInteger
import javax.inject.Inject
import javax.inject.Singleton

/**
 * MCP (Model Context Protocol) client for communicating with the desktop
 * Qwen3-Coder-Next server over Tailscale WireGuard tunnel.
 *
 * Implements the full MCP 2024-11 initialization handshake:
 * 1. Client → Server: initialize { protocolVersion, capabilities, clientInfo }
 * 2. Server → Client: { result: { protocolVersion, capabilities, serverInfo } }
 * 3. Client → Server: notifications/initialized
 *
 * All communication is local P2P — no cloud endpoints.
 */
@Singleton
class McpClient @Inject constructor(
    private val httpClient: HttpClient,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) {
    private val requestId = AtomicInteger(0)
    private val json = Json { ignoreUnknownKeys = true }

    @Volatile
    var serverUrl: String = ""
        private set

    /** Server info received during initialization. */
    @Volatile
    var serverInfo: McpServerInfo? = null
        private set

    /** Server capabilities received during initialization. */
    @Volatile
    var serverCapabilities: McpServerCapabilities? = null
        private set

    /** Whether the MCP handshake has been completed. */
    @Volatile
    var isInitialized: Boolean = false
        private set

    fun configure(desktopIp: String, port: Int = DEFAULT_PORT) {
        serverUrl = "http://$desktopIp:$port/rpc"
        // Reset initialization state when reconfigured
        isInitialized = false
        serverInfo = null
        serverCapabilities = null
    }

    val isConfigured: Boolean get() = serverUrl.isNotBlank()

    /**
     * Full MCP 2024-11 initialization handshake.
     *
     * Sends `initialize` request, parses server capabilities, then
     * sends `notifications/initialized` notification to complete the handshake.
     */
    suspend fun initialize(): KidResult<McpInitializeResult> = withContext(ioDispatcher) {
        runCatchingKid {
            // Step 1: Send initialize request
            val request = McpRequest(
                id = requestId.incrementAndGet(),
                method = "initialize",
                params = mapOf(
                    "protocolVersion" to JsonPrimitive(PROTOCOL_VERSION),
                    "capabilities" to buildJsonObject {
                        // Client capabilities — we support tool usage
                    },
                    "clientInfo" to buildJsonObject {
                        put("name", "Mias Android")
                        put("version", CLIENT_VERSION)
                    },
                ),
            )
            val responseStr = sendRequest(request)

            // Step 2: Parse server response
            val response = json.decodeFromString<McpResponse>(responseStr)
            if (response.error != null) {
                throw RuntimeException("MCP initialization failed: ${response.error.message}")
            }

            val resultJson = response.result
                ?: throw RuntimeException("MCP initialization returned null result")

            val initResult = json.decodeFromJsonElement(
                McpInitializeResult.serializer(),
                resultJson,
            )

            serverInfo = initResult.serverInfo
            serverCapabilities = initResult.capabilities

            // Step 3: Send notifications/initialized to complete handshake
            val notification = McpNotification(
                method = "notifications/initialized",
            )
            val notifBody = json.encodeToString(McpNotification.serializer(), notification)
            httpClient.post(serverUrl) {
                contentType(ContentType.Application.Json)
                setBody(notifBody)
            }

            isInitialized = true
            initResult
        }
    }

    /**
     * List available tools on the desktop server.
     * Parses the actual tools/list response instead of returning hardcoded values.
     */
    suspend fun listTools(): KidResult<List<McpTool>> = withContext(ioDispatcher) {
        runCatchingKid {
            ensureInitialized()

            val request = McpRequest(
                id = requestId.incrementAndGet(),
                method = "tools/list",
            )
            val responseStr = sendRequest(request)
            val response = json.decodeFromString<McpResponse>(responseStr)
            if (response.error != null) {
                throw RuntimeException("MCP error: ${response.error.message}")
            }

            // Parse tools from the actual server response
            val resultObj = response.result?.jsonObject
            val toolsArray = resultObj?.get("tools")?.jsonArray ?: emptyList()

            toolsArray.map { toolElement ->
                val toolObj = toolElement.jsonObject
                McpTool(
                    name = toolObj["name"]?.jsonPrimitive?.content ?: "unknown",
                    description = toolObj["description"]?.jsonPrimitive?.content ?: "",
                )
            }
        }
    }

    /** Call a tool on the desktop server. */
    suspend fun callTool(name: String, arguments: Map<String, String>): KidResult<McpToolResult> =
        withContext(ioDispatcher) {
            runCatchingKid {
                ensureInitialized()

                val jsonArgs: Map<String, JsonElement> = arguments.mapValues {
                    JsonPrimitive(it.value)
                }
                val request = McpRequest(
                    id = requestId.incrementAndGet(),
                    method = "tools/call",
                    params = mapOf(
                        "name" to JsonPrimitive(name),
                        "arguments" to buildJsonObject {
                            jsonArgs.forEach { (k, v) -> put(k, v) }
                        },
                    ),
                )
                val responseStr = sendRequest(request)
                val response = json.decodeFromString<McpResponse>(responseStr)
                if (response.error != null) {
                    McpToolResult(name, response.error.message, isError = true)
                } else {
                    McpToolResult(name, response.result?.toString() ?: "")
                }
            }
        }

    /** Generate text using the desktop Qwen3-Coder-Next model. */
    suspend fun generate(prompt: String, maxTokens: Int = 2048): KidResult<String> =
        withContext(ioDispatcher) {
            val result = callTool(
                "generate",
                mapOf("prompt" to prompt, "max_tokens" to maxTokens.toString()),
            )
            when (result) {
                is KidResult.Success -> {
                    if (result.data.isError) {
                        KidResult.Error(result.data.output)
                    } else {
                        KidResult.Success(result.data.output)
                    }
                }
                is KidResult.Error -> result
            }
        }

    /**
     * Ensure the MCP handshake has been completed before making tool calls.
     * Auto-initializes if not yet done.
     */
    private suspend fun ensureInitialized() {
        if (!isInitialized) {
            val result = initialize()
            if (result is KidResult.Error) {
                throw RuntimeException("MCP auto-initialization failed: ${result.message}")
            }
        }
    }

    private suspend fun sendRequest(request: McpRequest): String {
        check(isConfigured) { "MCP client not configured. Call configure() first." }
        val body = json.encodeToString(McpRequest.serializer(), request)
        val response = httpClient.post(serverUrl) {
            contentType(ContentType.Application.Json)
            setBody(body)
        }
        return response.body<String>()
    }

    companion object {
        const val DEFAULT_PORT = 8401
        private const val PROTOCOL_VERSION = "2025-03-26"
        private const val CLIENT_VERSION = "0.1.0"
    }
}
