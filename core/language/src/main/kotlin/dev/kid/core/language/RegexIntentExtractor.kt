package dev.kid.core.language

import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class RegexIntentExtractor @Inject constructor() : IntentExtractor {

    override fun extract(rawInput: String): StructuredIntent {
        val cleaned = normalize(rawInput)
        val entities = extractEntities(cleaned)
        val modifiers = extractModifiers(cleaned)

        val (intentType, confidence) = inferIntent(cleaned, entities)
        val actionHint = mapActionHint(intentType)

        return StructuredIntent(
            originalText = rawInput,
            cleanedText = cleaned,
            intentType = intentType,
            entities = entities,
            modifiers = modifiers,
            confidence = confidence,
            actionHint = actionHint,
        )
    }

    private fun normalize(text: String): String {
        if (text.isBlank()) return ""

        val hasCodeSignals = text.contains("```") || text.count { it == '{' || it == '}' } >= 4
        var normalized = text
            .replace(WHITESPACE_REGEX, " ")
            .trim()

        if (!hasCodeSignals) {
            normalized = normalized
                .replace(FILLER_REGEX, " ")
                .replace(WHITESPACE_REGEX, " ")
                .replace(SPACE_BEFORE_PUNCT_REGEX, "$1")
                .trim()
        }

        return normalized
    }

    private fun inferIntent(
        cleaned: String,
        entities: Map<String, String>,
    ): Pair<IntentType, Float> {
        val lower = cleaned.lowercase(Locale.US)
        val hasUrl = entities.keys.any { it.startsWith("url") }
        val hasFile = entities.keys.any { it.startsWith("file") || it == "format" || it.startsWith("path") }

        val wantsFileGeneration =
            FILE_GENERATION_KEYWORDS.any { it in lower } ||
                FORMAT_KEYWORDS.any { it in lower }
        if (wantsFileGeneration) {
            return IntentType.FILE_GENERATION to 0.91f
        }

        if (hasUrl && ("fetch" in lower || "read" in lower || "summarize" in lower || "open" in lower)) {
            return IntentType.WEB_FETCH to 0.92f
        }

        if (WEB_RESEARCH_KEYWORDS.any { it in lower }) {
            return IntentType.WEB_RESEARCH to 0.88f
        }

        if (looksLikeMath(lower)) {
            return IntentType.CALCULATOR to 0.93f
        }

        if (APP_LAUNCH_KEYWORDS.any { it in lower } && APP_HINTS.any { it in lower }) {
            return IntentType.APP_LAUNCH to 0.87f
        }

        if (hasFile || FILESYSTEM_KEYWORDS.any { it in lower }) {
            return IntentType.FILESYSTEM to 0.82f
        }

        return IntentType.CHAT to 0.65f
    }

    private fun extractEntities(cleaned: String): Map<String, String> {
        val out = linkedMapOf<String, String>()

        URL_REGEX.findAll(cleaned)
            .map { it.value.trim() }
            .distinct()
            .take(3)
            .forEachIndexed { index, url ->
                out["url_${index + 1}"] = url
            }

        WINDOWS_PATH_REGEX.findAll(cleaned)
            .map { it.value.trim() }
            .distinct()
            .take(2)
            .forEachIndexed { index, path ->
                out["path_win_${index + 1}"] = path
            }

        UNIX_PATH_REGEX.findAll(cleaned)
            .map { it.value.trim() }
            .distinct()
            .take(2)
            .forEachIndexed { index, path ->
                out["path_unix_${index + 1}"] = path
            }

        FILE_NAME_REGEX.findAll(cleaned)
            .map { it.value.trim() }
            .distinct()
            .take(4)
            .forEachIndexed { index, file ->
                out["file_${index + 1}"] = file
            }

        APP_NAME_REGEX.find(cleaned)?.groupValues?.getOrNull(2)?.let { appName ->
            if (appName.isNotBlank()) {
                out["app"] = appName.trim()
            }
        }

        FORMAT_REGEX.find(cleaned)?.groupValues?.getOrNull(1)?.let { ext ->
            out["format"] = ext.lowercase(Locale.US)
        }

        NUMBER_REGEX.findAll(cleaned)
            .map { it.value }
            .take(2)
            .forEachIndexed { index, num ->
                out["number_${index + 1}"] = num
            }

        return out
    }

    private fun extractModifiers(cleaned: String): List<String> {
        val lower = cleaned.lowercase(Locale.US)
        val tags = linkedSetOf<String>()

        if (URGENT_KEYWORDS.any { it in lower }) tags += "urgent"
        if (PRIVACY_KEYWORDS.any { it in lower }) tags += "private"
        if (SHORT_KEYWORDS.any { it in lower }) tags += "concise"
        if (DETAIL_KEYWORDS.any { it in lower }) tags += "detailed"

        return tags.toList()
    }

    private fun mapActionHint(intentType: IntentType): String? = when (intentType) {
        IntentType.WEB_FETCH -> "web_fetch"
        IntentType.WEB_RESEARCH -> "web_research"
        IntentType.CALCULATOR -> "calculator"
        IntentType.FILESYSTEM -> "filesystem"
        IntentType.FILE_GENERATION -> "filesystem"
        IntentType.APP_LAUNCH -> "app_launch"
        IntentType.CHAT -> null
    }

    private fun looksLikeMath(lower: String): Boolean {
        val hasMathVerb = MATH_KEYWORDS.any { it in lower }
        val hasExpression = MATH_EXPRESSION_REGEX.containsMatchIn(lower)
        return hasMathVerb || hasExpression
    }

    companion object {
        private val WHITESPACE_REGEX = "\\s+".toRegex()
        private val FILLER_REGEX = "\\b(uh+|um+|hmm+|basically|actually|literally|kindly)\\b".toRegex(RegexOption.IGNORE_CASE)
        private val SPACE_BEFORE_PUNCT_REGEX = "\\s+([,.;!?])".toRegex()

        private val URL_REGEX = "https?://[^\\s)]+".toRegex(RegexOption.IGNORE_CASE)
        private val WINDOWS_PATH_REGEX = "[A-Za-z]:\\\\[^\\s\"']+".toRegex()
        private val UNIX_PATH_REGEX = "(?:^|\\s)(/[^\\s\"']+)".toRegex()
        private val FILE_NAME_REGEX = "\\b[\\w.-]+\\.(txt|md|docx|zip|json|csv|pdf|py|kt|java|sh|pptx?)\\b".toRegex(RegexOption.IGNORE_CASE)
        private val FORMAT_REGEX = "\\.(txt|md|docx|zip|json|csv|pdf|py|pptx?)\\b".toRegex(RegexOption.IGNORE_CASE)
        private val APP_NAME_REGEX = "\\b(open|launch)\\s+([a-zA-Z0-9 _.-]+?)(?:\\s+app)?$".toRegex(RegexOption.IGNORE_CASE)
        private val NUMBER_REGEX = "\\b\\d+(?:\\.\\d+)?\\b".toRegex()
        private val MATH_EXPRESSION_REGEX = "\\b\\d+(?:\\s*[+\\-*/]\\s*\\d+)+\\b".toRegex()

        private val FILE_GENERATION_KEYWORDS = listOf(
            "generate file", "create file", "make file", "export", "bundle", "archive", "document",
            "write a markdown", "create a markdown", "make a docx", "make a zip"
        )
        private val FORMAT_KEYWORDS = listOf("docx", "markdown", "zip", "txt", "pdf", "ppt")
        private val WEB_RESEARCH_KEYWORDS = listOf("research", "search web", "find on web", "google", "look up")
        private val FILESYSTEM_KEYWORDS = listOf("read file", "write file", "delete file", "create folder", "save to")
        private val APP_LAUNCH_KEYWORDS = listOf("open", "launch", "start")
        private val APP_HINTS = listOf("app", "youtube", "chrome", "maps", "spotify", "settings", "camera")
        private val MATH_KEYWORDS = listOf("calculate", "compute", "sum", "multiply", "divide", "minus", "plus")

        private val URGENT_KEYWORDS = listOf("urgent", "asap", "right now", "immediately")
        private val PRIVACY_KEYWORDS = listOf("private", "confidential", "secret", "sensitive")
        private val SHORT_KEYWORDS = listOf("short", "brief", "concise")
        private val DETAIL_KEYWORDS = listOf("detailed", "step by step", "comprehensive")
    }
}
