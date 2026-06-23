package dev.mias.core.data.rag

import dev.mias.core.common.MiasResult
import dev.mias.core.common.di.IoDispatcher
import dev.mias.core.common.model.EmbeddingProvider
import dev.mias.core.common.runCatchingMias
import dev.mias.core.common.util.cosineSimilarity
import dev.mias.core.common.util.toByteArray
import dev.mias.core.common.util.toFloatArray
import dev.mias.core.data.db.dao.DocumentDao
import dev.mias.core.data.db.entity.DocumentChunkEntity
import dev.mias.core.data.db.entity.DocumentEntity
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DocumentRepositoryImpl @Inject constructor(
    private val dao: DocumentDao,
    private val embeddingProvider: EmbeddingProvider,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) : DocumentRepository {

    override fun observeDocuments(): Flow<List<Document>> =
        dao.observeDocuments()
            .map { list -> list.map { it.toDomain() } }
            .flowOn(ioDispatcher)

    override fun observeDocumentCount(): Flow<Int> = dao.observeDocumentCount().flowOn(ioDispatcher)

    override suspend fun ingest(
        name: String,
        text: String,
        conversationId: String?,
    ): MiasResult<Document> =
        withContext(ioDispatcher) {
            runCatchingMias {
                // Bound the work up front: each chunk costs one on-device
                // embedding pass, so a huge file would otherwise freeze ingest
                // for many minutes. The first ~500K chars of a document carry
                // its substance; beyond that we truncate rather than refuse.
                val bounded = if (text.length > MAX_INGEST_CHARS) {
                    text.take(MAX_INGEST_CHARS)
                } else {
                    text
                }
                val pieces = TextChunker.chunk(bounded)
                if (pieces.isEmpty()) {
                    throw IllegalArgumentException("This file has no readable text to add.")
                }
                if (!embeddingProvider.isReady()) {
                    throw IllegalStateException(
                        "Add an embedding model (e.g. Nomic Embed) in Models before adding documents.",
                    )
                }

                val docId = UUID.randomUUID().toString()
                val now = System.currentTimeMillis()
                var failedEmbeddings = 0
                val chunks = pieces.mapIndexed { index, piece ->
                    // Stop promptly if the caller (e.g. a closing screen)
                    // cancelled — don't grind through hundreds of chunks.
                    currentCoroutineContext().ensureActive()

                    // One retry per chunk: a single transient embedding failure
                    // shouldn't leave a permanent hole in the document.
                    var embedding: ByteArray? = null
                    var lastError: String? = null
                    for (attempt in 0 until EMBED_ATTEMPTS_PER_CHUNK) {
                        when (val r = embeddingProvider.getEmbedding(piece)) {
                            is MiasResult.Success -> {
                                embedding = r.data.toByteArray()
                                break
                            }
                            is MiasResult.Error -> lastError = r.message
                        }
                    }
                    if (embedding == null) {
                        failedEmbeddings++
                        // First chunk failing twice means embeddings aren't
                        // really working — abort cleanly rather than store an
                        // un-retrievable document.
                        if (index == 0) throw IllegalStateException(lastError ?: "Embedding failed.")
                    }
                    DocumentChunkEntity(
                        id = UUID.randomUUID().toString(),
                        documentId = docId,
                        ordinal = index,
                        text = piece,
                        embedding = embedding,
                    )
                }

                // A document where most chunks have no embedding is mostly
                // invisible to retrieval — surface that instead of storing it.
                if (failedEmbeddings > pieces.size / 2) {
                    throw IllegalStateException(
                        "Embedding failed for most of this document — please try adding it again.",
                    )
                }

                val document = DocumentEntity(
                    id = docId,
                    name = name.ifBlank { "Untitled document" },
                    addedAt = now,
                    charCount = bounded.length,
                    chunkCount = chunks.size,
                    conversationId = conversationId,
                )
                dao.insertDocumentWithChunks(document, chunks)
                document.toDomain()
            }
        }

    override suspend fun deleteDocument(id: String): MiasResult<Unit> =
        withContext(ioDispatcher) {
            runCatchingMias { dao.deleteDocument(id) }
        }

    override suspend fun retrieve(
        query: String,
        conversationId: String?,
        topK: Int,
    ): RetrievedContext =
        withContext(ioDispatcher) {
            runCatching {
                if (query.isBlank()) return@runCatching RetrievedContext.EMPTY
                val queryVec = when (val r = embeddingProvider.getEmbedding(query)) {
                    is MiasResult.Success -> r.data
                    is MiasResult.Error -> return@runCatching RetrievedContext.EMPTY
                }

                // Scope: global documents (conversationId == null) plus any
                // belonging to this conversation.
                val docs = dao.getAllDocuments().associateBy { it.id }
                val inScopeDocIds = docs.values
                    .filter { it.conversationId == null || it.conversationId == conversationId }
                    .map { it.id }
                if (inScopeDocIds.isEmpty()) return@runCatching RetrievedContext.EMPTY

                // Load only the in-scope chunks — not every BLOB in the store.
                val chunks = dao.getChunksForDocuments(inScopeDocIds)
                if (chunks.isEmpty()) return@runCatching RetrievedContext.EMPTY

                val scored = chunks.mapNotNull { chunk ->
                    val vec = chunk.embedding?.toFloatArray() ?: return@mapNotNull null
                    if (vec.size != queryVec.size) return@mapNotNull null
                    ScoredChunk(chunk, vec, vec.cosineSimilarity(queryVec))
                }

                // Greedy selection with near-duplicate suppression: overlapping
                // chunks from the same document region score almost identically
                // and would otherwise crowd out genuinely distinct passages.
                // A candidate too similar to an already-selected chunk is
                // skipped, so the topK slots carry diverse evidence.
                val ranked = scored
                    .filter { it.score >= MIN_RELEVANCE }
                    .sortedByDescending { it.score }
                val top = mutableListOf<ScoredChunk>()
                for (candidate in ranked) {
                    if (top.size >= topK) break
                    val isNearDuplicate = top.any {
                        it.vector.cosineSimilarity(candidate.vector) >= DUPLICATE_SIMILARITY
                    }
                    if (!isNearDuplicate) top.add(candidate)
                }

                if (top.isEmpty()) return@runCatching RetrievedContext.EMPTY

                // Attribute each passage to its document so the model can say
                // *where* a fact came from, not just that it came from "docs".
                val promptText = buildString {
                    appendLine("## From your documents")
                    top.forEach { sc ->
                        val docName = docs[sc.chunk.documentId]?.name
                        if (docName != null) {
                            appendLine("- [$docName] ${sc.chunk.text.trim()}")
                        } else {
                            appendLine("- ${sc.chunk.text.trim()}")
                        }
                    }
                }.trim()

                val sources = top
                    .mapNotNull { sc -> docs[sc.chunk.documentId]?.name }
                    .distinct()

                RetrievedContext(promptText = promptText, sources = sources)
            }.getOrDefault(RetrievedContext.EMPTY)
        }

    override suspend fun isEmbeddingReady(): Boolean =
        withContext(ioDispatcher) { embeddingProvider.isReady() }

    private fun DocumentEntity.toDomain() = Document(
        id = id,
        name = name,
        addedAt = addedAt,
        chunkCount = chunkCount,
        conversationId = conversationId,
    )

    /** A chunk with its decoded embedding and query similarity, for selection. */
    private class ScoredChunk(
        val chunk: DocumentChunkEntity,
        val vector: FloatArray,
        val score: Float,
    )

    companion object {
        /** Minimum cosine score for a chunk to be considered relevant enough to inject. */
        private const val MIN_RELEVANCE = 0.25f

        /** Chunks at least this similar to one already selected are redundant. */
        private const val DUPLICATE_SIMILARITY = 0.93f

        /** Ingest length bound — substance over bulk; ~600 chunks' worth. */
        private const val MAX_INGEST_CHARS = 500_000

        /** Embedding tries per chunk (1 retry for transient failures). */
        private const val EMBED_ATTEMPTS_PER_CHUNK = 2
    }
}
