package dev.mias.core.inference

/**
 * Decoding/sampling parameters for one model family.
 *
 * Different model families are trained to expect different sampling. Feeding
 * every model the same temperature/top-p leaves quality on the table — Qwen
 * recommends tighter top-p/top-k than the generic default, and code models
 * want near-greedy decoding for correctness. These are applied to the native
 * llama.cpp sampler per loaded model.
 *
 * [DEFAULT] reproduces the engine's historical hardcoded values exactly, so an
 * unrecognised model behaves precisely as before.
 */
data class SamplingProfile(
    val temperature: Float,
    val topK: Int,
    val topP: Float,
    val repeatPenalty: Float,
    val repeatLastN: Int,
    val seed: Int = DEFAULT_SEED,
) {
    companion object {
        const val DEFAULT_SEED = 1234
    }
}

/**
 * Picks the [SamplingProfile] for a model by family, from its name.
 *
 * Conservative on purpose: only families with well-known recommended settings
 * deviate from [DEFAULT]; everything else gets the proven default. Values can
 * be tuned after on-device A/B testing.
 */
object SamplingProfiles {

    /** Exactly the engine's previous hardcoded sampler — the safe baseline. */
    val DEFAULT = SamplingProfile(
        temperature = 0.7f,
        topK = 40,
        topP = 0.9f,
        repeatPenalty = 1.17f,
        repeatLastN = 64,
    )

    /** Qwen2.5 family — Alibaba's recommended sampling (tighter top-p/top-k). */
    val QWEN = SamplingProfile(
        temperature = 0.7f,
        topK = 20,
        topP = 0.8f,
        repeatPenalty = 1.1f,
        repeatLastN = 64,
    )

    /** Phi-3.5 family. */
    val PHI = SamplingProfile(
        temperature = 0.7f,
        topK = 40,
        topP = 0.9f,
        repeatPenalty = 1.1f,
        repeatLastN = 64,
    )

    /** Code models — near-greedy for correctness and stable structure. */
    val CODE = SamplingProfile(
        temperature = 0.2f,
        topK = 20,
        topP = 0.8f,
        repeatPenalty = 1.05f,
        repeatLastN = 64,
    )

    fun forModel(modelName: String): SamplingProfile {
        val n = modelName.lowercase()
        return when {
            "coder" in n || "code" in n -> CODE
            "qwen" in n -> QWEN
            "phi" in n -> PHI
            else -> DEFAULT
        }
    }
}
