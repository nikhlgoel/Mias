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

    override suspend fun loadModel(modelPath: String): MiasResult<Unit> = runCatchingMias {
        if (!nativeLoadModel(modelPath)) {
            throw IllegalStateException("Failed to bind llama.cpp to GGUF model path: $modelPath")
        }
        isLoaded = true
    }

    override suspend fun generate(prompt: String, maxTokens: Int): MiasResult<String> =
        withContext(Dispatchers.IO) {
            runCatchingMias {
                check(isLoaded) { "Model not loaded. Call loadModel() first." }
                nativeGenerate(prompt, maxTokens)
            }
        }

    override fun generateStream(prompt: String, maxTokens: Int): Flow<MiasResult<String>> =
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
                    nativeGenerateStream(prompt, maxTokens, callback)
                } finally {
                    close()
                }
            }

            awaitClose {
                // If stream gets cancelled mid-flight, we'd theoretically signal the C++ loop.
                // For now, C++ processes the stream completely.
            }
        }

    override suspend fun unloadModel(): MiasResult<Unit> = runCatchingMias {
        nativeUnload()
        isLoaded = false
    }

    override fun isModelLoaded(): Boolean = isLoaded

    // JNI native methods (Implemented in mias_jni_bridge.cpp)
    private external fun nativeInit()
    private external fun nativeLoadModel(path: String): Boolean
    private external fun nativeGenerate(prompt: String, maxTokens: Int): String
    private external fun nativeGenerateStream(prompt: String, maxTokens: Int, callback: (String) -> Unit)
    private external fun nativeUnload()

    companion object {
        init {
            try {
                System.loadLibrary("mias_inference")
            } catch (e: UnsatisfiedLinkError) {
                // In dev-mode, fail silently if library doesn't exist yet, avoiding crashing tests.
            }
        }
    }
}
