package dev.kid.core.agent.capabilities

import dev.kid.core.agent.AgentCapability
import dev.kid.core.agent.ParameterType
import dev.kid.core.agent.ToolParameter
import dev.kid.core.common.KidResult
import dev.kid.core.common.runCatchingKid
import dev.kid.core.resilience.ConnectivityMonitor
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpHeaders
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

    override suspend fun execute(input: Map<String, String>): KidResult<String> {
        val url = input["url"] ?: return KidResult.Error("Missing required parameter: url")
        val maxChars = input["max_chars"]?.toIntOrNull() ?: DEFAULT_MAX_CHARS

        if (!connectivity.isOnline) {
            return KidResult.Error("No internet connection available")
        }

        return runCatchingKid {
            val response = httpClient.get(url) {
                header(HttpHeaders.UserAgent, USER_AGENT)
                header(HttpHeaders.Accept, "text/html,text/plain")
            }

            if (!response.status.isSuccess()) {
                throw RuntimeException("HTTP ${response.status.value}: Failed to fetch $url")
            }

            val body = response.bodyAsText()
            val text = stripHtml(body)
            if (text.length > maxChars) text.take(maxChars) + "\n...[truncated]" else text
        }
    }

    private fun stripHtml(html: String): String {
        return html
            .replace(Regex("<script[^>]*>[\\s\\S]*?</script>", RegexOption.IGNORE_CASE), "")
            .replace(Regex("<style[^>]*>[\\s\\S]*?</style>", RegexOption.IGNORE_CASE), "")
            .replace(Regex("<[^>]+>"), " ")
            .replace(Regex("&nbsp;|&amp;|&lt;|&gt;|&quot;"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()
    }

    companion object {
        private const val DEFAULT_MAX_CHARS = 4000
        private const val USER_AGENT = "Kid-AI/1.0 (Local Agent)"
    }
}
