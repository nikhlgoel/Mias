package dev.mias.core.modelhub.model

/**
 * Derived, execution-relevant capabilities of a model — distilled from the
 * static [ModelCard] metadata into the few signals the inference layer needs
 * to decide *how* to run it.
 *
 * This is the model half of the agentic/deterministic decision (the device
 * half lives in `core:inference`'s `DeviceTier`). Keeping the parsing here,
 * next to [ModelCard], means the rules live with the data they read.
 */
data class ModelCapabilityProfile(
    /** Parameter count in billions, parsed from [ModelCard.parameterCount]. */
    val paramsB: Float,
    /** Trained context window, from [ModelCard.contextLength]. */
    val contextWindow: Int,
    /**
     * Whether this model is competent enough to *drive* the ReAct tool loop
     * (decide and emit tool-call JSON itself). Below the floor, the model is
     * only ever fed pre-fetched results — it never self-invokes tools.
     */
    val supportsToolCalls: Boolean,
    /** True multimodal vision (a MediaPipe `.task` bundle), not a text GGUF. */
    val supportsVision: Boolean,
    /** Suggested per-turn generation cap — larger for models that can use it. */
    val recommendedMaxTokens: Int,
) {
    companion object {
        /**
         * Minimum parameter count (billions) for the agentic tool loop.
         *
         * Reliable JSON tool-calling on-device starts around this size; below
         * it, models hallucinate tool calls and leak reasoning (the deterministic
         * path is used instead). Tunable — the single knob that moves the line.
         */
        const val AGENTIC_MIN_PARAMS_B: Float = 2.0f

        internal const val SMALL_MODEL_MAX_TOKENS: Int = 512
        internal const val LARGE_MODEL_MAX_TOKENS: Int = 1024
    }
}

/** Build the [ModelCapabilityProfile] for this card. */
fun ModelCard.capabilityProfile(): ModelCapabilityProfile {
    val params = parseParameterCount(parameterCount)
    val isVision = ModelRole.VISION in roles && runtime == ModelRuntime.GOOGLE_AI_EDGE
    // NPU-accelerated Gemma punches above its raw size for structured output,
    // so an NPU 2B+ counts as tool-capable; otherwise apply the flat floor.
    val toolCapable = params >= ModelCapabilityProfile.AGENTIC_MIN_PARAMS_B
    return ModelCapabilityProfile(
        paramsB = params,
        contextWindow = contextLength.takeIf { it > 0 } ?: DEFAULT_CONTEXT_WINDOW,
        supportsToolCalls = toolCapable && !isVision,
        supportsVision = isVision,
        recommendedMaxTokens = if (toolCapable) {
            ModelCapabilityProfile.LARGE_MODEL_MAX_TOKENS
        } else {
            ModelCapabilityProfile.SMALL_MODEL_MAX_TOKENS
        },
    )
}

private const val DEFAULT_CONTEXT_WINDOW = 4096

/**
 * Parse a human "parameter count" string into billions.
 *
 * Handles the shapes registries actually emit: "0.5B", "1.5B", "3.8B", "7B",
 * "270M", "500m", with or without a trailing "params"/whitespace. Unknown or
 * unparseable input returns 0f, which the policy treats as below the floor
 * (safe default → deterministic).
 */
fun parseParameterCount(raw: String): Float {
    if (raw.isBlank()) return 0f
    // First number (optionally decimal) followed by an optional B/M unit.
    val match = Regex("([0-9]+(?:\\.[0-9]+)?)\\s*([bBmM])?").find(raw.trim()) ?: return 0f
    val value = match.groupValues[1].toFloatOrNull() ?: return 0f
    return when (match.groupValues[2].lowercase()) {
        "m" -> value / 1000f   // 270M → 0.27B
        else -> value          // "B" or unitless → already billions
    }
}
