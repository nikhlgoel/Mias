package dev.mias.core.inference.engine

import dev.mias.core.common.MiasResult
import dev.mias.core.common.model.EmbeddingProvider
import dev.mias.core.common.runCatchingMias
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * High-performance embedding engine wrapping natively compiled llama.cpp.
 *
 * Utilizes JNI to execute GGUF embedding models (like Nomic Embed v2) directly on CPU.
 * Retrieves FloatArrays to be stored in the Hindsight vector database for semantic search.
 *
 * Must be `@Singleton`: the underlying llama.cpp embedding context is a single
 * global native slot, and both [dev.mias.core.data.hindsight.HindsightMemory]
 * (via [EmbeddingProvider]) and [dev.mias.core.inference.orchestrator.RoleClassifier]
 * (direct injection) share it. Two instances would diverge on `isLoaded` and
 * double-load the native model.
 */
@Singleton
class EmbeddingEngine @Inject constructor() : EmbeddingProvider {

    @Volatile
    private var isLoaded = false

    // All native calls run on Dispatchers.IO — they block (model load / inference)
    // and would ANR the UI if a caller invoked them from the main thread.
    suspend fun loadModel(modelPath: String): MiasResult<Unit> = withContext(Dispatchers.IO) {
        runCatchingMias {
            if (!nativeLoadEmbeddingModel(modelPath)) {
                throw IllegalStateException("Failed to bind embeddings llama.cpp to GGUF path: $modelPath")
            }
            isLoaded = true
        }
    }

    override suspend fun getEmbedding(text: String): MiasResult<FloatArray> = withContext(Dispatchers.IO) {
        runCatchingMias {
            check(isLoaded) { "Embedding model not loaded. Call loadModel() first." }
            nativeGetEmbedding(text) ?: throw IllegalStateException("JNI returned null embedding array")
        }
    }

    suspend fun unloadModel(): MiasResult<Unit> = withContext(Dispatchers.IO) {
        runCatchingMias {
            nativeUnloadEmbeddingModel()
            isLoaded = false
        }
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
