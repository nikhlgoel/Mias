package dev.mias.core.data.rag

import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

@DisplayName("TextChunker")
class TextChunkerTest {

    @Nested
    @DisplayName("basics")
    inner class Basics {

        @Test
        fun `blank input produces no chunks`() {
            assertThat(TextChunker.chunk("")).isEmpty()
            assertThat(TextChunker.chunk("   \n\n  ")).isEmpty()
        }

        @Test
        fun `short input is a single chunk`() {
            val text = "A short note."
            assertThat(TextChunker.chunk(text)).containsExactly(text)
        }

        @Test
        fun `normalizes windows line endings`() {
            val chunks = TextChunker.chunk("line one\r\nline two")
            assertThat(chunks).containsExactly("line one\nline two")
        }

        @Test
        fun `every chunk respects the target size`() {
            val text = sentenceText(repeats = 60)
            val chunks = TextChunker.chunk(text, targetChars = 200, overlapChars = 40)
            assertThat(chunks).isNotEmpty()
            chunks.forEach { assertThat(it.length).isAtMost(200) }
        }

        @Test
        fun `no word is lost across seams`() {
            val words = (1..300).map { "word$it" }
            val text = words.joinToString(" ")
            val joined = TextChunker.chunk(text, targetChars = 200, overlapChars = 40)
                .joinToString(" ")
            words.forEach { assertThat(joined).contains(it) }
        }

        @Test
        fun `rejects nonsensical configuration`() {
            try {
                TextChunker.chunk("x", targetChars = 0)
                assert(false) { "expected IllegalArgumentException" }
            } catch (_: IllegalArgumentException) {
            }
            try {
                TextChunker.chunk("x", targetChars = 100, overlapChars = 100)
                assert(false) { "expected IllegalArgumentException" }
            } catch (_: IllegalArgumentException) {
            }
        }
    }

    @Nested
    @DisplayName("boundary grading")
    inner class Boundaries {

        @Test
        fun `prefers a paragraph break when one is in the back half`() {
            val para1 = "First paragraph with enough text to pass the halfway mark of the window."
            val para2 = "Second paragraph that continues the document with more material here."
            val text = "$para1\n\n$para2\n\n$para1"
            val chunks = TextChunker.chunk(text, targetChars = 100, overlapChars = 10)
            // The first chunk should be exactly the first paragraph — cut at \n\n,
            // not mid-sentence at the 100-char mark.
            assertThat(chunks.first()).isEqualTo(para1)
        }

        @Test
        fun `breaks at sentence ends when no paragraph break exists`() {
            val text = sentenceText(repeats = 30)
            val chunks = TextChunker.chunk(text, targetChars = 120, overlapChars = 20)
            // Every non-final chunk must end with a sentence terminator.
            chunks.dropLast(1).forEach { chunk ->
                assertThat(chunk.last()).isIn(listOf('.', '!', '?'))
            }
        }

        @Test
        fun `does not mistake a decimal point for a sentence end`() {
            // The only dots are inside numbers; the chunker must fall through to
            // space breaks rather than cutting right after "3."
            val text = ("value 3.14159 plus 2.71828 equals roughly 5.85987 and more numbers " +
                "follow forever ").repeat(10)
            val chunks = TextChunker.chunk(text, targetChars = 80, overlapChars = 10)
            chunks.forEach { chunk ->
                assertThat(chunk).doesNotMatch(".*\\d\\.$")
            }
        }

        @Test
        fun `hard-cuts pathological input without separators`() {
            val blob = "A".repeat(2_000)
            val chunks = TextChunker.chunk(blob, targetChars = 500, overlapChars = 50)
            assertThat(chunks).isNotEmpty()
            chunks.forEach { assertThat(it.length).isAtMost(500) }
            // Coverage: overlapping hard cuts must still span the whole blob.
            assertThat(chunks.sumOf { it.length }).isAtLeast(blob.length)
        }
    }

    @Nested
    @DisplayName("bounds")
    inner class Bounds {

        @Test
        fun `honors the max chunk cap`() {
            val huge = "Sentence goes here. ".repeat(5_000)
            val chunks = TextChunker.chunk(huge, targetChars = 100, overlapChars = 20, maxChunks = 25)
            assertThat(chunks).hasSize(25)
        }

        @Test
        fun `overlap carries seam context into the next chunk`() {
            val text = sentenceText(repeats = 40)
            val chunks = TextChunker.chunk(text, targetChars = 150, overlapChars = 50)
            assertThat(chunks.size).isAtLeast(2)
            // The start of each subsequent chunk must re-appear near the end of
            // its predecessor (it was copied across the seam).
            for (i in 1 until chunks.size) {
                val head = chunks[i].take(20)
                assertThat(chunks[i - 1]).contains(head)
            }
        }
    }

    private fun sentenceText(repeats: Int): String =
        (1..repeats).joinToString(" ") { "This is sentence number $it in the document." }
}
