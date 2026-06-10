package dev.mias.core.agent.capabilities

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import dagger.hilt.android.qualifiers.ApplicationContext
import dev.mias.core.agent.AgentCapability
import dev.mias.core.agent.ParameterType
import dev.mias.core.agent.ToolParameter
import dev.mias.core.common.MiasResult
import dev.mias.core.common.runCatchingMias
import dev.mias.core.resilience.ConnectivityMonitor
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpHeaders
import io.ktor.http.isSuccess
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/** A cited source behind a web answer. */
data class WebSource(val index: Int, val title: String, val url: String)

/** A lead image pulled from a cited page (already fetched + decoded). */
data class WebImage(val sourceIndex: Int, val sourceUrl: String, val bitmap: Bitmap)

/**
 * Structured result of a web answer, for the UI layer.
 *  - [promptText] is the grounded reference material fed to the model.
 *  - [sources] back the tappable [1][2] citations.
 *  - [images] are lead images for visual queries (empty otherwise).
 *  - [online] is false when there was no connectivity (everything else empty).
 */
data class WebAnswerResult(
    val promptText: String,
    val sources: List<WebSource>,
    val images: List<WebImage>,
    val online: Boolean,
)

/**
 * web_answer — the discovery+reading step the agent previously lacked.
 *
 * Searches the web, opens the top N results, extracts each article's readable
 * text (and, for visual queries, its lead image), and returns grounded
 * reference material with numbered citations. This is what turns "list of
 * sites" into "actual answer with sources".
 *
 * Depth (how many sources, how much of each, whether to pull images) is decided
 * by the caller — the app scales it to the device tier and the query. Images
 * are fetched through this app's own client and cached locally; no third-party
 * image loader and no render-time calls to arbitrary CDNs.
 */
@Singleton
class WebAnswerCapability @Inject constructor(
    @ApplicationContext private val context: Context,
    private val httpClient: HttpClient,
    private val connectivity: ConnectivityMonitor,
    private val webSearch: WebSearchCapability,
) : AgentCapability {

    override val name = "web_answer"

    override val description = "Search the web AND read the top results, returning a grounded " +
        "summary with numbered source citations. Prefer this over web_search when the user " +
        "wants an actual answer about current/online information, not just a list of links."

    override val parameters = listOf(
        ToolParameter("query", "What to find out"),
        ToolParameter(
            "max_results",
            "How many results to open and read (default 2)",
            required = false,
            type = ParameterType.INT,
        ),
    )

    /** Agentic tool path: returns just the grounded text the model reads. */
    override suspend fun execute(input: Map<String, String>): MiasResult<String> {
        val query = input["query"]?.trim()
        if (query.isNullOrBlank()) return MiasResult.Error("Missing required parameter: query")
        if (!connectivity.isOnline) return MiasResult.Error("No internet connection available")
        val maxResults = (input["max_results"]?.toIntOrNull() ?: DEFAULT_RESULTS)
        return runCatchingMias {
            answer(query, maxResults = maxResults, includeImages = false).promptText
                .ifBlank { "No readable results found for \"$query\"." }
        }
    }

    /**
     * Full structured answer for the app layer. Best-effort and resilient: a
     * page that fails to load is skipped (its search snippet is used instead),
     * and image fetch failures never block the text answer.
     */
    suspend fun answer(
        query: String,
        maxResults: Int = DEFAULT_RESULTS,
        maxCharsPerSource: Int = DEFAULT_CHARS_PER_SOURCE,
        includeImages: Boolean = false,
    ): WebAnswerResult = withContext(Dispatchers.IO) {
        if (!connectivity.isOnline) {
            return@withContext WebAnswerResult("", emptyList(), emptyList(), online = false)
        }

        val hits = runCatching { webSearch.search(query, maxResults.coerceIn(1, MAX_RESULTS)) }
            .getOrDefault(emptyList())
        if (hits.isEmpty()) {
            return@withContext WebAnswerResult("", emptyList(), emptyList(), online = true)
        }

        val sources = mutableListOf<WebSource>()
        val images = mutableListOf<WebImage>()
        val blocks = mutableListOf<String>()

        hits.take(maxResults).forEachIndexed { i, hit ->
            val index = i + 1
            sources += WebSource(index, hit.title, hit.url)

            val html = runCatching { fetchHtml(hit.url) }.getOrNull()
            val body = html?.let {
                WebContentExtractor.extractReadableText(it).take(maxCharsPerSource)
            }
            // Use the article text when we got it; otherwise fall back to the
            // search snippet so the source is still grounded, not invented.
            val excerpt = body?.takeIf { it.isNotBlank() } ?: hit.snippet
            blocks += buildString {
                append("[").append(index).append("] ").append(hit.title)
                append(" — ").append(hit.url).append('\n')
                append(excerpt)
            }

            if (includeImages && images.size < MAX_IMAGES && html != null) {
                WebContentExtractor.extractLeadImageUrl(html, hit.url)?.let { imgUrl ->
                    fetchImage(imgUrl)?.let { bmp ->
                        images += WebImage(index, hit.url, bmp)
                    }
                }
            }
        }

        val promptText = buildString {
            append("Web results for \"").append(query)
            append("\" — base your answer on these and cite sources as [1], [2]:\n\n")
            append(blocks.joinToString("\n\n"))
        }
        WebAnswerResult(promptText, sources, images, online = true)
    }

    private suspend fun fetchHtml(url: String): String {
        val response = httpClient.get(url) {
            header(HttpHeaders.UserAgent, BROWSER_UA)
            header(HttpHeaders.Accept, "text/html,application/xhtml+xml")
            header(HttpHeaders.AcceptLanguage, "en-US,en;q=0.9")
        }
        if (!response.status.isSuccess()) throw RuntimeException("HTTP ${response.status.value}")
        return response.bodyAsText()
    }

    /** Fetch image bytes via our own client, cache locally, decode downscaled. */
    private suspend fun fetchImage(url: String): Bitmap? = runCatching {
        val response = httpClient.get(url) { header(HttpHeaders.UserAgent, BROWSER_UA) }
        if (!response.status.isSuccess()) return null
        val bytes: ByteArray = response.body()
        if (bytes.size < MIN_IMAGE_BYTES || bytes.size > MAX_IMAGE_BYTES) return null

        // Cache locally (privacy-first: under our control, no remote loader).
        val dir = File(context.cacheDir, "web_images").apply { mkdirs() }
        runCatching { File(dir, "${url.hashCode()}.img").writeBytes(bytes) }

        decodeDownscaled(bytes)
    }.getOrNull()

    private fun decodeDownscaled(bytes: ByteArray): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
        var sample = 1
        val largest = maxOf(bounds.outWidth, bounds.outHeight)
        while (largest / sample > MAX_IMAGE_DIM) sample *= 2
        val opts = BitmapFactory.Options().apply { inSampleSize = sample }
        return BitmapFactory.decodeByteArray(bytes, 0, bytes.size, opts)
    }

    companion object {
        private const val DEFAULT_RESULTS = 2
        private const val MAX_RESULTS = 5
        private const val DEFAULT_CHARS_PER_SOURCE = 2500
        private const val MAX_IMAGES = 2
        private const val MAX_IMAGE_DIM = 768
        private const val MIN_IMAGE_BYTES = 2_048
        private const val MAX_IMAGE_BYTES = 5_000_000
        private const val BROWSER_UA =
            "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) " +
                "Chrome/120.0 Mobile Safari/537.36"
    }
}
