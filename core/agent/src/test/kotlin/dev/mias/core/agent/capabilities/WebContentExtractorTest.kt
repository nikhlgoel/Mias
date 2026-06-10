package dev.mias.core.agent.capabilities

import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

@DisplayName("WebContentExtractor")
class WebContentExtractorTest {

    @Nested
    @DisplayName("extractReadableText")
    inner class ReadableText {

        @Test
        fun `strips scripts, styles, nav and tags`() {
            val html = """
                <html><head><style>.x{color:red}</style></head>
                <body>
                  <nav>menu home about</nav>
                  <script>var x = 1; alert('hi')</script>
                  <h1>Title</h1>
                  <p>First paragraph.</p>
                  <p>Second paragraph.</p>
                  <footer>copyright</footer>
                </body></html>
            """.trimIndent()

            val text = WebContentExtractor.extractReadableText(html)

            assertThat(text).contains("Title")
            assertThat(text).contains("First paragraph.")
            assertThat(text).contains("Second paragraph.")
            assertThat(text).doesNotContain("alert")
            assertThat(text).doesNotContain("menu home about")
            assertThat(text).doesNotContain("copyright")
            assertThat(text).doesNotContain("<")
        }

        @Test
        fun `decodes html entities`() {
            val text = WebContentExtractor.extractReadableText("<p>Tom &amp; Jerry &lt;3</p>")
            assertThat(text).isEqualTo("Tom & Jerry <3")
        }
    }

    @Nested
    @DisplayName("extractLeadImageUrl")
    inner class LeadImage {

        private val page = "https://example.com/news/story"

        @Test
        fun `prefers absolute og image`() {
            val html =
                """<meta property="og:image" content="https://cdn.example.com/hero.jpg">"""
            assertThat(WebContentExtractor.extractLeadImageUrl(html, page))
                .isEqualTo("https://cdn.example.com/hero.jpg")
        }

        @Test
        fun `resolves a relative og image against the page`() {
            val html = """<meta property="og:image" content="/img/hero.jpg">"""
            assertThat(WebContentExtractor.extractLeadImageUrl(html, page))
                .isEqualTo("https://example.com/img/hero.jpg")
        }

        @Test
        fun `falls back to twitter image`() {
            val html = """<meta name="twitter:image" content="https://example.com/t.jpg">"""
            assertThat(WebContentExtractor.extractLeadImageUrl(html, page))
                .isEqualTo("https://example.com/t.jpg")
        }

        @Test
        fun `falls back to first content image`() {
            val html = """<article><img src="https://example.com/photo.png"></article>"""
            assertThat(WebContentExtractor.extractLeadImageUrl(html, page))
                .isEqualTo("https://example.com/photo.png")
        }

        @Test
        fun `skips logos, icons, svg and data uris`() {
            assertThat(
                WebContentExtractor.extractLeadImageUrl(
                    """<meta property="og:image" content="https://example.com/logo.png">""",
                    page,
                ),
            ).isNull()
            assertThat(
                WebContentExtractor.extractLeadImageUrl(
                    """<img src="data:image/png;base64,AAAA">""",
                    page,
                ),
            ).isNull()
            assertThat(
                WebContentExtractor.extractLeadImageUrl(
                    """<img src="https://example.com/diagram.svg">""",
                    page,
                ),
            ).isNull()
        }

        @Test
        fun `returns null when there is no image`() {
            assertThat(WebContentExtractor.extractLeadImageUrl("<p>no images here</p>", page))
                .isNull()
        }
    }
}
