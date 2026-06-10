package dev.mias.core.agent.capabilities

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import dagger.hilt.android.qualifiers.ApplicationContext
import dev.mias.core.agent.AgentCapability
import dev.mias.core.agent.ParameterType
import dev.mias.core.agent.ToolParameter
import dev.mias.core.common.MiasResult
import dev.mias.core.common.cache.TtlCache
import dev.mias.core.common.runCatchingMias
import dev.mias.core.resilience.ConnectivityMonitor
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.contentLength
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
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
 * Searches the web, opens the top N results **in parallel**, extracts each
 * article's readable text (and, for visual queries, its lead image), and
 * returns grounded reference material with numbered citations. This is what
 * turns "list of sites" into "actual answer with sources".
 *
 * Robustness model — every stage degrades instead of failing the turn:
 *  - each source read is individually time-boxed and failure-isolated; a dead
 *    page falls back to its search snippet, never sinks its siblings;
 *  - pages are screened by content-type and declared size before download,
 *    and processed text is hard-capped, so a misbehaving server can't flood
 *    memory or stall extraction;
 *  - sources are chosen for host diversity (three independent outlets ground
 *    better than three pages from one site);
 *  - short-TTL caches make a regenerate (or an agentic follow-up on the same
 *    query) free instead of a second network round;
 *  - image fetches are content-type/size-screened, decoded downscaled, and
 *    disk-cached with read-back and pruning, so the cache can never grow
 *    unbounded.
 *
 * Depth (how many sources, how much of each, whether to pull images) is decided
 * by the caller — the app scales it to the device tier and the query. Images
 * are fetched through this app's own client; no third-party image loader and
 * no render-time calls to arbitrary CDNs.
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

    /** Extracted page content: readable text (capped) + the page's lead image URL. */
    private class ExtractedPage(val text: String, val leadImageUrl: String?)

    /** One search hit paired with its (possibly failed → null) page read. */
    private class FetchedSource(val hit: SearchHit, val page: ExtractedPage?)

    /**
     * url → extracted content. Makes a regenerate, or the same source surfacing
     * for a related query, free. Text is capped at [EXTRACT_CACHE_CHARS] per
     * entry so the cache stays a few hundred KB at worst.
     */
    private val pageCache = TtlCache<String, ExtractedPage>(
        ttlMillis = PAGE_CACHE_TTL_MS,
        maxEntries = PAGE_CACHE_ENTRIES,
    )

    /**
     * Full-result cache, deliberately tiny: it can hold decoded bitmaps, so a
     * low entry cap bounds memory. Covers the immediate-regenerate case.
     */
    private val answerCache = TtlCache<String, WebAnswerResult>(
        ttlMillis = ANSWER_CACHE_TTL_MS,
        maxEntries = ANSWER_CACHE_ENTRIES,
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
     * Full structured answer for the app layer. Never throws; degrades to an
     * empty (but honest) result when offline or when nothing could be read.
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

        val wantedSources = maxResults.coerceIn(1, MAX_SOURCES)
        val perSourceChars = maxCharsPerSource.coerceIn(MIN_CHARS_PER_SOURCE, MAX_CHARS_PER_SOURCE)
        val cacheKey = "${query.trim().lowercase()}|$wantedSources|$perSourceChars|$includeImages"
        answerCache.get(cacheKey)?.let { return@withContext it }

        // Search wide (cached upstream), then pick the most host-diverse subset.
        val hits = runCatching { webSearch.search(query, SEARCH_BREADTH) }
            .getOrDefault(emptyList())
        val chosen = WebSupport.selectDiverse(hits, wantedSources)
        if (chosen.isEmpty()) {
            return@withContext WebAnswerResult("", emptyList(), emptyList(), online = true)
        }

        // Read all chosen pages in parallel. Each read is time-boxed and
        // failure-isolated: a hung or broken source becomes `page == null`
        // (snippet fallback) without affecting its siblings.
        val fetched: List<FetchedSource> = coroutineScope {
            chosen.map { hit ->
                async {
                    val page = withTimeoutOrNull(PER_SOURCE_TIMEOUT_MS) {
                        runCatching { fetchExtracted(hit.url) }.getOrNull()
                    }
                    FetchedSource(hit, page)
                }
            }.awaitAll()
        }

        val sources = fetched.mapIndexed { i, f -> WebSource(i + 1, f.hit.title, f.hit.url) }
        val blocks = fetched.mapIndexed { i, f ->
            // Article text when the read succeeded; the search snippet as the
            // grounded-but-thin fallback. Never fabricate, never omit a cite.
            val excerpt = f.page?.text?.take(perSourceChars)?.takeIf { it.isNotBlank() }
                ?: f.hit.snippet
            buildString {
                append("[").append(i + 1).append("] ").append(f.hit.title)
                append(" — ").append(f.hit.url).append('\n')
                append(excerpt)
            }
        }

        val images = if (includeImages) fetchLeadImages(fetched) else emptyList()

        val promptText = buildString {
            append("Web results for \"").append(query)
            append("\" — base your answer on these and cite sources as [1], [2]:\n\n")
            append(blocks.joinToString("\n\n"))
        }
        val result = WebAnswerResult(promptText, sources, images, online = true)
        // Memory-cache only image-less results: bitmaps would pin megabytes of
        // heap for the TTL, and the image disk cache already makes a repeat
        // visual query cheap (read-back, no network).
        if (images.isEmpty()) answerCache.put(cacheKey, result)
        result
    }

    // ── Page reading ────────────────────────────────────────────────────

    /**
     * Fetch a page and extract readable text + lead image URL, via the page
     * cache. Returns null for anything that shouldn't be grounded on: non-HTML
     * content, oversized documents, error statuses, unparseable bodies.
     */
    private suspend fun fetchExtracted(url: String): ExtractedPage? {
        if (!WebSupport.isFetchableUrl(url)) return null
        pageCache.get(url)?.let { return it }

        val response = httpClient.get(url) {
            header(HttpHeaders.UserAgent, BROWSER_UA)
            header(HttpHeaders.Accept, "text/html,application/xhtml+xml")
            header(HttpHeaders.AcceptLanguage, "en-US,en;q=0.9")
        }
        if (!response.status.isSuccess()) return null

        // Screen by declared type/size before reading the body: a PDF, a video,
        // or a 50 MB page is not something to pull through an HTML extractor.
        response.contentType()?.let { type ->
            val isHtmlish = type.match(ContentType.Text.Any) ||
                (type.contentType == "application" && "xml" in type.contentSubtype)
            if (!isHtmlish) return null
        }
        response.contentLength()?.let { declared ->
            if (declared > MAX_HTML_BYTES) return null
        }

        // Cap the processed text even when no length was declared, so regex
        // extraction work is bounded regardless of what the server sends.
        val html = response.bodyAsText().take(MAX_HTML_CHARS)
        val text = WebContentExtractor.extractReadableText(html).take(EXTRACT_CACHE_CHARS)
        if (text.isBlank()) return null

        val page = ExtractedPage(
            text = text,
            leadImageUrl = WebContentExtractor.extractLeadImageUrl(html, url),
        )
        pageCache.put(url, page)
        return page
    }

    // ── Images ──────────────────────────────────────────────────────────

    /**
     * Download the lead images of the first [MAX_IMAGES] sources that have
     * one, in parallel, each individually time-boxed. Image failures never
     * affect the text answer.
     */
    private suspend fun fetchLeadImages(fetched: List<FetchedSource>): List<WebImage> =
        coroutineScope {
            fetched.mapIndexedNotNull { i, f ->
                f.page?.leadImageUrl?.let { Triple(i + 1, f.hit.url, it) }
            }
                .take(MAX_IMAGES)
                .map { (sourceIndex, sourceUrl, imageUrl) ->
                    async {
                        withTimeoutOrNull(IMAGE_TIMEOUT_MS) {
                            runCatching { fetchImage(imageUrl) }.getOrNull()
                        }?.let { WebImage(sourceIndex, sourceUrl, it) }
                    }
                }
                .awaitAll()
                .filterNotNull()
        }

    /**
     * Fetch one image via our own client with a disk cache: cache hit decodes
     * straight from disk; a miss downloads (content-type/size screened), caches
     * the bytes, prunes the cache directory, and decodes downscaled. A corrupt
     * cache file is deleted and re-fetched rather than poisoning the entry.
     */
    private suspend fun fetchImage(url: String): Bitmap? {
        if (!WebSupport.isFetchableUrl(url)) return null

        val cacheFile = File(imageCacheDir(), "${WebSupport.stableHash(url)}.img")
        if (cacheFile.exists()) {
            runCatching { cacheFile.readBytes() }.getOrNull()
                ?.let { bytes -> decodeDownscaled(bytes)?.let { return it } }
            runCatching { cacheFile.delete() }
        }

        val response = httpClient.get(url) {
            header(HttpHeaders.UserAgent, BROWSER_UA)
            header(HttpHeaders.Accept, "image/*")
        }
        if (!response.status.isSuccess()) return null
        response.contentType()?.let { type ->
            if (!type.match(ContentType.Image.Any)) return null
        }
        response.contentLength()?.let { declared ->
            if (declared > MAX_IMAGE_BYTES) return null
        }

        val bytes: ByteArray = response.body()
        if (bytes.size < MIN_IMAGE_BYTES || bytes.size > MAX_IMAGE_BYTES) return null
        val bitmap = decodeDownscaled(bytes) ?: return null

        // Best-effort persistence; never let cache IO break the answer.
        runCatching {
            cacheFile.writeBytes(bytes)
            pruneImageCache()
        }
        return bitmap
    }

    private fun imageCacheDir(): File =
        File(context.cacheDir, IMAGE_CACHE_DIR).apply { mkdirs() }

    /** Keep the image cache under [IMAGE_CACHE_MAX_BYTES], deleting oldest first. */
    private fun pruneImageCache() {
        val files = imageCacheDir().listFiles()?.sortedBy { it.lastModified() } ?: return
        var total = files.sumOf { it.length() }
        for (f in files) {
            if (total <= IMAGE_CACHE_MAX_BYTES) break
            total -= f.length()
            runCatching { f.delete() }
        }
    }

    /** Decode bytes to a bitmap no larger than [MAX_IMAGE_DIM] on its long side. */
    private fun decodeDownscaled(bytes: ByteArray): Bitmap? {
        if (bytes.isEmpty()) return null
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
        // Source selection.
        private const val DEFAULT_RESULTS = 2
        private const val MAX_SOURCES = 5
        private const val SEARCH_BREADTH = 8 // search wide, then pick diverse hosts

        // Per-source excerpt sizing.
        private const val DEFAULT_CHARS_PER_SOURCE = 2500
        private const val MIN_CHARS_PER_SOURCE = 500
        private const val MAX_CHARS_PER_SOURCE = 4000

        // Time boxes. The app layer wraps the whole answer in its own outer
        // timeout; these keep any single source/image from eating that budget.
        private const val PER_SOURCE_TIMEOUT_MS = 8_000L
        private const val IMAGE_TIMEOUT_MS = 6_000L

        // Page screening / processing bounds.
        private const val MAX_HTML_BYTES = 3_000_000L // skip if Content-Length says bigger
        private const val MAX_HTML_CHARS = 600_000 // hard cap on text processed
        private const val EXTRACT_CACHE_CHARS = 8_000 // per-entry cap in pageCache

        // Caches.
        private const val PAGE_CACHE_TTL_MS = 10 * 60 * 1000L
        private const val PAGE_CACHE_ENTRIES = 24
        private const val ANSWER_CACHE_TTL_MS = 3 * 60 * 1000L
        private const val ANSWER_CACHE_ENTRIES = 4

        // Images.
        private const val MAX_IMAGES = 2
        private const val MAX_IMAGE_DIM = 768
        private const val MIN_IMAGE_BYTES = 2_048
        private const val MAX_IMAGE_BYTES = 5_000_000L
        private const val IMAGE_CACHE_DIR = "web_images"
        private const val IMAGE_CACHE_MAX_BYTES = 16_000_000L

        private const val BROWSER_UA =
            "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) " +
                "Chrome/120.0 Mobile Safari/537.36"
    }
}
