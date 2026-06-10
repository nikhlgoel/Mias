package dev.mias.core.agent.capabilities

import dev.mias.core.agent.AgentCapability
import dev.mias.core.agent.ParameterType
import dev.mias.core.agent.ToolParameter
import dev.mias.core.common.MiasResult
import dev.mias.core.common.runCatchingMias
import dev.mias.core.resilience.ConnectivityMonitor
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpHeaders
import io.ktor.http.contentLength
import io.ktor.http.isSuccess
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Web fetch agent — retrieves content from URLs.
 * Strips HTML to plain text for model consumption.
 * Respects connectivity state and content size limits.
 */
@Singleton
class WebFetchCapability @Inject constructor(
    private val httpClient: HttpClient,
    private val connectivity: ConnectivityMonitor,
) : AgentCapability {

    override val name = "web_fetch"

    override val description = "Fetch and extract text content from a URL. " +
        "Returns plain text (HTML tags stripped). Use for research, fact-checking, " +
        "or gathering information from websites."

    override val parameters = listOf(
        ToolParameter("url", "The URL to fetch content from"),
        ToolParameter(
            "max_chars",
            "Maximum characters to return (default: 4000)",
            required = false,
            type = ParameterType.INT,
        ),
    )

    override suspend fun execute(input: Map<String, String>): MiasResult<String> {
        val url = input["url"]?.trim() ?: return MiasResult.Error("Missing required parameter: url")
        val maxChars = input["max_chars"]?.toIntOrNull() ?: DEFAULT_MAX_CHARS

        if (!WebSupport.isFetchableUrl(url)) {
            return MiasResult.Error("URL must be a reachable http(s) address")
        }
        if (!connectivity.isOnline) {
            return MiasResult.Error("No internet connection available")
        }

        return runCatchingMias {
            val response = httpClient.get(url) {
                header(HttpHeaders.UserAgent, USER_AGENT)
                header(HttpHeaders.Accept, "text/html,text/plain")
            }

            if (!response.status.isSuccess()) {
                throw RuntimeException("HTTP ${response.status.value}: Failed to fetch $url")
            }
            response.contentLength()?.let { declared ->
                if (declared > MAX_HTML_BYTES) {
                    throw RuntimeException("Document too large to read (${declared / 1_000_000} MB)")
                }
            }

            // Shared extractor: same boilerplate-stripping as web_research /
            // web_answer, with bounded processing.
            val body = response.bodyAsText().take(MAX_HTML_CHARS)
            val text = WebContentExtractor.extractReadableText(body)
            if (text.length > maxChars) text.take(maxChars) + "\n...[truncated]" else text
        }
    }

    companion object {
        private const val DEFAULT_MAX_CHARS = 4000
        private const val MAX_HTML_BYTES = 3_000_000L
        private const val MAX_HTML_CHARS = 600_000
        private const val USER_AGENT = "Mias-AI/1.0 (Local Agent)"
    }
}
