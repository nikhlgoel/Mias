package dev.kid.core.inference

import dev.kid.core.common.KidResult

/**
 * Contract for on-device model inference.
 * Implementations must run entirely on-device (ONNX Runtime / MediaPipe).
 * No cloud fallback is permitted.
 */
interface InferenceEngine {
    suspend fun loadModel(modelPath: String): KidResult<Unit>
    suspend fun generate(prompt: String, maxTokens: Int = 512): KidResult<String>
    
    /** 
     * Streams output token by token.
     * Essential for fast Time-To-First-Token (TTFT) on UI.
     */
    fun generateStream(prompt: String, maxTokens: Int = 512): kotlinx.coroutines.flow.Flow<KidResult<String>>

    suspend fun unloadModel(): KidResult<Unit>
    fun isModelLoaded(): Boolean
}
