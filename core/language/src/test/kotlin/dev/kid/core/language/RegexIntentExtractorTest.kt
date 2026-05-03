package dev.kid.core.language

import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test

class RegexIntentExtractorTest {

    private val extractor = RegexIntentExtractor()

    @Test
    fun `classifies web research intent`() {
        val intent = extractor.extract("Please search web for Kotlin coroutine cancellation guide")

        assertThat(intent.intentType).isEqualTo(IntentType.WEB_RESEARCH)
        assertThat(intent.actionHint).isEqualTo("web_research")
    }

    @Test
    fun `classifies calculator intent from expression`() {
        val intent = extractor.extract("what is 87 * 23?")

        assertThat(intent.intentType).isEqualTo(IntentType.CALCULATOR)
        assertThat(intent.actionHint).isEqualTo("calculator")
    }

    @Test
    fun `extracts file generation format and file name`() {
        val intent = extractor.extract("Create a markdown file notes.md and export as .zip")

        assertThat(intent.intentType).isEqualTo(IntentType.FILE_GENERATION)
        assertThat(intent.entities["file_1"]).isEqualTo("notes.md")
        assertThat(intent.entities["format"]).isEqualTo("zip")
    }

    @Test
    fun `normalizes filler words but preserves meaning`() {
        val intent = extractor.extract("um  actually   please make a concise summary")

        assertThat(intent.cleanedText).isEqualTo("please make a concise summary")
        assertThat(intent.modifiers).contains("concise")
    }

    @Test
    fun `falls back to chat when no tool intent is detected`() {
        val intent = extractor.extract("Tell me something funny about rainy weather")

        assertThat(intent.intentType).isEqualTo(IntentType.CHAT)
        assertThat(intent.actionHint).isNull()
    }
}
