package dev.mias.core.data.rag

/**
 * Splits document text into overlapping, embeddable chunks. Tries to break on a
 * natural boundary (paragraph/sentence) near the target size so a chunk rarely
 * cuts a sentence in half; overlap preserves context across the seam.
 */
object TextChunker {

    private const val TARGET_CHARS = 800
    private const val OVERLAP_CHARS = 150
    private val BOUNDARY_CHARS = charArrayOf('\n', '.', '!', '?')

    fun chunk(
        text: String,
        targetChars: Int = TARGET_CHARS,
        overlapChars: Int = OVERLAP_CHARS,
    ): List<String> {
        val clean = text.replace("\r\n", "\n").replace('\r', '\n').trim()
        if (clean.isBlank()) return emptyList()
        if (clean.length <= targetChars) return listOf(clean)

        val chunks = mutableListOf<String>()
        var start = 0
        while (start < clean.length) {
            var end = minOf(start + targetChars, clean.length)
            if (end < clean.length) {
                val boundary = clean.substring(start, end).lastIndexOfAny(BOUNDARY_CHARS)
                // Only honor a boundary in the back half so chunks don't get tiny.
                if (boundary > targetChars / 2) end = start + boundary + 1
            }
            clean.substring(start, end).trim().takeIf { it.isNotBlank() }?.let { chunks.add(it) }
            if (end >= clean.length) break
            start = (end - overlapChars).coerceAtLeast(start + 1)
        }
        return chunks
    }
}
