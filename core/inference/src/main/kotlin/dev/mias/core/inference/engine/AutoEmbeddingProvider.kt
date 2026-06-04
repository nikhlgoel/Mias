package dev.mias.core.inference.engine

import dev.mias.core.common.MiasResult
import dev.mias.core.common.model.EmbeddingProvider
import dev.mias.core.modelhub.manager.ModelManager
import dev.mias.core.modelhub.model.ModelRole
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject
import javax.inject.Singleton

/**
 * [EmbeddingProvider] that lazily loads the installed EMBEDDING-role model into
 * the [EmbeddingEngine] the first time an embedding is requested.
 *
 * Previously nothing ever called `EmbeddingEngine.loadModel`, so every embedding
 * request failed and both Hindsight memory and RAG silently degraded. The
 * embedding model runs in its own native context (separate from the chat model),
 * so loading it here is independent of, and safe alongside, the chat engine.
 */
@Singleton
class AutoEmbeddingProvider @Inject constructor(
    private val engine: EmbeddingEngine,
    private val modelManager: ModelManager,
) : EmbeddingProvider {

    private val loadMutex = Mutex()

    override suspend fun getEmbedding(text: String): MiasResult<FloatArray> {
        if (!ensureLoaded()) {
            return MiasResult.Error("No embedding model is installed. Add one (e.g. Nomic Embed) in Models.")
        }
        return engine.getEmbedding(text)
    }

    override suspend fun isReady(): Boolean =
        engine.isModelLoaded() || modelManager.getModelForRole(ModelRole.EMBEDDING) != null

    /** Loads the EMBEDDING model once. Returns true if the engine is ready. */
    private suspend fun ensureLoaded(): Boolean {
        if (engine.isModelLoaded()) return true
        return loadMutex.withLock {
            if (engine.isModelLoaded()) return@withLock true
            val model = modelManager.getModelForRole(ModelRole.EMBEDDING) ?: return@withLock false
            engine.loadModel(model.localPath) is MiasResult.Success
        }
    }
}
