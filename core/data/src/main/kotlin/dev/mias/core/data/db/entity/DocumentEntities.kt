package dev.mias.core.data.db.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * A user-provided document added to the local knowledge base for RAG.
 * The original text isn't kept whole — it lives split across [DocumentChunkEntity].
 */
@Entity(tableName = "documents")
data class DocumentEntity(
    @PrimaryKey val id: String,
    val name: String,
    val addedAt: Long,
    val charCount: Int,
    val chunkCount: Int,
    /**
     * Scope: null = global (available to every chat); a conversation id = only
     * retrievable within that chat. Documents added from the Knowledge screen
     * are global; documents attached from a specific chat are scoped to it.
     */
    val conversationId: String? = null,
)

/**
 * One retrievable slice of a document, with its embedding stored as a packed
 * little-endian float blob (see VectorUtils). Chunks cascade-delete with their
 * parent document.
 */
@Entity(
    tableName = "document_chunks",
    foreignKeys = [
        ForeignKey(
            entity = DocumentEntity::class,
            parentColumns = ["id"],
            childColumns = ["documentId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("documentId")],
)
data class DocumentChunkEntity(
    @PrimaryKey val id: String,
    val documentId: String,
    val ordinal: Int,
    val text: String,
    val embedding: ByteArray? = null,
)
