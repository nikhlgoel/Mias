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

    override suspend fun ingest(name: String, text: String): MiasResult<Document> =
        withContext(ioDispatcher) {
            runCatchingMias {
                val pieces = TextChunker.chunk(text)
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
                val chunks = pieces.mapIndexed { index, piece ->
                    val embedding = when (val r = embeddingProvider.getEmbedding(piece)) {
                        is MiasResult.Success -> r.data.toByteArray()
                        is MiasResult.Error ->
                            // First chunk failing means embeddings aren't really working —
                            // abort cleanly rather than store an un-retrievable document.
                            if (index == 0) throw IllegalStateException(r.message) else null
                    }
                    DocumentChunkEntity(
                        id = UUID.randomUUID().toString(),
                        documentId = docId,
                        ordinal = index,
                        text = piece,
                        embedding = embedding,
                    )
                }

                val document = DocumentEntity(
                    id = docId,
                    name = name.ifBlank { "Untitled document" },
                    addedAt = now,
                    charCount = text.length,
                    chunkCount = chunks.size,
                )
                dao.insertDocumentWithChunks(document, chunks)
                document.toDomain()
            }
        }

    override suspend fun deleteDocument(id: String): MiasResult<Unit> =
        withContext(ioDispatcher) {
            runCatchingMias { dao.deleteDocument(id) }
        }

    override suspend fun retrieve(query: String, topK: Int): String =
        withContext(ioDispatcher) {
            runCatching {
                if (query.isBlank()) return@runCatching ""
                val queryVec = when (val r = embeddingProvider.getEmbedding(query)) {
                    is MiasResult.Success -> r.data
                    is MiasResult.Error -> return@runCatching ""
                }
                val chunks = dao.getAllChunks()
                if (chunks.isEmpty()) return@runCatching ""

                val scored = chunks.mapNotNull { chunk ->
                    val vec = chunk.embedding?.toFloatArray() ?: return@mapNotNull null
                    // Guard against a dimension mismatch if the embedding model changed.
                    if (vec.size != queryVec.size) return@mapNotNull null
                    chunk to vec.cosineSimilarity(queryVec)
                }

                val top = scored
                    .sortedByDescending { it.second }
                    .take(topK)
                    .filter { it.second >= MIN_RELEVANCE }

                if (top.isEmpty()) return@runCatching ""

                buildString {
                    appendLine("## From your documents")
                    top.forEach { (chunk, _) -> appendLine("- ${chunk.text.trim()}") }
                }.trim()
            }.getOrDefault("")
        }

    override suspend fun isEmbeddingReady(): Boolean =
        withContext(ioDispatcher) { embeddingProvider.isReady() }

    private fun DocumentEntity.toDomain() = Document(
        id = id,
        name = name,
        addedAt = addedAt,
        chunkCount = chunkCount,
    )

    companion object {
        /** Minimum cosine score for a chunk to be considered relevant enough to inject. */
        private const val MIN_RELEVANCE = 0.25f
    }
}
