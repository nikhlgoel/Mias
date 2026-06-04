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
    /**
     * Startup warm-up — embeddings only. The embedding model is small and
     * unblocks Hindsight memory + RAG, which run before generation on every
     * message. We deliberately do NOT load the larger chat model here: that's
     * deferred to [warmChatModel], triggered when a chat actually opens, so the
     * app doesn't burn RAM/battery loading weights the user may never use.
     */
    suspend fun warm() {
        runCatching {
            if (embeddingProvider.isReady()) embeddingProvider.getEmbedding(WARM_TEXT)
        }.onFailure { Log.d(TAG, "Embedding warm-up skipped: ${it.message}") }
    }

    /** Warm the chat model — call when the user enters a chat, not at startup. */
    suspend fun warmChatModel() {
        runCatching { orchestrator.warmUp() }
            .onFailure { Log.d(TAG, "Chat model warm-up skipped: ${it.message}") }
    }

    companion object {
        private const val TAG = "ModelWarmup"
        private const val WARM_TEXT = "warm up"
    }
}
