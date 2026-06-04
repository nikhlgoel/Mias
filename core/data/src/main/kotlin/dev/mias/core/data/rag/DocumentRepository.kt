package dev.mias.core.data.rag

import dev.mias.core.common.MiasResult
import kotlinx.coroutines.flow.Flow

/** A document in the local knowledge base. */
data class Document(
    val id: String,
    val name: String,
    val addedAt: Long,
    val chunkCount: Int,
    /** Null = global; otherwise the chat this document is scoped to. */
    val conversationId: String? = null,
)

/** Retrieved RAG context plus the document names it was drawn from (for citations). */
data class RetrievedContext(
    val promptText: String,
    val sources: List<String>,
) {
    companion object {
        val EMPTY = RetrievedContext("", emptyList())
    }
}

/**
 * Local-only RAG store: ingest user text, embed it, and retrieve the most
 * relevant passages for a query. Nothing leaves the device.
 */
interface DocumentRepository {

    fun observeDocuments(): Flow<List<Document>>

    fun observeDocumentCount(): Flow<Int>

    /**
     * Chunk, embed, and store [text]. Fails clearly if no embedding model is
     * available. [conversationId] scopes the document to a single chat; null
     * makes it global (available to every chat).
     */
    suspend fun ingest(
        name: String,
        text: String,
        conversationId: String? = null,
    ): MiasResult<Document>

    suspend fun deleteDocument(id: String): MiasResult<Unit>

    /**
     * Retrieve the passages most relevant to [query] from global documents plus
     * any scoped to [conversationId], with their source names for citations.
     * Returns [RetrievedContext.EMPTY] when nothing applies — always safe to use.
     */
    suspend fun retrieve(
        query: String,
        conversationId: String? = null,
        topK: Int = DEFAULT_TOP_K,
    ): RetrievedContext

    /** Whether embeddings can currently be produced. */
    suspend fun isEmbeddingReady(): Boolean

    companion object {
        const val DEFAULT_TOP_K: Int = 4
    }
}
