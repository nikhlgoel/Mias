package dev.mias.core.data.rag

import dev.mias.core.common.MiasResult
import kotlinx.coroutines.flow.Flow

/** A document in the local knowledge base. */
data class Document(
    val id: String,
    val name: String,
    val addedAt: Long,
    val chunkCount: Int,
)

/**
 * Local-only RAG store: ingest user text, embed it, and retrieve the most
 * relevant passages for a query. Nothing leaves the device.
 */
interface DocumentRepository {

    fun observeDocuments(): Flow<List<Document>>

    fun observeDocumentCount(): Flow<Int>

    /** Chunk, embed, and store [text]. Fails clearly if no embedding model is available. */
    suspend fun ingest(name: String, text: String): MiasResult<Document>

    suspend fun deleteDocument(id: String): MiasResult<Unit>

    /**
     * Retrieve the passages most relevant to [query], formatted for the prompt.
     * Returns an empty string when there are no documents, no embedding model,
     * or nothing relevant — callers can always concatenate it safely.
     */
    suspend fun retrieve(query: String, topK: Int = DEFAULT_TOP_K): String

    /** Whether embeddings can currently be produced. */
    suspend fun isEmbeddingReady(): Boolean

    companion object {
        const val DEFAULT_TOP_K: Int = 4
    }
}
