package dev.kid.core.inference.engine

import android.content.Context
import com.google.mediapipe.tasks.genai.llminference.LlmInference
import dev.kid.core.common.KidResult
import dev.kid.core.common.runCatchingKid
import dev.kid.core.inference.InferenceEngine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Google AI Edge inference engine wrapping MediaPipe GenAI LlmInference.
 *
 * Uses the Google AI Edge SDK to run Gemma models with NPU acceleration
 * when available on the device. This makes the "runs on NPU" claim honest
 * for Gemma models on supported Pixel/Samsung devices.
 *
 * Priority in orchestrator: GoogleAiEdgeEngine (Gemma) → LlamaCppEngine (Qwen) → DesktopEngine
 */
class GoogleAiEdgeEngine(
    private val context: Context,
    private val modelPath: String,
) : InferenceEngine {

    @Volatile
    private var llmInference: LlmInference? = null

    @Volatile
    private var isLoaded = false

    /**
     * Check if this engine is available — the Gemma model file must exist at the
     * configured path for this engine to be usable.
     */
    fun isAvailable(): Boolean {
        return File(modelPath).exists()
    }

    override suspend fun loadModel(modelPath: String): KidResult<Unit> =
        withContext(Dispatchers.IO) {
            runCatchingKid {
                val options = LlmInference.LlmInferenceOptions.builder()
                    .setModelPath(modelPath.ifBlank { this@GoogleAiEdgeEngine.modelPath })
                    .setMaxTokens(2048)
                    .setMaxTopK(40)
                    .setTemperature(0.7f)
                    .setTopK(40)
                    .build()

                llmInference = LlmInference.createFromOptions(context, options)
                isLoaded = true
            }
        }

    override suspend fun generate(prompt: String, maxTokens: Int): KidResult<String> =
        withContext(Dispatchers.IO) {
            runCatchingKid {
                val inference = requireNotNull(llmInference) {
                    "Model not loaded. Call loadModel() first."
                }
                inference.generateResponse(prompt)
            }
        }

    override fun generateStream(prompt: String, maxTokens: Int): Flow<KidResult<String>> =
        callbackFlow {
            val inference = llmInference
            if (inference == null) {
                trySend(KidResult.Error("Model not loaded. Call loadModel() first."))
                close()
                return@callbackFlow
            }

            launch(Dispatchers.IO) {
                try {
                    inference.generateResponseAsync(prompt).forEach { partialResult ->
                        trySend(KidResult.Success(partialResult))
                    }
                } catch (e: Exception) {
                    trySend(KidResult.Error("AI Edge inference failed: ${e.message}"))
                } finally {
                    close()
                }
            }

            awaitClose {
                // Stream cancelled — no cleanup needed as LlmInference handles its own lifecycle
            }
        }

    override suspend fun unloadModel(): KidResult<Unit> = runCatchingKid {
        llmInference?.close()
        llmInference = null
        isLoaded = false
    }

    override fun isModelLoaded(): Boolean = isLoaded
}
