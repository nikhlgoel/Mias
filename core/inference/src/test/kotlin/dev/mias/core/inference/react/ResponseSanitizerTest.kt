package dev.mias.core.inference.react

import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

@DisplayName("ResponseSanitizer")
class ResponseSanitizerTest {

    @Nested
    @DisplayName("stripMetaReasoning")
    inner class StripMetaReasoning {

        @Test
        fun `drops a leading tool-intent sentence`() {
            val input = "I will use web_search to find the news. The capital of France is Paris."
            assertThat(ResponseSanitizer.stripMetaReasoning(input))
                .isEqualTo("The capital of France is Paris.")
        }

        @Test
        fun `drops third-person 'the user is asking' preamble`() {
            val input = "The user is asking for a summary. Here is the summary: it works."
            assertThat(ResponseSanitizer.stripMetaReasoning(input))
                .isEqualTo("Here is the summary: it works.")
        }

        @Test
        fun `keeps the original when it is entirely meta`() {
            // Nothing substantive remains → don't blank the bubble.
            val input = "I am a large language model. I have access to the tools listed above."
            assertThat(ResponseSanitizer.stripMetaReasoning(input)).isEqualTo(input)
        }

        @Test
        fun `does not touch a normal answer`() {
            val input = "I think Paris is a wonderful city to visit in spring."
            assertThat(ResponseSanitizer.stripMetaReasoning(input)).isEqualTo(input)
        }
    }

    @Nested
    @DisplayName("sanitize")
    inner class Sanitize {

        @Test
        fun `plain prose with leaked reasoning is cleaned`() {
            val raw = "I need to search for this. Actually, the answer is 42."
            assertThat(ResponseSanitizer.sanitize(raw).chatText)
                .isEqualTo("Actually, the answer is 42.")
        }

        @Test
        fun `json should_say is extracted`() {
            val raw = """{"thought":"x","action":"respond_user","is_final":true,"should_say":"Hello there"}"""
            assertThat(ResponseSanitizer.sanitize(raw).chatText).isEqualTo("Hello there")
        }
    }
}
