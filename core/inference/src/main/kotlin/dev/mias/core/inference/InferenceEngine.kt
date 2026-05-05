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
     * Essential for fast Time-To-First-Token (TTFT) on UI.
     */
    fun generateStream(prompt: String, maxTokens: Int = 512): kotlinx.coroutines.flow.Flow<MiasResult<String>>

    suspend fun unloadModel(): MiasResult<Unit>
    fun isModelLoaded(): Boolean
}
