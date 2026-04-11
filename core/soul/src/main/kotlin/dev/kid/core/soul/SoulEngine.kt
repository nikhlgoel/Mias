package dev.kid.core.soul

import dev.kid.core.soul.model.Sentiment
import dev.kid.core.soul.model.SoulState
import dev.kid.core.soul.model.SoulTrait
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Soul Engine — personality evolution through dynamic LoRA blending.
 *
 * Before each inference call, the Soul Engine:
 * 1. Analyzes recent user messages for sentiment
 * 2. Computes LoRA blend coefficients via the policy network
 * 3. Generates a system prompt modifier reflecting the personality blend
 *
 * The model generates text that feels like a person, not a tool.
 */
@Singleton
class SoulEngine @Inject constructor(
    private val sentimentAnalyzer: SentimentAnalyzer,
    private val blendPolicy: LoraBlendPolicy,
) {
    private val _state = MutableStateFlow(defaultState())
    val state: StateFlow<SoulState> = _state.asStateFlow()

    private val recentMessages = mutableListOf<String>()

    /** Process a new user message and update soul state. */
    fun processUserMessage(message: String, isCodeTask: Boolean = false) {
        recentMessages.add(message)
        if (recentMessages.size > MAX_RECENT_MESSAGES) {
            recentMessages.removeFirst()
        }

        val sentiment = sentimentAnalyzer.analyzeTrend(recentMessages)
        val blend = blendPolicy.computeBlend(
            sentiment = sentiment,
            isCodeTask = isCodeTask,
            recentMessageCount = recentMessages.size,
        )

        val dominantTrait = blend.maxByOrNull { it.value }?.key ?: SoulTrait.UTILITY

        _state.value = SoulState(
            dominantTrait = dominantTrait,
            blendCoefficients = blend,
            detectedSentiment = sentiment,
            energyLevel = computeEnergy(blend),
        )
    }

    /** Generate a personality-adjusted system prompt modifier. */
    fun getPersonalityPrompt(): String {
        val soul = _state.value
        return buildString {
            appendLine("Personality calibration:")
            when (soul.detectedSentiment) {
                Sentiment.FRUSTRATED -> {
                    appendLine("- User seems frustrated. Acknowledge their frustration briefly, then pivot to solutions.")
                    appendLine("- Be direct and helpful. Skip the jokes.")
                }
                Sentiment.SAD -> {
                    appendLine("- User seems down. Be warm and supportive.")
                    appendLine("- Reference shared memories if relevant.")
                    appendLine("- Gentle tone, no forced positivity.")
                }
                Sentiment.EXCITED -> {
                    appendLine("- User is excited! Match their energy.")
                    appendLine("- Be enthusiastic but don't overshoot.")
                    appendLine("- Punjabi energy phrases welcome.")
                }
                Sentiment.STRESSED -> {
                    appendLine("- User is under pressure. Be calm and efficient.")
                    appendLine("- Prioritize actionable help over conversation.")
                    appendLine("- Briefly acknowledge the stress, then get to work.")
                }
                Sentiment.IN_FLOW -> {
                    appendLine("- User is in flow state. Be minimal.")
                    appendLine("- Short, direct answers. No filler.")
                    appendLine("- Don't interrupt their momentum.")
                }
                Sentiment.CURIOUS -> {
                    appendLine("- User is curious and exploring. Explain clearly.")
                    appendLine("- Go deeper if they keep asking. Use analogies.")
                }
                else -> {
                    appendLine("- Normal conversational mode.")
                    appendLine("- Mix warmth with utility naturally.")
                }
            }

            if (soul.blendCoefficients.getOrDefault(SoulTrait.PUNJABI, 0f) > 0.3f) {
                appendLine("- Mix in Punjabi naturally where it feels right.")
            }
        }
    }

    /** Get current sentiment for UI display. */
    fun currentSentiment(): Sentiment = _state.value.detectedSentiment

    private fun computeEnergy(blend: Map<SoulTrait, Float>): Float {
        val hype = blend[SoulTrait.HYPE] ?: 0f
        val humor = blend[SoulTrait.HUMOR] ?: 0f
        val empathy = blend[SoulTrait.EMPATHY] ?: 0f
        return ((hype * 0.4f + humor * 0.3f + empathy * 0.3f) * 100).coerceIn(0f, 100f)
    }

    private fun defaultState() = SoulState(
        dominantTrait = SoulTrait.UTILITY,
        blendCoefficients = SoulTrait.entries.associateWith { 0.3f },
        detectedSentiment = Sentiment.NEUTRAL,
        energyLevel = 50f,
    )

    companion object {
        private const val MAX_RECENT_MESSAGES = 5
    }
}
