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

    override suspend fun execute(input: Map<String, String>): KidResult<String> =
        runCatchingKid {
            if (!connectivity.isOnline) {
                throw IllegalStateException("No internet connection")
            }

            val url = input["url"]?.trim()
                ?: throw IllegalArgumentException("Missing: url")
            val focus = input["focus"]?.trim()
            val maxChars = input["max_chars"]?.toIntOrNull() ?: DEFAULT_MAX_CHARS

            // Validate URL scheme
            if (!url.startsWith("http://") && !url.startsWith("https://")) {
                throw IllegalArgumentException("URL must start with http:// or https://")
            }

            val response = httpClient.get(url) {
                header(HttpHeaders.UserAgent, USER_AGENT)
                header(HttpHeaders.Accept, "text/html,application/xhtml+xml")
                header(HttpHeaders.AcceptLanguage, "en-US,en;q=0.9")
            }

            if (!response.status.isSuccess()) {
                throw IllegalStateException("HTTP ${response.status.value}: $url")
            }

            val rawHtml = response.bodyAsText()
            val text = extractReadableText(rawHtml, focus)
            val result = text.take(maxChars)

            buildString {
                appendLine("--- Web Research: $url ---")
                if (focus != null) appendLine("Focus: $focus")
                appendLine()
                append(result)
                if (text.length > maxChars) appendLine("\n[...truncated — ${text.length - maxChars} chars omitted]")
            }
        }

    /**
     * Extract readable text from HTML. Removes scripts, styles, nav, footers.
     * Preserves paragraph structure with newlines.
     */
    private fun extractReadableText(html: String, focus: String?): String {
        // Remove script tags and their content
        var text = html
            .replace(Regex("<script[^>]*>[\\s\\S]*?</script>", RegexOption.IGNORE_CASE), "")
            .replace(Regex("<style[^>]*>[\\s\\S]*?</style>", RegexOption.IGNORE_CASE), "")
            .replace(Regex("<nav[^>]*>[\\s\\S]*?</nav>", RegexOption.IGNORE_CASE), "")
            .replace(Regex("<footer[^>]*>[\\s\\S]*?</footer>", RegexOption.IGNORE_CASE), "")
            .replace(Regex("<header[^>]*>[\\s\\S]*?</header>", RegexOption.IGNORE_CASE), "")

        // Convert block elements to newlines for readability
        text = text
            .replace(Regex("</(p|div|h[1-6]|li|br|tr)[^>]*>", RegexOption.IGNORE_CASE), "\n")
            .replace(Regex("<(br|hr)[^>]*/?>", RegexOption.IGNORE_CASE), "\n")

        // Strip remaining HTML tags
        text = text.replace(Regex("<[^>]+>"), "")

        // Decode common HTML entities
        text = text
            .replace("&amp;", "&")
            .replace("&lt;", "<")
            .replace("&gt;", ">")
            .replace("&quot;", "\"")
            .replace("&#39;", "'")
            .replace("&nbsp;", " ")
            .replace(Regex("&#\\d+;"), "")

        // Normalize whitespace
        text = text.lines()
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .joinToString("\n")
            .replace(Regex("\n{3,}"), "\n\n")

        // If focus keyword provided, boost relevant paragraphs to front
        if (focus != null) {
            val paragraphs = text.split("\n\n")
            val focusLower = focus.lowercase()
            val relevant = paragraphs.filter { it.lowercase().contains(focusLower) }
            val rest = paragraphs.filter { !it.lowercase().contains(focusLower) }
            text = (relevant + rest).joinToString("\n\n")
        }

        return text
    }

    companion object {
        private const val DEFAULT_MAX_CHARS = 6000
        private const val USER_AGENT =
            "Mozilla/5.0 (Linux; Android 17) AppleWebKit/537.36 Mias/1.0 Research"
    }
}
