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
import io.ktor.http.isSuccess
import java.net.URLDecoder
import java.net.URLEncoder
import javax.inject.Inject
import javax.inject.Singleton

/** A single web search result. Public so other capabilities can chain on it. */
data class SearchHit(val title: String, val url: String, val snippet: String)

/**
 * Web search agent — turns a natural-language query into a ranked list of
 * result titles, URLs, and snippets that the model (or [WebAnswerCapability])
 * can then read with `web_research` / `web_answer`.
 *
 * Backed by DuckDuckGo's HTML endpoint: no API key, no account, no tracking
 * cookies — consistent with the app's local-first, privacy-first posture.
 */
@Singleton
class WebSearchCapability @Inject constructor(
    private val httpClient: HttpClient,
    private val connectivity: ConnectivityMonitor,
) : AgentCapability {

    override val name = "web_search"

    override val description = "Search the web for information. Input a natural-language " +
        "query; returns a numbered list of result titles, URLs, and snippets. " +
        "Follow up with web_fetch or web_research on a URL to read a result in full."

    override val parameters = listOf(
        ToolParameter("query", "What to search for"),
        ToolParameter(
            "max_results",
            "How many results to return (default 6, max 10)",
            required = false,
            type = ParameterType.INT,
        ),
    )

    /**
     * Run the search and return structured hits, in rank order. Throws on a
     * transport/HTTP failure so callers can decide how to degrade. Does not
     * check connectivity — callers that need that should test it first.
     */
    suspend fun search(query: String, limit: Int = DEFAULT_RESULTS): List<SearchHit> {
        val capped = limit.coerceIn(1, MAX_RESULTS)
        val url = "$ENDPOINT?q=${URLEncoder.encode(query, "UTF-8")}&kl=us-en"
        val response = httpClient.get(url) {
            header(HttpHeaders.UserAgent, BROWSER_UA)
            header(HttpHeaders.Accept, "text/html")
            header(HttpHeaders.AcceptLanguage, "en-US,en;q=0.9")
        }
        if (!response.status.isSuccess()) {
            throw RuntimeException("Search failed: HTTP ${response.status.value}")
        }
        return parseResults(response.bodyAsText(), capped)
    }

    override suspend fun execute(input: Map<String, String>): MiasResult<String> {
        val query = input["query"]?.trim()
        if (query.isNullOrBlank()) return MiasResult.Error("Missing required parameter: query")
        val limit = (input["max_results"]?.toIntOrNull() ?: DEFAULT_RESULTS).coerceIn(1, MAX_RESULTS)

        if (!connectivity.isOnline) {
            return MiasResult.Error("No internet connection available")
        }

        return runCatchingMias {
            val results = search(query, limit)
            if (results.isEmpty()) {
                "No results found for \"$query\"."
            } else {
                buildString {
                    appendLine("Search results for \"$query\":")
                    results.forEachIndexed { i, r ->
                        appendLine()
                        appendLine("${i + 1}. ${r.title}")
                        appendLine("   ${r.url}")
                        if (r.snippet.isNotBlank()) appendLine("   ${r.snippet}")
                    }
                }.trim()
            }
        }
    }

    /** Extract result anchors + snippets from the DDG HTML page, in order. */
    private fun parseResults(html: String, limit: Int): List<SearchHit> {
        val titles = RESULT_ANCHOR.findAll(html).map { m ->
            decodeUrl(m.groupValues[1]) to stripTags(m.groupValues[2])
        }.toList()
        val snippets = SNIPPET.findAll(html).map { stripTags(it.groupValues[1]) }.toList()

        return titles.take(limit).mapIndexed { i, (link, title) ->
            SearchHit(
                title = title.ifBlank { link },
                url = link,
                snippet = snippets.getOrNull(i).orEmpty(),
            )
        }.filter { it.url.isNotBlank() }
    }

    /** DDG wraps real URLs as `//duckduckgo.com/l/?uddg=<encoded>&…`. */
    private fun decodeUrl(href: String): String {
        val cleaned = href.replace("&amp;", "&")
        val uddg = UDDG.find(cleaned)?.groupValues?.getOrNull(1)
        return when {
            uddg != null -> runCatching { URLDecoder.decode(uddg, "UTF-8") }.getOrDefault("")
            cleaned.startsWith("//") -> "https:$cleaned"
            else -> cleaned
        }
    }

    private fun stripTags(s: String): String = s
        .replace(Regex("<[^>]+>"), "")
        .replace("&amp;", "&")
        .replace("&lt;", "<")
        .replace("&gt;", ">")
        .replace("&quot;", "\"")
        .replace("&#x27;", "'")
        .replace("&#39;", "'")
        .replace(Regex("\\s+"), " ")
        .trim()

    companion object {
        private const val ENDPOINT = "https://html.duckduckgo.com/html/"
        private const val DEFAULT_RESULTS = 6
        private const val MAX_RESULTS = 10
        private const val BROWSER_UA =
            "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) " +
                "Chrome/120.0 Mobile Safari/537.36"

        private val RESULT_ANCHOR =
            Regex("<a[^>]*class=\"result__a\"[^>]*href=\"([^\"]*)\"[^>]*>(.*?)</a>", RegexOption.DOT_MATCHES_ALL)
        private val SNIPPET =
            Regex("<a[^>]*class=\"result__snippet\"[^>]*>(.*?)</a>", RegexOption.DOT_MATCHES_ALL)
        private val UDDG = Regex("[?&]uddg=([^&]+)")
    }
}
