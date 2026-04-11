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
import kotlinx.serialization.json.put
import java.util.concurrent.atomic.AtomicInteger
import javax.inject.Inject
import javax.inject.Singleton

/**
 * MCP (Model Context Protocol) client for communicating with the desktop
 * Qwen3-Coder-Next server over Tailscale WireGuard tunnel.
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

    fun configure(desktopIp: String, port: Int = DEFAULT_PORT) {
        serverUrl = "http://$desktopIp:$port/rpc"
    }

    val isConfigured: Boolean get() = serverUrl.isNotBlank()

    /** Initialize MCP session with the desktop server. */
    suspend fun initialize(): KidResult<String> = withContext(ioDispatcher) {
        runCatchingKid {
            val request = McpRequest(
                id = requestId.incrementAndGet(),
                method = "initialize",
                params = mapOf(
                    "protocolVersion" to JsonPrimitive("2025-03-26"),
                    "clientInfo" to buildJsonObject {
                        put("name", "Kid Android")
                        put("version", "0.1.0")
                    },
                ),
            )
            val responseStr = sendRequest(request)
            responseStr
        }
    }

    /** List available tools on the desktop server. */
    suspend fun listTools(): KidResult<List<McpTool>> = withContext(ioDispatcher) {
        runCatchingKid {
            val request = McpRequest(
                id = requestId.incrementAndGet(),
                method = "tools/list",
            )
            val responseStr = sendRequest(request)
            // Parse tools from response
            val response = json.decodeFromString<McpResponse>(responseStr)
            if (response.error != null) {
                throw RuntimeException("MCP error: ${response.error.message}")
            }
            // For now, return known tools
            listOf(
                McpTool("generate", "Generate text with Qwen3-Coder-Next"),
                McpTool("execute_code", "Run Python code on desktop"),
                McpTool("git_operation", "Execute git commands"),
                McpTool("file_read", "Read a file from desktop"),
            )
        }
    }

    /** Call a tool on the desktop server. */
    suspend fun callTool(name: String, arguments: Map<String, String>): KidResult<McpToolResult> =
        withContext(ioDispatcher) {
            runCatchingKid {
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
    }
}
