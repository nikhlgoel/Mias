package dev.mias.core.agent.capabilities

import java.net.URI

/**
 * Pure HTML → content helpers shared by the web capabilities. No network, no
 * Android — just string work, so it's fully unit-testable.
 *
 * Provides two things [WebAnswerCapability] needs from a fetched page:
 *  - readable article text (scripts/nav/boilerplate stripped), and
 *  - the page's lead image URL (og:image / twitter:image / first content img),
 *    resolved to an absolute URL.
 */
object WebContentExtractor {

    /**
     * Extract readable text from HTML: removes scripts, styles, nav, header,
     * footer; turns block elements into line breaks; strips remaining tags and
     * decodes common entities. If [focus] is given, paragraphs mentioning it
     * are floated to the front.
     */
    fun extractReadableText(html: String, focus: String? = null): String {
        var text = html
            .replace(SCRIPT, "")
            .replace(STYLE, "")
            .replace(NAV, "")
            .replace(FOOTER, "")
            .replace(HEADER, "")

        text = text
            .replace(BLOCK_CLOSE, "\n")
            .replace(BREAK, "\n")
            .replace(TAG, "")

        text = decodeEntities(text)

        text = text.lines()
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .joinToString("\n")
            .replace(BLANK_RUN, "\n\n")

        if (!focus.isNullOrBlank()) {
            val focusLower = focus.lowercase()
            val paragraphs = text.split("\n\n")
            val relevant = paragraphs.filter { it.lowercase().contains(focusLower) }
            val rest = paragraphs.filter { !it.lowercase().contains(focusLower) }
            text = (relevant + rest).joinToString("\n\n")
        }

        return text.trim()
    }

    /**
     * Find the page's lead image. Prefers Open Graph / Twitter card images
     * (curated by the publisher), then a `<link rel="image_src">`, then the
     * first reasonable `<img>`. Returns an absolute URL, or null if none found
     * or only data/SVG/icon assets are present.
     */
    fun extractLeadImageUrl(html: String, pageUrl: String): String? {
        val candidate = META_OG_IMAGE.firstMatch(html)
            ?: META_TWITTER_IMAGE.firstMatch(html)
            ?: LINK_IMAGE_SRC.firstMatch(html)
            ?: firstContentImage(html)
            ?: return null
        val cleaned = decodeEntities(candidate).trim()
        if (cleaned.isBlank() || cleaned.startsWith("data:")) return null
        val absolute = resolveUrl(cleaned, pageUrl) ?: return null
        // Skip obvious non-photo assets.
        val lower = absolute.lowercase()
        if (lower.endsWith(".svg") || "sprite" in lower || "icon" in lower || "logo" in lower) {
            return null
        }
        return absolute
    }

    private fun firstContentImage(html: String): String? =
        IMG_SRC.findAll(html)
            .map { it.groupValues[1] }
            .firstOrNull { it.isNotBlank() && !it.startsWith("data:") }

    /** Resolve a possibly-relative URL against the page it was found on. */
    private fun resolveUrl(ref: String, pageUrl: String): String? = runCatching {
        when {
            ref.startsWith("http://") || ref.startsWith("https://") -> ref
            ref.startsWith("//") -> "https:$ref"
            else -> URI(pageUrl).resolve(ref).toString()
        }
    }.getOrNull()

    private fun decodeEntities(s: String): String = s
        .replace("&amp;", "&")
        .replace("&lt;", "<")
        .replace("&gt;", ">")
        .replace("&quot;", "\"")
        .replace("&#39;", "'")
        .replace("&nbsp;", " ")
        .replace(NUMERIC_ENTITY, "")

    private fun Regex.firstMatch(html: String): String? =
        find(html)?.groupValues?.getOrNull(1)?.takeIf { it.isNotBlank() }

    private val SCRIPT = Regex("<script[^>]*>[\\s\\S]*?</script>", RegexOption.IGNORE_CASE)
    private val STYLE = Regex("<style[^>]*>[\\s\\S]*?</style>", RegexOption.IGNORE_CASE)
    private val NAV = Regex("<nav[^>]*>[\\s\\S]*?</nav>", RegexOption.IGNORE_CASE)
    private val FOOTER = Regex("<footer[^>]*>[\\s\\S]*?</footer>", RegexOption.IGNORE_CASE)
    private val HEADER = Regex("<header[^>]*>[\\s\\S]*?</header>", RegexOption.IGNORE_CASE)
    private val BLOCK_CLOSE = Regex("</(p|div|h[1-6]|li|br|tr)[^>]*>", RegexOption.IGNORE_CASE)
    private val BREAK = Regex("<(br|hr)[^>]*/?>", RegexOption.IGNORE_CASE)
    private val TAG = Regex("<[^>]+>")
    private val BLANK_RUN = Regex("\n{3,}")
    private val NUMERIC_ENTITY = Regex("&#\\d+;")

    private val META_OG_IMAGE = Regex(
        "<meta[^>]+property=[\"']og:image[\"'][^>]+content=[\"']([^\"']+)[\"']",
        RegexOption.IGNORE_CASE,
    )
    private val META_TWITTER_IMAGE = Regex(
        "<meta[^>]+name=[\"']twitter:image[\"'][^>]+content=[\"']([^\"']+)[\"']",
        RegexOption.IGNORE_CASE,
    )
    private val LINK_IMAGE_SRC = Regex(
        "<link[^>]+rel=[\"']image_src[\"'][^>]+href=[\"']([^\"']+)[\"']",
        RegexOption.IGNORE_CASE,
    )
    private val IMG_SRC = Regex("<img[^>]+src=[\"']([^\"']+)[\"']", RegexOption.IGNORE_CASE)
}
