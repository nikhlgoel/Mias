package dev.kid.core.language

/**
 * Turns user free-form input into normalized structured intent.
 *
 * This improves context quality before model inference and enables
 * deterministic routing hints for tools and memory search.
 */
interface IntentExtractor {
    fun extract(rawInput: String): StructuredIntent
}

enum class IntentType(val value: String) {
    CHAT("chat"),
    WEB_FETCH("web_fetch"),
    WEB_RESEARCH("web_research"),
    FILESYSTEM("filesystem"),
    FILE_GENERATION("file_generation"),
    CALCULATOR("calculator"),
    APP_LAUNCH("app_launch"),
}

data class StructuredIntent(
    val originalText: String,
    val cleanedText: String,
    val intentType: IntentType,
    val entities: Map<String, String>,
    val modifiers: List<String>,
    val confidence: Float,
    val actionHint: String? = null,
)
