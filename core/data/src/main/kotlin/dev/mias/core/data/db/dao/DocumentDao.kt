package dev.mias.core.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import dev.mias.core.data.db.entity.DocumentChunkEntity
import dev.mias.core.data.db.entity.DocumentEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface DocumentDao {

    @Query("SELECT * FROM documents ORDER BY addedAt DESC")
    fun observeDocuments(): Flow<List<DocumentEntity>>

    @Query("SELECT COUNT(*) FROM documents")
    fun observeDocumentCount(): Flow<Int>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertDocument(document: DocumentEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertChunks(chunks: List<DocumentChunkEntity>)

    @Query("DELETE FROM documents WHERE id = :id")
    suspend fun deleteDocument(id: String)

    /** All chunks across every document — used for the brute-force vector scan. */
    @Query("SELECT * FROM document_chunks")
    suspend fun getAllChunks(): List<DocumentChunkEntity>

    @Transaction
    suspend fun insertDocumentWithChunks(
        document: DocumentEntity,
        chunks: List<DocumentChunkEntity>,
    ) {
        insertDocument(document)
        insertChunks(chunks)
    }
}
