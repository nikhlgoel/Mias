package dev.kid.core.soul

import dev.kid.core.soul.model.Sentiment
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Lightweight sentiment classifier.
 *
 * Analyzes user text for emotional valence. In production this would be
 * a ~5MB ONNX model. For now, uses keyword heuristics as a baseline
 * that is augmented when the full ONNX model is loaded.
 */
@Singleton
class SentimentAnalyzer @Inject constructor() {

    fun analyze(text: String): Sentiment {
        val lower = text.lowercase()

        // Frustration / stress signals
        if (containsAny(lower, FRUSTRATED_KEYWORDS)) return Sentiment.FRUSTRATED
        if (containsAny(lower, STRESSED_KEYWORDS)) return Sentiment.STRESSED
        if (containsAny(lower, SAD_KEYWORDS)) return Sentiment.SAD

        // Positive signals
        if (containsAny(lower, EXCITED_KEYWORDS)) return Sentiment.EXCITED
        if (containsAny(lower, HAPPY_KEYWORDS)) return Sentiment.HAPPY

        // Curiosity
        if (lower.contains("?") || containsAny(lower, CURIOUS_KEYWORDS)) {
            return Sentiment.CURIOUS
        }

        // Flow state (short, direct commands — user is in the zone)
        if (text.length < 30 && !text.contains("?")) return Sentiment.IN_FLOW

        return Sentiment.NEUTRAL
    }

    /** Analyze sentiment trend from last N messages. */
    fun analyzeTrend(messages: List<String>): Sentiment {
        if (messages.isEmpty()) return Sentiment.NEUTRAL
        val sentiments = messages.map { analyze(it) }
        // Return the most frequent sentiment
        return sentiments.groupingBy { it }.eachCount().maxByOrNull { it.value }?.key
            ?: Sentiment.NEUTRAL
    }

    private fun containsAny(text: String, keywords: List<String>): Boolean =
        keywords.any { text.contains(it) }

    companion object {
        private val FRUSTRATED_KEYWORDS = listOf(
            "not working", "broken", "wtf", "damn", "ugh", "kaam nhi",
            "fix this", "error", "crash", "fail", "hate", "pagal",
        )
        private val STRESSED_KEYWORDS = listOf(
            "deadline", "urgent", "asap", "hurry", "tension", "pressure",
            "exam", "jaldi", "time nhi", "stress",
        )
        private val SAD_KEYWORDS = listOf(
            "sad", "lonely", "miss", "upset", "depressed", "udaas", "dukhi",
        )
        private val EXCITED_KEYWORDS = listOf(
            "amazing", "awesome", "let's go", "hell yeah", "balle balle",
            "great", "perfect", "vadiya", "sahi", "shandar",
        )
        private val HAPPY_KEYWORDS = listOf(
            "thanks", "love", "nice", "good", "happy", "khush", "dhanyavaad",
        )
        private val CURIOUS_KEYWORDS = listOf(
            "how", "why", "what", "explain", "tell me", "dasss", "ki",
        )
    }
}
