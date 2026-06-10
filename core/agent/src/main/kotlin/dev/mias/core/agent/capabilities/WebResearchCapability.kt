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
 * WebResearchCapability — deep content extraction from URLs.
 *
 * More advanced than WebFetchCapability:
 * - Extracts article text (strips ads, navigation, boilerplate)
 * - Can search for multiple key points at once
 * - Provides a structured summary format for the model
 * - Handles pagination hints
 */
@Singleton
class WebResearchCapability @Inject constructor(
    private val httpClient: HttpClient,
    private val connectivity: ConnectivityMonitor,
) : AgentCapability {

    override val name = "web_research"
    override val description =
        "Fetch and extract clean content from a URL. Better than web_fetch for article reading."
    override val parameters = listOf(
        ToolParameter("url", "The URL to research", required = true, type = ParameterType.STRING),
        ToolParameter(
            "focus",
            "Optional: specific aspect to focus on (e.g. 'pricing', 'installation steps')",
            required = false,
        ),
        ToolParameter(
            "max_chars",
            "Maximum characters to return (default: 6000)",
            required = false,
            type = ParameterType.INT,
        ),
    )

    override suspend fun execute(input: Map<String, String>): MiasResult<String> =
        runCatchingMias {
            if (!connectivity.isOnline) {
                throw IllegalStateException("No internet connection")
            }

            val url = input["url"]?.trim()
                ?: throw IllegalArgumentException("Missing: url")
            val focus = input["focus"]?.trim()
            val maxChars = input["max_chars"]?.toIntOrNull() ?: DEFAULT_MAX_CHARS

            if (!WebSupport.isFetchableUrl(url)) {
                throw IllegalArgumentException("URL must be a reachable http(s) address")
            }

            val response = httpClient.get(url) {
                header(HttpHeaders.UserAgent, USER_AGENT)
                header(HttpHeaders.Accept, "text/html,application/xhtml+xml")
                header(HttpHeaders.AcceptLanguage, "en-US,en;q=0.9")
            }

            if (!response.status.isSuccess()) {
                throw IllegalStateException("HTTP ${response.status.value}: $url")
            }
            response.contentLength()?.let { declared ->
                if (declared > MAX_HTML_BYTES) {
                    throw IllegalStateException("Document too large to read (${declared / 1_000_000} MB)")
                }
            }

            // Shared extractor (same engine as web_answer): bounded processing,
            // boilerplate stripped, focus paragraphs floated to the front.
            val rawHtml = response.bodyAsText().take(MAX_HTML_CHARS)
            val text = WebContentExtractor.extractReadableText(rawHtml, focus)
            val result = text.take(maxChars)

            buildString {
                appendLine("--- Web Research: $url ---")
                if (focus != null) appendLine("Focus: $focus")
                appendLine()
                append(result)
                if (text.length > maxChars) appendLine("\n[...truncated — ${text.length - maxChars} chars omitted]")
            }
        }

    companion object {
        private const val DEFAULT_MAX_CHARS = 6000
        private const val MAX_HTML_BYTES = 3_000_000L
        private const val MAX_HTML_CHARS = 600_000
        private const val USER_AGENT =
            "Mozilla/5.0 (Linux; Android 17) AppleWebKit/537.36 Mias/1.0 Research"
    }
}
