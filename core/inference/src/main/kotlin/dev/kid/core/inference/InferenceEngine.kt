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
    suspend fun unloadModel(): KidResult<Unit>
    fun isModelLoaded(): Boolean
}
