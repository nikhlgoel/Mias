package dev.mias.core.data.rag

/**
 * Splits document text into overlapping, embeddable chunks.
 *
 * Boundary choice is graded, best-first, searched within the back half of the
 * window so chunks never get tiny:
 *  1. paragraph break (`\n\n`) — keeps whole paragraphs together,
 *  2. sentence end (`. / ! / ?` followed by whitespace) — never splits
 *     mid-sentence, and the whitespace requirement keeps decimals ("3.14")
 *     and dotted abbreviations from being mistaken for sentence ends,
 *  3. any newline,
 *  4. any space,
 *  5. hard cut (pathological input with no separators at all, e.g. a Base64
 *     blob — still bounded, never an infinite loop).
 *
 * Overlap preserves context across the seam so a fact straddling two chunks is
 * fully present in at least one of them.
 *
 * [maxChunks] bounds the total: each chunk costs one embedding pass at ingest
 * (~50–200 ms on-device), so an unbounded document would freeze ingestion for
 * minutes and bloat the DB. Callers cap input length too; this is the
 * belt-and-suspenders bound.
 */
object TextChunker {

    const val TARGET_CHARS = 800
    const val OVERLAP_CHARS = 150

    /** ~600 chunks ≈ 480 KB of text ≈ a few minutes of worst-case ingest. */
    const val MAX_CHUNKS = 600

    fun chunk(
        text: String,
        targetChars: Int = TARGET_CHARS,
        overlapChars: Int = OVERLAP_CHARS,
        maxChunks: Int = MAX_CHUNKS,
    ): List<String> {
        require(targetChars > 0) { "targetChars must be positive" }
        require(overlapChars in 0 until targetChars) { "overlap must be smaller than target" }

        val clean = text.replace("\r\n", "\n").replace('\r', '\n').trim()
        if (clean.isBlank()) return emptyList()
        if (clean.length <= targetChars) return listOf(clean)

        val chunks = mutableListOf<String>()
        var start = 0
        while (start < clean.length && chunks.size < maxChunks) {
            val hardEnd = minOf(start + targetChars, clean.length)
            val end = if (hardEnd < clean.length) {
                start + breakPoint(clean.substring(start, hardEnd), targetChars / 2)
            } else {
                hardEnd
            }
            clean.substring(start, end).trim().takeIf { it.isNotBlank() }?.let { chunks.add(it) }
            if (end >= clean.length) break
            start = (end - overlapChars).coerceAtLeast(start + 1)
        }
        return chunks
    }

    /**
     * Best break position (exclusive end) within [window], graded by boundary
     * quality. Only positions after [minPos] qualify; falls back to a hard cut
     * at the window's end when nothing suitable exists.
     */
    private fun breakPoint(window: String, minPos: Int): Int {
        // 1. Paragraph break — cut after the blank line.
        val para = window.lastIndexOf("\n\n")
        if (para > minPos) return para + 2

        // 2. Sentence end — terminator followed by whitespace.
        for (i in window.length - 2 downTo minPos + 1) {
            val c = window[i]
            if ((c == '.' || c == '!' || c == '?') && window[i + 1].isWhitespace()) {
                return i + 1
            }
        }

        // 3. Any newline.
        val newline = window.lastIndexOf('\n')
        if (newline > minPos) return newline + 1

        // 4. Any space — at least never split inside a word.
        val space = window.lastIndexOf(' ')
        if (space > minPos) return space + 1

        // 5. No separators at all: hard cut.
        return window.length
    }
}
