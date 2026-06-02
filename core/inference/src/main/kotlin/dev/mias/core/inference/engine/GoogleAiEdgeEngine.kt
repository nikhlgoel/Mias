package dev.mias.core.inference.engine

import android.content.Context
import com.google.mediapipe.tasks.genai.llminference.LlmInference
import com.google.mediapipe.tasks.genai.llminference.LlmInferenceSession
import dev.mias.core.common.MiasResult
import dev.mias.core.common.runCatchingMias
import dev.mias.core.inference.InferenceEngine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
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

    // MediaPipe GenAI does not support GBNF grammars; the `grammar` argument
    // is accepted for interface conformance and ignored.
    override suspend fun generate(prompt: String, maxTokens: Int, grammar: String?): MiasResult<String> =
        withContext(Dispatchers.IO) {
            runCatchingMias {
                val inference = requireNotNull(llmInference) {
                    "Model not loaded. Call loadModel() first."
                }
                inference.generateResponse(prompt)
            }
        }

    override fun generateStream(prompt: String, maxTokens: Int, grammar: String?): Flow<MiasResult<String>> =
        callbackFlow {
            val inference = llmInference
            if (inference == null) {
                trySend(MiasResult.Error("Model not loaded. Call loadModel() first."))
                close()
                return@callbackFlow
            }

            // A per-request session gives both true token streaming and a real
            // cancellation handle. The previous blocking generateResponse() call
            // ignored cancellation entirely — the Stop button changed the UI but
            // the NPU kept running to completion. cancelGenerateResponseAsync()
            // halts the in-flight run so those cycles are actually freed.
            val sessionOptions = LlmInferenceSession.LlmInferenceSessionOptions.builder()
                .setTopK(40)
                .setTopP(0.9f)
                .setTemperature(0.7f)
                .build()

            val session = try {
                LlmInferenceSession.createFromOptions(inference, sessionOptions)
            } catch (e: Exception) {
                trySend(MiasResult.Error("AI Edge session failed: ${e.message}"))
                close()
                return@callbackFlow
            }

            try {
                session.addQueryChunk(prompt)
                // ProgressListener.run(partial, done): each partial is an
                // incremental delta, matching the generateStream contract.
                session.generateResponseAsync { partial, done ->
                    trySend(MiasResult.Success(partial))
                    if (done) close()
                }
            } catch (e: Exception) {
                trySend(MiasResult.Error("AI Edge inference failed: ${e.message}"))
                close()
            }

            awaitClose {
                // Stop button / collector cancellation: halt generation, then
                // release the session so the native context is freed without a
                // JNI pointer leak. Guarded so a normal completion (already
                // closed) doesn't throw on double-close.
                runCatching { session.cancelGenerateResponseAsync() }
                runCatching { session.close() }
            }
        }

    override suspend fun unloadModel(): MiasResult<Unit> = runCatchingMias {
        llmInference?.close()
        llmInference = null
        isLoaded = false
    }

    override fun isModelLoaded(): Boolean = isLoaded
}
