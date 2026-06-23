package dev.mias.core.inference.engine

import dev.mias.core.common.MiasResult
import dev.mias.core.common.cache.TtlCache
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
 *
 * Caching: short texts (queries) are cached briefly. One chat turn embeds the
 * same user text up to twice — RAG retrieval and Hindsight query — so the
 * second call should not pay a native inference pass. Long texts (document
 * chunks during ingest) are deliberately NOT cached: they are one-shot by
 * nature and hundreds of them would just churn the query entries out.
 */
@Singleton
class AutoEmbeddingProvider @Inject constructor(
    private val engine: EmbeddingEngine,
    private val modelManager: ModelManager,
) : EmbeddingProvider {

    private val loadMutex = Mutex()

    /**
     * text → embedding for short (query-sized) inputs. Values are treated as
     * immutable by every consumer (cosine reads, byte serialization), so the
     * same array instance can be handed out safely.
     */
    private val queryCache = TtlCache<String, FloatArray>(
        ttlMillis = CACHE_TTL_MS,
        maxEntries = CACHE_MAX_ENTRIES,
    )

    override suspend fun getEmbedding(text: String): MiasResult<FloatArray> {
        if (!ensureLoaded()) {
            return MiasResult.Error("No embedding model is installed. Add one (e.g. Nomic Embed) in Models.")
        }
        val cacheable = text.length <= CACHE_MAX_TEXT_CHARS
        if (cacheable) {
            queryCache.get(text)?.let { return MiasResult.Success(it) }
        }
        val result = engine.getEmbedding(text)
        if (cacheable && result is MiasResult.Success) {
            queryCache.put(text, result.data)
        }
        return result
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

    companion object {
        /** Queries are short; document chunks (~800 chars) stay uncached. */
        private const val CACHE_MAX_TEXT_CHARS = 512
        private const val CACHE_TTL_MS = 10 * 60 * 1000L

        /** 768-dim float vectors ≈ 3 KB each → ≤ ~100 KB total. */
        private const val CACHE_MAX_ENTRIES = 32
    }
}
