package dev.mias.app.bootstrap

import android.util.Log
import dev.mias.core.common.model.EmbeddingProvider
import dev.mias.core.inference.orchestrator.InferenceOrchestrator
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Background warm-up so the first message feels instant.
 *
 * On-device models pay a one-time multi-hundred-MB load. Doing it lazily on the
 * first send means the user stares at "Thinking" while weights stream off disk.
 * This pre-loads them shortly after launch, on a background dispatcher, so by
 * the time the user types the engine is usually already hot.
 *
 * Best-effort and idempotent: if no model is installed yet, or a load fails,
 * nothing breaks — the normal lazy path still runs on first use.
 */
@Singleton
class ModelWarmup @Inject constructor(
    private val orchestrator: InferenceOrchestrator,
    private val embeddingProvider: EmbeddingProvider,
) {
    suspend fun warm() {
        // Embedding model first: it's small and unblocks Hindsight memory + RAG
        // retrieval, both of which run before generation on every message.
        runCatching {
            if (embeddingProvider.isReady()) embeddingProvider.getEmbedding(WARM_TEXT)
        }.onFailure { Log.d(TAG, "Embedding warm-up skipped: ${it.message}") }

        // Then the chat model, so the first real send streams immediately.
        runCatching { orchestrator.warmUp() }
            .onFailure { Log.d(TAG, "Chat model warm-up skipped: ${it.message}") }
    }

    companion object {
        private const val TAG = "ModelWarmup"
        private const val WARM_TEXT = "warm up"
    }
}
