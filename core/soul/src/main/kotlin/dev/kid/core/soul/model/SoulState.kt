package dev.kid.core.soul.model

/** Active Soul personality blend state. */
data class SoulState(
    val dominantTrait: SoulTrait,
    val blendCoefficients: Map<SoulTrait, Float>,
    val detectedSentiment: Sentiment,
    val energyLevel: Float,
)

/** Available personality LoRA traits. */
enum class SoulTrait {
    EMPATHY,
    HUMOR,
    UTILITY,
    TECHNICAL,
    PUNJABI,
    HYPE,
}

/** Detected user sentiment. */
enum class Sentiment {
    HAPPY,
    EXCITED,
    NEUTRAL,
    FRUSTRATED,
    SAD,
    STRESSED,
    CURIOUS,
    IN_FLOW,
}
