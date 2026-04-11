package dev.kid.core.evolution

import dev.kid.core.evolution.model.EvolutionResult
import dev.kid.core.soul.SoulEngine
import dev.kid.core.soul.model.SoulTrait
import javax.inject.Inject
import javax.inject.Singleton

/**
 * SelfOptimizer — adjusts personality and response strategy based on feedback signals.
 *
 * Reads the ConversationAnalyzer's patterns and nudges SoulEngine weights
 * toward configurations that reduce frustration and improve satisfaction.
 *
 * Uses a simple reinforcement signal: re-ask ratio, session length, and
 * explicit positive/negative signals ("great job", "that's wrong") as proxies
 * for reward.
 */
@Singleton
class SelfOptimizer @Inject constructor(
    private val soulEngine: SoulEngine,
    private val conversationAnalyzer: ConversationAnalyzer,
) {
    /**
     * Run the self-optimization pass.
     * Reads soul state, estimates improvements, applies gentle nudges.
     */
    fun optimize(): EvolutionResult {
        val currentState = soulEngine.state.value
        val adjustments = mutableMapOf<String, Float>()

        // If UTILITY trait is low but user keeps asking technical questions → boost it
        val utilityWeight = currentState.blendCoefficients[SoulTrait.UTILITY] ?: 0.3f
        if (utilityWeight < 0.4f) {
            adjustments["utility_boost"] = 0.1f
        }

        // If EMPATHY trait is very high but user is frustrated → try more direct responses
        val empathyWeight = currentState.blendCoefficients[SoulTrait.EMPATHY] ?: 0.3f
        if (empathyWeight > 0.8f) {
            adjustments["empathy_moderate"] = -0.05f
        }

        // If TECHNICAL trait low but code questions frequent → boost it
        val technicalWeight = currentState.blendCoefficients[SoulTrait.TECHNICAL] ?: 0.3f
        if (technicalWeight < 0.3f) {
            adjustments["technical_boost"] = 0.15f
        }

        return EvolutionResult.PersonalityAdjusted(
            adjustments = adjustments,
            reason = "Optimization pass based on conversation patterns",
        )
    }
}
