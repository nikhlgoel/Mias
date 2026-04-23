package dev.kid.core.common.model

import dev.kid.core.common.KidResult

/**
 * Interface for providing vector embeddings for given text.
 * Implemented by inference engine and accessed by data layer.
 */
interface EmbeddingProvider {
    suspend fun getEmbedding(text: String): KidResult<FloatArray>
}
