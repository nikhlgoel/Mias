package dev.mias.core.inference.engine

import dev.mias.core.common.MiasResult
import dev.mias.core.common.runCatchingMias
import dev.mias.core.inference.InferenceEngine
import dev.mias.core.inference.InferenceError
import dev.mias.core.inference.SamplingProfile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.channels.trySendBlocking
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * High-performance inference engine wrapping natively compiled llama.cpp.
 *
 * Utilizes JNI to execute GGUF models directly on CPU/GPU via cross-platform C++.
 * This engine forms the core of our scalable inference layer, replacing single-backend
 * engines and providing access to extensive optimization (ARM NEON) globally.
 *
 * **Must be @Singleton:** the native side (`model`/`ctx`/`sampler`) is a single
 * set of C++ file-static globals — there is exactly one native model slot. If
 * Hilt handed out two wrapper instances (e.g. one for the "primary" and one for
 * the "survival" engine), each would carry its own `isLoaded` flag while sharing
 * that one slot, so loading the survival model would silently leave the primary
 * wrapper *thinking* it still holds the chat model. One wrapper ⇒ one slot.
 */
@Singleton
class LlamaCppEngine @Inject constructor() : InferenceEngine {

    init {
        runCatching { nativeInit() }
    }

    @Volatile
    private var isLoaded = false

    override suspend fun loadModel(modelPath: String): MiasResult<Unit> =
        withContext(Dispatchers.IO) {
            // MUST be off the main thread: loading a GGUF reads/mmaps hundreds of
            // MB and allocates the KV cache, which blocks for seconds and ANRs the
            // UI if run on the caller's (Main) dispatcher.
            try {
                // Foolproof pre-check: a missing or truncated file means the
                // download never finished or was corrupted. Surface that clearly
                // (and typed) instead of a generic native "failed to bind".
                val file = java.io.File(modelPath)
                if (!file.exists() || file.length() < MIN_VALID_MODEL_BYTES) {
                    throw InferenceError.ModelFileInvalid(modelPath, file.length())
                }
                if (!nativeLoadModel(modelPath)) {
                    throw InferenceError.ModelLoadFailed(
                        "Couldn't load the model — the file may be corrupted or an " +
                            "unsupported format. Re-download it from Models.",
                        retryable = false,
                    )
                }
                isLoaded = true
                MiasResult.Success(Unit)
            } catch (e: Throwable) {
                val err = InferenceError.classify(e)
                MiasResult.Error(err.message, err)
            }
        }

    override suspend fun generate(prompt: String, maxTokens: Int, grammar: String?): MiasResult<String> =
        withContext(Dispatchers.IO) {
            try {
                if (!isLoaded) throw InferenceError.ModelNotLoaded
                MiasResult.Success(nativeGenerate(prompt, maxTokens, grammar.orEmpty()))
            } catch (e: Throwable) {
                val err = InferenceError.classify(e)
                MiasResult.Error(err.message, err)
            }
        }

    override fun generateStream(prompt: String, maxTokens: Int, grammar: String?): Flow<MiasResult<String>> =
        callbackFlow {
            if (!isLoaded) {
                trySend(MiasResult.Error(InferenceError.ModelNotLoaded.message, InferenceError.ModelNotLoaded))
                close()
                return@callbackFlow
            }

            val callback: (String) -> Unit = { token ->
                // Block the native-calling IO thread (never Main) if the channel
                // buffer momentarily fills, so a fast token burst is never
                // silently dropped — the old trySend() could lose tokens.
                trySendBlocking(MiasResult.Success(token))
            }

            launch(Dispatchers.IO) {
                try {
                    // Empty grammar string = unconstrained (native treats "" as none).
                    nativeGenerateStream(prompt, maxTokens, grammar.orEmpty(), callback)
                } catch (e: Throwable) {
                    val err = InferenceError.classify(e)
                    trySend(MiasResult.Error(err.message, err))
                } finally {
                    close()
                }
            }

            awaitClose {
                // Cancelling the collector (e.g. the user taps Stop, which
                // cancels the inference Job) flips the native abort flag so
                // the in-flight C++ generation loop breaks out within one token
                // instead of running to completion.
                runCatching { nativeStopGeneration() }
            }
        }

    /**
     * Push per-model sampling to the native sampler. Guarded so a missing
     * native lib (unit tests) is a silent no-op rather than a crash.
     */
    override fun applySamplingProfile(profile: SamplingProfile) {
        runCatching {
            nativeSetSampling(
                profile.temperature,
                profile.topK,
                profile.topP,
                profile.repeatPenalty,
                profile.repeatLastN,
                profile.seed,
            )
        }
    }

    /**
     * Request the active native generation to halt. Safe to call at any time;
     * it only sets an atomic flag the C++ loop polls. A no-op when nothing is
     * generating.
     */
    fun stopGeneration() {
        runCatching { nativeStopGeneration() }
    }

    override suspend fun unloadModel(): MiasResult<Unit> = withContext(Dispatchers.IO) {
        runCatchingMias {
            nativeUnload()
            isLoaded = false
        }
    }

    override fun isModelLoaded(): Boolean = isLoaded

    // JNI native methods (Implemented in mias_jni_bridge.cpp)
    private external fun nativeInit()
    private external fun nativeLoadModel(path: String): Boolean
    private external fun nativeGenerate(prompt: String, maxTokens: Int, grammar: String): String
    private external fun nativeGenerateStream(prompt: String, maxTokens: Int, grammar: String, callback: (String) -> Unit)
    private external fun nativeStopGeneration()
    private external fun nativeUnload()
    private external fun nativeSetSampling(
        temperature: Float,
        topK: Int,
        topP: Float,
        repeatPenalty: Float,
        repeatLastN: Int,
        seed: Int,
    )

    companion object {
        /** Smallest plausible GGUF; anything under this is a failed/partial download. */
        private const val MIN_VALID_MODEL_BYTES = 1_000_000L // 1 MB

        init {
            try {
                System.loadLibrary("mias_inference")
            } catch (e: UnsatisfiedLinkError) {
                // In dev-mode, fail silently if library doesn't exist yet, avoiding crashing tests.
            }
        }
    }
}
