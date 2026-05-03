package dev.kid.core.soul

import dev.kid.core.soul.model.Sentiment
import dev.kid.core.soul.model.SoulTrait
import java.util.Calendar
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Computes LoRA blend coefficients based on sentiment, time-of-day,
 * and task context.
 *
 * W_soul = W_base + Σ(αᵢ × ΔWᵢ)
 * Where αᵢ = blend coefficient for each personality trait LoRA adapter.
 */
@Singleton
class LoraBlendPolicy @Inject constructor() {

    /** Compute blend weights for a given sentiment and context. */
    fun computeBlend(
        sentiment: Sentiment,
        isCodeTask: Boolean = false,
        recentMessageCount: Int = 0,
    ): Map<SoulTrait, Float> {
        val base = sentimentBaseBlend(sentiment)
        val timeAdjusted = applyTimeOfDay(base)
        val taskAdjusted = if (isCodeTask) applyCodeContext(timeAdjusted) else timeAdjusted

        // Normalize so no single weight exceeds 1.0
        return taskAdjusted.mapValues { (_, v) -> v.coerceIn(0f, 1f) }
    }

    private fun sentimentBaseBlend(sentiment: Sentiment): Map<SoulTrait, Float> =
        when (sentiment) {
            Sentiment.HAPPY -> mapOf(
                SoulTrait.EMPATHY to 0.4f,
                SoulTrait.HUMOR to 0.6f,
                SoulTrait.UTILITY to 0.4f,
                SoulTrait.TECHNICAL to 0.2f,
                SoulTrait.PUNJABI to 0.5f,
                SoulTrait.HYPE to 0.3f,
            )
            Sentiment.EXCITED -> mapOf(
                SoulTrait.EMPATHY to 0.3f,
                SoulTrait.HUMOR to 0.5f,
                SoulTrait.UTILITY to 0.3f,
                SoulTrait.TECHNICAL to 0.2f,
                SoulTrait.PUNJABI to 0.6f,
                SoulTrait.HYPE to 0.8f,
            )
            Sentiment.FRUSTRATED -> mapOf(
                SoulTrait.EMPATHY to 0.8f,
                SoulTrait.HUMOR to 0.05f,
                SoulTrait.UTILITY to 0.7f,
                SoulTrait.TECHNICAL to 0.5f,
                SoulTrait.PUNJABI to 0.3f,
                SoulTrait.HYPE to 0.0f,
            )
            Sentiment.SAD -> mapOf(
                SoulTrait.EMPATHY to 0.9f,
                SoulTrait.HUMOR to 0.1f,
                SoulTrait.UTILITY to 0.3f,
                SoulTrait.TECHNICAL to 0.1f,
                SoulTrait.PUNJABI to 0.4f,
                SoulTrait.HYPE to 0.0f,
            )
            Sentiment.STRESSED -> mapOf(
                SoulTrait.EMPATHY to 0.8f,
                SoulTrait.HUMOR to 0.0f,
                SoulTrait.UTILITY to 0.8f,
                SoulTrait.TECHNICAL to 0.4f,
                SoulTrait.PUNJABI to 0.2f,
                SoulTrait.HYPE to 0.0f,
            )
            Sentiment.CURIOUS -> mapOf(
                SoulTrait.EMPATHY to 0.3f,
                SoulTrait.HUMOR to 0.3f,
                SoulTrait.UTILITY to 0.5f,
                SoulTrait.TECHNICAL to 0.7f,
                SoulTrait.PUNJABI to 0.3f,
                SoulTrait.HYPE to 0.2f,
            )
            Sentiment.IN_FLOW -> mapOf(
                SoulTrait.EMPATHY to 0.1f,
                SoulTrait.HUMOR to 0.0f,
                SoulTrait.UTILITY to 0.9f,
                SoulTrait.TECHNICAL to 0.6f,
                SoulTrait.PUNJABI to 0.1f,
                SoulTrait.HYPE to 0.0f,
            )
            Sentiment.NEUTRAL -> mapOf(
                SoulTrait.EMPATHY to 0.4f,
                SoulTrait.HUMOR to 0.3f,
                SoulTrait.UTILITY to 0.5f,
                SoulTrait.TECHNICAL to 0.3f,
                SoulTrait.PUNJABI to 0.4f,
                SoulTrait.HYPE to 0.1f,
            )
        }

    /** Late night shifts toward more empathy, less hype. */
    private fun applyTimeOfDay(blend: Map<SoulTrait, Float>): Map<SoulTrait, Float> {
        val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        val mutable = blend.toMutableMap()

        when {
            hour in 0..5 || hour >= 23 -> {
                // Late night — softer, more empathetic
                mutable[SoulTrait.EMPATHY] = (mutable[SoulTrait.EMPATHY] ?: 0f) + 0.2f
                mutable[SoulTrait.HYPE] = 0f
                mutable[SoulTrait.HUMOR] = (mutable[SoulTrait.HUMOR] ?: 0f) * 0.5f
            }
            hour in 6..9 -> {
                // Morning — energetic, encouraging
                mutable[SoulTrait.HYPE] = (mutable[SoulTrait.HYPE] ?: 0f) + 0.15f
            }
        }

        return mutable
    }

    /** Code tasks shift toward utility + technical. */
    private fun applyCodeContext(blend: Map<SoulTrait, Float>): Map<SoulTrait, Float> {
        val mutable = blend.toMutableMap()
        mutable[SoulTrait.UTILITY] = (mutable[SoulTrait.UTILITY] ?: 0f) + 0.2f
        mutable[SoulTrait.TECHNICAL] = (mutable[SoulTrait.TECHNICAL] ?: 0f) + 0.3f
        mutable[SoulTrait.HUMOR] = (mutable[SoulTrait.HUMOR] ?: 0f) * 0.3f
        mutable[SoulTrait.HYPE] = 0f
        return mutable
    }
}
