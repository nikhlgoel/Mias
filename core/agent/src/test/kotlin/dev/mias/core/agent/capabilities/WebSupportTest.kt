package dev.mias.core.agent.capabilities

import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

@DisplayName("WebSupport")
class WebSupportTest {

    @Nested
    @DisplayName("isFetchableUrl")
    inner class Fetchable {

        @Test
        fun `accepts normal http and https urls`() {
            assertThat(WebSupport.isFetchableUrl("https://example.com/page")).isTrue()
            assertThat(WebSupport.isFetchableUrl("http://news.site.org/a?b=1")).isTrue()
            assertThat(WebSupport.isFetchableUrl("  https://example.com  ")).isTrue()
        }

        @Test
        fun `rejects non-http schemes and artifacts`() {
            assertThat(WebSupport.isFetchableUrl("javascript:alert(1)")).isFalse()
            assertThat(WebSupport.isFetchableUrl("data:text/html;base64,AAAA")).isFalse()
            assertThat(WebSupport.isFetchableUrl("ftp://example.com/file")).isFalse()
            assertThat(WebSupport.isFetchableUrl("/relative/path")).isFalse()
            assertThat(WebSupport.isFetchableUrl("")).isFalse()
        }

        @Test
        fun `rejects loopback addresses`() {
            assertThat(WebSupport.isFetchableUrl("http://localhost/admin")).isFalse()
            assertThat(WebSupport.isFetchableUrl("http://127.0.0.1:8080/x")).isFalse()
        }
    }

    @Nested
    @DisplayName("isSearchArtifact")
    inner class Artifact {

        @Test
        fun `flags duckduckgo ad and tracking urls`() {
            assertThat(WebSupport.isSearchArtifact("https://duckduckgo.com/y.js?ad_provider=x")).isTrue()
            assertThat(WebSupport.isSearchArtifact("https://html.duckduckgo.com/html/?q=x")).isTrue()
        }

        @Test
        fun `passes real destinations`() {
            assertThat(WebSupport.isSearchArtifact("https://www.reuters.com/technology/")).isFalse()
        }

        @Test
        fun `treats unparseable urls as artifacts`() {
            assertThat(WebSupport.isSearchArtifact("not a url")).isTrue()
        }
    }

    @Nested
    @DisplayName("host")
    inner class Host {

        @Test
        fun `extracts and normalizes the host`() {
            assertThat(WebSupport.host("https://WWW.Example.COM/page")).isEqualTo("example.com")
            assertThat(WebSupport.host("https://sub.example.com/x")).isEqualTo("sub.example.com")
        }

        @Test
        fun `returns null for garbage`() {
            assertThat(WebSupport.host("::::")).isNull()
            assertThat(WebSupport.host("")).isNull()
        }
    }

    @Nested
    @DisplayName("selectDiverse")
    inner class Diverse {

        private fun hit(url: String, title: String = url) = SearchHit(title, url, "")

        @Test
        fun `prefers one hit per host in rank order`() {
            val hits = listOf(
                hit("https://a.com/1"),
                hit("https://a.com/2"),
                hit("https://b.com/1"),
                hit("https://c.com/1"),
            )
            val picked = WebSupport.selectDiverse(hits, 3)
            assertThat(picked.map { it.url }).containsExactly(
                "https://a.com/1", "https://b.com/1", "https://c.com/1",
            ).inOrder()
        }

        @Test
        fun `fills remaining slots from duplicate hosts`() {
            val hits = listOf(
                hit("https://a.com/1"),
                hit("https://a.com/2"),
                hit("https://b.com/1"),
            )
            val picked = WebSupport.selectDiverse(hits, 3)
            assertThat(picked.map { it.url }).containsExactly(
                "https://a.com/1", "https://b.com/1", "https://a.com/2",
            ).inOrder()
        }

        @Test
        fun `dedupes identical urls`() {
            val hits = listOf(hit("https://a.com/1"), hit("https://a.com/1"))
            assertThat(WebSupport.selectDiverse(hits, 5)).hasSize(1)
        }

        @Test
        fun `handles zero and oversize maxima`() {
            val hits = listOf(hit("https://a.com/1"))
            assertThat(WebSupport.selectDiverse(hits, 0)).isEmpty()
            assertThat(WebSupport.selectDiverse(hits, 10)).hasSize(1)
            assertThat(WebSupport.selectDiverse(emptyList(), 3)).isEmpty()
        }
    }

    @Nested
    @DisplayName("stableHash")
    inner class Hashing {

        @Test
        fun `is deterministic, filesystem-safe, and collision-resistant for distinct input`() {
            val a = WebSupport.stableHash("https://example.com/image.jpg")
            val b = WebSupport.stableHash("https://example.com/image.jpg")
            val c = WebSupport.stableHash("https://example.com/other.jpg")
            assertThat(a).isEqualTo(b)
            assertThat(a).isNotEqualTo(c)
            assertThat(a).matches("[0-9a-f]{16}")
        }
    }
}
