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

    @Test
    fun `classifies app launch intent for known app`() {
        val intent = extractor.extract("open Spotify")

        assertThat(intent.intentType).isEqualTo(IntentType.APP_LAUNCH)
        assertThat(intent.actionHint).isEqualTo("app_launch")
        assertThat(intent.entities["app"]).isEqualTo("Spotify")
    }

    @Test
    fun `classifies app launch with launch keyword`() {
        val intent = extractor.extract("launch YouTube app")

        assertThat(intent.intentType).isEqualTo(IntentType.APP_LAUNCH)
        assertThat(intent.entities["app"]).isNotNull()
    }

    @Test
    fun `classifies calculator intent from keyword`() {
        val intent = extractor.extract("calculate 500 divided by 25")

        assertThat(intent.intentType).isEqualTo(IntentType.CALCULATOR)
        assertThat(intent.actionHint).isEqualTo("calculator")
    }

    @Test
    fun `classifies filesystem intent from read file keyword`() {
        val intent = extractor.extract("read file notes.txt from downloads")

        assertThat(intent.intentType).isEqualTo(IntentType.FILESYSTEM)
        assertThat(intent.actionHint).isEqualTo("filesystem")
    }

    @Test
    fun `extracts URL entity from web fetch request`() {
        val intent = extractor.extract("fetch https://example.com/api/data")

        assertThat(intent.intentType).isEqualTo(IntentType.WEB_FETCH)
        assertThat(intent.entities["url_1"]).isEqualTo("https://example.com/api/data")
    }

    @Test
    fun `detects urgent modifier`() {
        val intent = extractor.extract("I need this done right now urgently")

        assertThat(intent.modifiers).contains("urgent")
    }

    @Test
    fun `detects privacy modifier`() {
        val intent = extractor.extract("this is confidential information about my salary")

        assertThat(intent.modifiers).contains("private")
    }

    @Test
    fun `empty input returns chat intent`() {
        val intent = extractor.extract("")

        assertThat(intent.intentType).isEqualTo(IntentType.CHAT)
        assertThat(intent.cleanedText).isEmpty()
    }
}
