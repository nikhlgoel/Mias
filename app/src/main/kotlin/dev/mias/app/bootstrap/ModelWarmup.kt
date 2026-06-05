package dev.mias.app.bootstrap

import android.util.Log
import dev.mias.core.common.model.EmbeddingProvider
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Background warm-up so the first message feels instant.
 *
 * Startup warms only the small embedding model — it unblocks Hindsight memory
 * and RAG, which run before generation on every message. The larger chat model
 * is deliberately NOT loaded here; it's warmed lazily when a chat actually
 * opens (see ChatViewModel.init → orchestrator.warmUp), so the app doesn't burn
 * RAM/battery loading weights the user may never use.
 *
 * Best-effort and idempotent: if no model is installed yet, or a load fails,
 * nothing breaks — the normal lazy path still runs on first use.
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
