package dev.mias.core.common.model

import dev.mias.core.common.MiasResult

/**
 * Interface for providing vector embeddings for given text.
 * Implemented by inference engine and accessed by data layer.
 */
interface EmbeddingProvider {
    suspend fun getEmbedding(text: String): MiasResult<FloatArray>
}
