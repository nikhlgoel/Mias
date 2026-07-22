package dev.mias.mobile.bootstrap

import android.util.Log
import dev.mias.core.common.model.EmbeddingProvider
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Background warm-up so the first message feels instant (relocated at the S7
 * cutover). Warms only the small embedding model — it unblocks Hindsight memory
 * and RAG, which run before generation on every message. The larger chat model
 * is warmed lazily when a chat opens. Best-effort and idempotent.
 */
@Singleton
class ModelWarmup @Inject constructor(
    private val embeddingProvider: EmbeddingProvider,
) {
    suspend fun warm() {
        runCatching {
            if (embeddingProvider.isReady()) embeddingProvider.getEmbedding(WARM_TEXT)
        }.onFailure { Log.d(TAG, "Embedding warm-up skipped: ${it.message}") }
    }

    companion object {
        private const val TAG = "ModelWarmup"
        private const val WARM_TEXT = "warm up"
    }
}
