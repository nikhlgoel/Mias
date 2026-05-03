package dev.kid.core.evolution

import dev.kid.core.data.Conversation
import dev.kid.core.data.Role
import dev.kid.core.evolution.model.BehaviorPattern
import dev.kid.core.evolution.model.EvolutionResult
import dev.kid.core.evolution.model.PatternCategory
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Analyzes conversation history to extract behavioral patterns.
 *
 * Looks for:
 * - Recurring topics the user asks about
 * - Emotional arc patterns (frustration → resolution)
 * - Response quality signals (short answers → user re-asks = bad)
 * - Time-of-day task patterns
 */
@Singleton
class ConversationAnalyzer @Inject constructor() {

    fun analyze(conversations: List<Conversation>): EvolutionResult {
        val allUserMessages = conversations
            .flatMap { conv -> conv.messages.filter { it.role == Role.USER } }
            .map { it.content.lowercase() }

        val patterns = mutableListOf<BehaviorPattern>()

        // ── Topic Affinity Detection ──────────────────────────────────────
        val topicMap = mutableMapOf<String, Int>()
        TOPIC_KEYWORDS.forEach { (topic, keywords) ->
            val count = allUserMessages.count { msg ->
                keywords.any { kw -> msg.contains(kw) }
            }
            if (count >= TOPIC_FREQUENCY_THRESHOLD) {
                topicMap[topic] = count
            }
        }

        topicMap.forEach { (topic, count) ->
            patterns.add(
                BehaviorPattern(
                    id = UUID.randomUUID().toString(),
                    description = "User frequently asks about $topic ($count times)",
                    frequency = count,
                    confidence = (count.toFloat() / allUserMessages.size).coerceAtMost(1f),
                    category = PatternCategory.TOPIC_AFFINITY,
                    firstObserved = System.currentTimeMillis() - (count * 3_600_000L),
                    lastObserved = System.currentTimeMillis(),
                ),
            )
        }

        // ── Code Task Detection ────────────────────────────────────────────
        val codeMessages = allUserMessages.count { msg ->
            CODE_SIGNALS.any { msg.contains(it) }
        }
        if (codeMessages > 0) {
            patterns.add(
                BehaviorPattern(
                    id = UUID.randomUUID().toString(),
                    description = "User is a developer — $codeMessages code-related queries",
                    frequency = codeMessages,
                    confidence = (codeMessages.toFloat() / allUserMessages.size.coerceAtLeast(1)),
                    category = PatternCategory.TASK_TYPE,
                    firstObserved = System.currentTimeMillis(),
                    lastObserved = System.currentTimeMillis(),
                ),
            )
        }

        // ── Frustration Pattern ────────────────────────────────────────────
        val frustrationSignals = allUserMessages.count { msg ->
            FRUSTRATION_SIGNALS.any { msg.contains(it) }
        }
        if (frustrationSignals >= 2) {
            patterns.add(
                BehaviorPattern(
                    id = UUID.randomUUID().toString(),
                    description = "User shows frustration $frustrationSignals times — improve directness",
                    frequency = frustrationSignals,
                    confidence = 0.7f,
                    category = PatternCategory.EMOTIONAL_STATE,
                    firstObserved = System.currentTimeMillis(),
                    lastObserved = System.currentTimeMillis(),
                ),
            )
        }

        // ── Short Answer Preference ────────────────────────────────────────
        val reaskCount = detectReasks(conversations)
        if (reaskCount >= 2) {
            patterns.add(
                BehaviorPattern(
                    id = UUID.randomUUID().toString(),
                    description = "User re-asks $reaskCount times — responses may be too long or unclear",
                    frequency = reaskCount,
                    confidence = 0.65f,
                    category = PatternCategory.RESPONSE_PREFERENCE,
                    firstObserved = System.currentTimeMillis(),
                    lastObserved = System.currentTimeMillis(),
                ),
            )
        }

        return EvolutionResult.PatternsFound(patterns)
    }

    /**
     * Detect if user re-asks the same question shortly after a response.
     * Heuristic: two user messages within the same conversation with
     * 65% n-gram overlap = likely re-ask.
     */
    private fun detectReasks(conversations: List<Conversation>): Int {
        var reaskCount = 0
        for (conv in conversations) {
            val userMsgs = conv.messages.filter { it.role == Role.USER }
            for (i in 1 until userMsgs.size) {
                val prev = userMsgs[i - 1].content.lowercase().split(" ").toSet()
                val curr = userMsgs[i].content.lowercase().split(" ").toSet()
                val overlap = (prev intersect curr).size.toFloat() / prev.size.coerceAtLeast(1)
                if (overlap > 0.65f) reaskCount++
            }
        }
        return reaskCount
    }

    companion object {
        private const val TOPIC_FREQUENCY_THRESHOLD = 3

        private val TOPIC_KEYWORDS = mapOf(
            "programming" to listOf("code", "function", "bug", "error", "class", "kotlin", "python", "java", "api"),
            "productivity" to listOf("todo", "task", "schedule", "remind", "deadline", "meeting", "plan"),
            "research" to listOf("explain", "what is", "how does", "why does", "define", "tell me about"),
            "creativity" to listOf("write", "story", "poem", "imagine", "create", "design", "idea"),
            "personal" to listOf("i feel", "stressed", "tired", "happy", "sad", "worried", "my life"),
        )

        private val CODE_SIGNALS = listOf(
            "function", "class", "variable", "debug", "compile", "error", "null",
            "kotlin", "java", "python", "android", "gradle", "git", "api", "json",
        )

        private val FRUSTRATION_SIGNALS = listOf(
            "wrong", "no that's not", "you didn't", "that's incorrect", "not what i asked",
            "again", "still not", "why can't you", "ugh", "frustrat",
        )
    }
}
