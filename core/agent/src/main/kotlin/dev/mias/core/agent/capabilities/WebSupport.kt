package dev.mias.core.agent.capabilities

import java.net.URI
import java.security.MessageDigest

/**
 * Pure URL/result hygiene helpers shared by the web capabilities. No network,
 * no Android — fully unit-testable.
 */
object WebSupport {

    /**
     * Whether a URL is something we should actually fetch: http/https, a real
     * host, and not a loopback address. Search engines occasionally emit
     * javascript:/data:/relative artifacts; fetching those wastes a slot or
     * fails confusingly downstream.
     */
    fun isFetchableUrl(url: String): Boolean {
        val trimmed = url.trim()
        if (!trimmed.startsWith("http://", ignoreCase = true) &&
            !trimmed.startsWith("https://", ignoreCase = true)
        ) {
            return false
        }
        val host = host(trimmed) ?: return false
        if (host == "localhost" || host.startsWith("127.") || host == "0.0.0.0") return false
        return true
    }

    /**
     * Whether a "result" URL is actually a search-engine artifact (ad redirect,
     * tracking endpoint) rather than a destination page. DDG ads surface as
     * `duckduckgo.com/y.js?ad_provider=…`; genuine results never point back at
     * the search engine itself.
     */
    fun isSearchArtifact(url: String): Boolean {
        val h = host(url) ?: return true
        return h == "duckduckgo.com" || h.endsWith(".duckduckgo.com")
    }

    /** Lowercased host of a URL, or null if unparseable. */
    fun host(url: String): String? = runCatching {
        URI(url.trim()).host?.lowercase()?.removePrefix("www.")
    }.getOrNull()?.takeIf { it.isNotBlank() }

    /**
     * Pick up to [max] hits favouring **host diversity**: reading the same
     * outlet three times grounds worse than three independent sources. First
     * pass takes the highest-ranked hit per distinct host; if slots remain,
     * they're filled with the remaining hits in rank order.
     */
    fun selectDiverse(hits: List<SearchHit>, max: Int): List<SearchHit> {
        if (max <= 0) return emptyList()
        val deduped = hits.distinctBy { it.url }
        val seenHosts = mutableSetOf<String>()
        val primary = mutableListOf<SearchHit>()
        val overflow = mutableListOf<SearchHit>()
        for (hit in deduped) {
            val h = host(hit.url)
            if (h != null && seenHosts.add(h)) primary += hit else overflow += hit
        }
        return (primary + overflow).take(max)
    }

    /**
     * Stable, filesystem-safe digest of a string (for cache file names).
     * SHA-256, first 16 hex chars — collision-safe at on-device cache scale,
     * unlike `String.hashCode()`.
     */
    fun stableHash(s: String): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(s.toByteArray(Charsets.UTF_8))
        return digest.joinToString("") { "%02x".format(it) }.take(16)
    }
}
