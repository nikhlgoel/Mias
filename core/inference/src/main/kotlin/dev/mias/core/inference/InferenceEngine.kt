package dev.mias.core.inference

import dev.mias.core.common.MiasResult

/**
 * Contract for on-device model inference.
 * Implementations must run entirely on-device (ONNX Runtime / MediaPipe).
 * No cloud fallback is permitted.
 */
interface InferenceEngine {
    suspend fun loadModel(modelPath: String): MiasResult<Unit>

    /**
     * @param grammar Optional GBNF grammar. When non-null, the engine
     *   constrains generation to tokens matching the grammar (used by the
     *   ReAct/agentic loop to force valid JSON). Null = unconstrained
     *   (standard chat). Engines without grammar support ignore it.
     */
    suspend fun generate(prompt: String, maxTokens: Int = 512, grammar: String? = null): MiasResult<String>

    /**
     * Streams output token by token.
     *
     * Contract: each [MiasResult.Success] emission carries an **incremental delta**
     * — the new token(s) only, not the cumulative response so far. Callers append
     * deltas in order to build the full output. Engines that cannot stream true
     * deltas should emit the full response as a single chunk.
     *
     * Essential for fast Time-To-First-Token (TTFT) on UI.
     *
     * @param grammar Optional GBNF grammar — see [generate].
     */
    fun generateStream(
        prompt: String,
        maxTokens: Int = 512,
        grammar: String? = null,
    ): kotlinx.coroutines.flow.Flow<MiasResult<String>>

    suspend fun unloadModel(): MiasResult<Unit>
    fun isModelLoaded(): Boolean

    /**
     * Apply per-model decoding parameters (temperature, top-k/p, repetition
     * penalty). Called by the orchestrator after a model loads so each family
     * gets sampling it was tuned for. Engines that manage sampling internally
     * (MediaPipe/NPU) ignore it — hence the no-op default.
     */
    fun applySamplingProfile(profile: SamplingProfile) {}
}
