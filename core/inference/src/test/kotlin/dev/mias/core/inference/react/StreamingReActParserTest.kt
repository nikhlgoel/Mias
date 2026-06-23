package dev.mias.core.inference.react

import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

/**
 * The parser is the user-facing no-JSON-leak guarantee: whatever the model
 * streams, the bubble must show either clean prose or the should_say value —
 * never raw structure.
 */
@DisplayName("StreamingReActParser")
class StreamingReActParserTest {

    @Nested
    @DisplayName("plain replies")
    inner class Plain {

        @Test
        fun `prose streams straight through`() {
            val state = StreamingReActParser.parse("Hello! How can I help you today?")
            assertThat(state.visible).isEqualTo("Hello! How can I help you today?")
            assertThat(state.thinking).isEmpty()
        }

        @Test
        fun `prose mentioning unquoted marker words still streams`() {
            // Markers are matched in their quoted-JSON form only, so a reply that
            // merely talks about tools isn't mistaken for JSON and hidden.
            val text = "The action_input field is where tools receive arguments."
            assertThat(StreamingReActParser.parse(text).visible).isEqualTo(text)
        }
    }

    @Nested
    @DisplayName("JSON streams")
    inner class JsonStreams {

        @Test
        fun `forming json shows nothing until should_say lands`() {
            val partial = """{"thought":"I need to check the weather","action":"web_search"""
            val state = StreamingReActParser.parse(partial)
            assertThat(state.visible).isEmpty()
            assertThat(state.thinking).isEqualTo("I need to check the weather")
        }

        @Test
        fun `complete json surfaces only the should_say value`() {
            val full = """{"thought":"simple greeting","action":"respond_user",""" +
                """"action_input":{},"is_final":true,"should_say":"Hi there!"}"""
            val state = StreamingReActParser.parse(full)
            assertThat(state.visible).isEqualTo("Hi there!")
            assertThat(state.thinking).isEqualTo("simple greeting")
        }

        @Test
        fun `partial should_say streams live as it grows`() {
            val partial = """{"thought":"x","action":"respond_user","action_input":{},""" +
                """"is_final":true,"should_say":"Hello wor"""
            assertThat(StreamingReActParser.parse(partial).visible).isEqualTo("Hello wor")
        }

        @Test
        fun `json escapes are decoded for display`() {
            val full = """{"thought":"t","should_say":"Line one\nShe said \"hi\""}"""
            assertThat(StreamingReActParser.parse(full).visible)
                .isEqualTo("Line one\nShe said \"hi\"")
        }

        @Test
        fun `quoted structural markers hide the buffer even without a leading brace`() {
            // Residue like `,"is_final":false}` after a parse failure must never
            // be shown as the reply body.
            val residue = ""","is_final":false,"action_input":{}}"""
            assertThat(StreamingReActParser.parse(residue).visible).isEmpty()
        }

        @Test
        fun `leading whitespace before the brace still counts as json`() {
            val partial = "  \n {\"thought\":\"deciding"
            val state = StreamingReActParser.parse(partial)
            assertThat(state.visible).isEmpty()
            assertThat(state.thinking).isEqualTo("deciding")
        }
    }
}
