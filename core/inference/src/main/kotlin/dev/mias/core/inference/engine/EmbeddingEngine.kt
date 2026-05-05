package dev.mias.core.inference.engine

import dev.mias.core.common.MiasResult
import dev.mias.core.common.model.EmbeddingProvider
import dev.mias.core.common.runCatchingMias
import javax.inject.Inject

/**
 * High-performance embedding engine wrapping natively compiled llama.cpp.
 *
 * Utilizes JNI to execute GGUF embedding models (like Nomic Embed v2) directly on CPU.
 * Retrieves FloatArrays to be stored in the Hindsight vector database for semantic search.
 */
class EmbeddingEngine @Inject constructor() : EmbeddingProvider {

    @Volatile
    private var isLoaded = false

    suspend fun loadModel(modelPath: String): MiasResult<Unit> = runCatchingMias {
        if (!nativeLoadEmbeddingModel(modelPath)) {
            throw IllegalStateException("Failed to bind embeddings llama.cpp to GGUF path: $modelPath")
        }
        isLoaded = true
    }

    override suspend fun getEmbedding(text: String): MiasResult<FloatArray> = runCatchingMias {
        check(isLoaded) { "Embedding model not loaded. Call loadModel() first." }
        nativeGetEmbedding(text) ?: throw IllegalStateException("JNI returned null embedding array")
    }

    suspend fun unloadModel(): MiasResult<Unit> = runCatchingMias {
        nativeUnloadEmbeddingModel()
        isLoaded = false
    }

    fun isModelLoaded(): Boolean = isLoaded

    // JNI native methods (Implemented in mias_jni_bridge.cpp)
    private external fun nativeLoadEmbeddingModel(path: String): Boolean
    private external fun nativeGetEmbedding(text: String): FloatArray?
    private external fun nativeUnloadEmbeddingModel()

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
