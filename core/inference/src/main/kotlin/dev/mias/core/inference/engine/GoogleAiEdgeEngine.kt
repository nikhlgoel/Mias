package dev.mias.core.inference.engine

import android.content.Context
import com.google.mediapipe.tasks.genai.llminference.LlmInference
import dev.mias.core.common.MiasResult
import dev.mias.core.common.runCatchingMias
import dev.mias.core.inference.InferenceEngine
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

    override suspend fun loadModel(modelPath: String): MiasResult<Unit> =
        withContext(Dispatchers.IO) {
            runCatchingMias {
                val options = LlmInference.LlmInferenceOptions.builder()
                    .setModelPath(modelPath.ifBlank { this@GoogleAiEdgeEngine.modelPath })
                    .setMaxTokens(2048)
                    .setMaxTopK(40)
                    .build()

                llmInference = LlmInference.createFromOptions(context, options)
                isLoaded = true
            }
        }

    override suspend fun generate(prompt: String, maxTokens: Int): MiasResult<String> =
        withContext(Dispatchers.IO) {
            runCatchingMias {
                val inference = requireNotNull(llmInference) {
                    "Model not loaded. Call loadModel() first."
                }
                inference.generateResponse(prompt)
            }
        }

    override fun generateStream(prompt: String, maxTokens: Int): Flow<MiasResult<String>> =
        callbackFlow {
            val inference = llmInference
            if (inference == null) {
                trySend(MiasResult.Error("Model not loaded. Call loadModel() first."))
                close()
                return@callbackFlow
            }

            launch(Dispatchers.IO) {
                try {
                    trySend(MiasResult.Success(inference.generateResponse(prompt)))
                } catch (e: Exception) {
                    trySend(MiasResult.Error("AI Edge inference failed: ${e.message}"))
                } finally {
                    close()
                }
            }

            awaitClose {
                // Stream cancelled — no cleanup needed as LlmInference handles its own lifecycle
            }
        }

    override suspend fun unloadModel(): MiasResult<Unit> = runCatchingMias {
        llmInference?.close()
        llmInference = null
        isLoaded = false
    }

    override fun isModelLoaded(): Boolean = isLoaded
}
