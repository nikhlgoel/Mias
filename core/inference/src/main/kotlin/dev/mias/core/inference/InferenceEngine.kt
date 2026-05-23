package dev.mias.core.inference

import dev.mias.core.common.MiasResult

/**
 * Contract for on-device model inference.
 * Implementations must run entirely on-device (ONNX Runtime / MediaPipe).
 * No cloud fallback is permitted.
 */
interface InferenceEngine {
    suspend fun loadModel(modelPath: String): MiasResult<Unit>
    suspend fun generate(prompt: String, maxTokens: Int = 512): MiasResult<String>
    
    /**
     * Streams output token by token.
     *
     * Contract: each [MiasResult.Success] emission carries an **incremental delta**
     * — the new token(s) only, not the cumulative response so far. Callers append
     * deltas in order to build the full output. Engines that cannot stream true
     * deltas should emit the full response as a single chunk.
     *
     * Essential for fast Time-To-First-Token (TTFT) on UI.
     */
    fun generateStream(prompt: String, maxTokens: Int = 512): kotlinx.coroutines.flow.Flow<MiasResult<String>>

    suspend fun unloadModel(): MiasResult<Unit>
    fun isModelLoaded(): Boolean
}
