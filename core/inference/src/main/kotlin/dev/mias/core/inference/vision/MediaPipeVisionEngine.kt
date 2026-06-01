package dev.mias.core.inference.vision

import android.content.Context
import android.graphics.Bitmap
import com.google.mediapipe.framework.image.BitmapImageBuilder
import com.google.mediapipe.tasks.genai.llminference.GraphOptions
import com.google.mediapipe.tasks.genai.llminference.LlmInference
import com.google.mediapipe.tasks.genai.llminference.LlmInferenceSession
import com.google.mediapipe.tasks.genai.llminference.ProgressListener
import dagger.hilt.android.qualifiers.ApplicationContext
import dev.mias.core.common.MiasResult
import dev.mias.core.common.di.IoDispatcher
import dev.mias.core.common.runCatchingMias
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * MediaPipe-backed vision engine.
 *
 * Wraps MediaPipe's LlmInference + LlmInferenceSession with vision modality
 * enabled, so callers can hand it a Bitmap + text prompt and stream back
 * a response. The model must be a vision-capable Gemma variant (Gemma 3n)
 * distributed as a `.task` bundle.
 *
 * Lifecycle: lazy. The first call to [process]/[processStream] (or an
 * explicit [load]) loads the model; subsequent calls reuse it. A fresh
 * session is created for each generation so previous image context does
 * not leak into the next turn.
 *
 * Failure modes the caller must handle:
 *  - Model file missing or wrong format → [MiasResult.Error].
 *  - Device cannot allocate enough memory for the .task → [MiasResult.Error].
 *  - User cancels mid-generation → flow closes; partial text is whatever
 *    was emitted before cancellation.
 */
@Singleton
class MediaPipeVisionEngine @Inject constructor(
    @ApplicationContext private val context: Context,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) {

    @Volatile
    private var llmInference: LlmInference? = null

    @Volatile
    private var loadedModelPath: String? = null

    fun isLoaded(): Boolean = llmInference != null

    fun isAvailable(modelPath: String): Boolean = File(modelPath).exists()

    suspend fun load(modelPath: String): MiasResult<Unit> = withContext(ioDispatcher) {
        runCatchingMias {
            if (loadedModelPath == modelPath && llmInference != null) return@runCatchingMias
            unload()

            val options = LlmInference.LlmInferenceOptions.builder()
                .setModelPath(modelPath)
                .setMaxTokens(MAX_TOKENS)
                .setMaxNumImages(MAX_IMAGES_PER_SESSION)
                .build()

            llmInference = LlmInference.createFromOptions(context, options)
            loadedModelPath = modelPath
        }
    }

    fun unload() {
        llmInference?.close()
        llmInference = null
        loadedModelPath = null
    }

    /**
     * One-shot vision Q&A. Loads the model if needed, runs the [prompt]
     * against the [image], returns the full response.
     */
    suspend fun process(
        modelPath: String,
        image: Bitmap,
        prompt: String,
    ): MiasResult<String> = withContext(ioDispatcher) {
        when (val loadResult = load(modelPath)) {
            is MiasResult.Error -> loadResult
            is MiasResult.Success -> runCatchingMias {
                val session = newSession()
                session.use {
                    it.addImage(BitmapImageBuilder(image).build())
                    it.addQueryChunk(prompt)
                    it.generateResponse()
                }
            }
        }
    }

    /**
     * Streaming variant. Each emitted [MiasResult.Success] carries an
     * incremental delta — matches the contract of
     * [dev.mias.core.inference.InferenceEngine.generateStream].
     */
    fun processStream(
        modelPath: String,
        image: Bitmap,
        prompt: String,
    ): Flow<MiasResult<String>> = callbackFlow {
        when (val loadResult = load(modelPath)) {
            is MiasResult.Error -> {
                trySend(loadResult)
                close()
                return@callbackFlow
            }
            is MiasResult.Success -> Unit
        }

        val session = newSession()
        try {
            session.addImage(BitmapImageBuilder(image).build())
            session.addQueryChunk(prompt)
            // partial = incremental delta (MediaPipe streams token deltas).
            // On done we only close the channel; the single session.close()
            // lives in awaitClose so we never double-close.
            val listener = ProgressListener<String> { partial, done ->
                trySend(MiasResult.Success(partial))
                if (done) close()
            }
            session.generateResponseAsync(listener)
        } catch (e: Exception) {
            trySend(MiasResult.Error("Vision inference failed: ${e.message}"))
            close(e)
        }
        awaitClose {
            // Covers normal completion, error, and collector cancellation
            // (user left the screen mid-generation). Cancel first so the
            // native side stops calling the listener before we free it.
            runCatching { session.cancelGenerateResponseAsync() }
            runCatching { session.close() }
        }
    }

    private fun newSession(): LlmInferenceSession {
        val inference = requireNotNull(llmInference) { "MediaPipe vision model not loaded" }
        val sessionOptions = LlmInferenceSession.LlmInferenceSessionOptions.builder()
            .setTemperature(DEFAULT_TEMPERATURE)
            .setTopK(DEFAULT_TOP_K)
            .setTopP(DEFAULT_TOP_P)
            .setGraphOptions(
                GraphOptions.builder()
                    .setEnableVisionModality(true)
                    .build(),
            )
            .build()
        return LlmInferenceSession.createFromOptions(inference, sessionOptions)
    }

    companion object {
        private const val MAX_TOKENS: Int = 1024
        private const val MAX_IMAGES_PER_SESSION: Int = 1
        private const val DEFAULT_TEMPERATURE: Float = 0.4f
        private const val DEFAULT_TOP_K: Int = 40
        private const val DEFAULT_TOP_P: Float = 0.95f
    }
}
