package dev.mias.core.common.model

import dev.mias.core.common.MiasResult

/**
 * Interface for providing vector embeddings for given text.
 * Implemented by inference engine and accessed by data layer.
 */
interface EmbeddingProvider {
    suspend fun getEmbedding(text: String): MiasResult<FloatArray>

    /**
     * Whether embeddings can currently be produced (e.g. an embedding model is
     * installed and loadable). Default true for simple/direct implementations;
     * overridden by providers that lazily load a model.
     */
    suspend fun isReady(): Boolean = true
}
