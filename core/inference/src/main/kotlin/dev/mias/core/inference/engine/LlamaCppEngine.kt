package dev.mias.core.inference.engine

import dev.mias.core.common.MiasResult
import dev.mias.core.common.runCatchingMias
import dev.mias.core.inference.InferenceEngine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

/**
 * High-performance inference engine wrapping natively compiled llama.cpp.
 *
 * Utilizes JNI to execute GGUF models directly on CPU/GPU via cross-platform C++.
 * This engine forms the core of our scalable inference layer, replacing single-backend
 * engines and providing access to extensive optimization (ARM NEON) globally.
 */
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
            runCatchingMias {
                // Foolproof pre-check: a missing or truncated file means the
                // download never finished or was corrupted. Surface that clearly
                // instead of a generic native "failed to bind".
                val file = java.io.File(modelPath)
                if (!file.exists() || file.length() < MIN_VALID_MODEL_BYTES) {
                    throw IllegalStateException(
                        "Model file is missing or incomplete (${file.length()} bytes). " +
                            "The download may have failed — delete and re-download it.",
                    )
                }
                if (!nativeLoadModel(modelPath)) {
                    throw IllegalStateException(
                        "Couldn't load the model — the file may be corrupted or an " +
                            "unsupported format. Re-download it from Models.",
                    )
                }
                isLoaded = true
            }
        }

    override suspend fun generate(prompt: String, maxTokens: Int, grammar: String?): MiasResult<String> =
        withContext(Dispatchers.IO) {
            runCatchingMias {
                check(isLoaded) { "Model not loaded. Call loadModel() first." }
                nativeGenerate(prompt, maxTokens, grammar.orEmpty())
            }
        }

    override fun generateStream(prompt: String, maxTokens: Int, grammar: String?): Flow<MiasResult<String>> =
        callbackFlow {
            if (!isLoaded) {
                trySend(MiasResult.Error("Model not loaded. Call loadModel() first."))
                close()
                return@callbackFlow
            }

            val callback: (String) -> Unit = { token ->
                trySend(MiasResult.Success(token))
            }

            launch(Dispatchers.IO) {
                try {
                    // Empty grammar string = unconstrained (native treats "" as none).
                    nativeGenerateStream(prompt, maxTokens, grammar.orEmpty(), callback)
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
