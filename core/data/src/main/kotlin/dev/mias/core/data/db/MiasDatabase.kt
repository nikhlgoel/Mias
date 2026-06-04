package dev.mias.core.data.db

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import dev.mias.core.data.db.dao.ConversationDao
import dev.mias.core.data.db.dao.DocumentDao
import dev.mias.core.data.db.dao.HindsightDao
import dev.mias.core.data.db.entity.ConversationEntity
import dev.mias.core.data.db.entity.DocumentChunkEntity
import dev.mias.core.data.db.entity.DocumentEntity
import dev.mias.core.data.db.entity.HindsightUserEntity
import dev.mias.core.data.db.entity.MentalModelEntity
import dev.mias.core.data.db.entity.MessageEntity
import dev.mias.core.data.db.entity.ObservationEntity
import dev.mias.core.data.db.entity.RawFactEntity

@Database(
    entities = [
        ConversationEntity::class,
        MessageEntity::class,
        RawFactEntity::class,
        ObservationEntity::class,
        MentalModelEntity::class,
        HindsightUserEntity::class,
        DocumentEntity::class,
        DocumentChunkEntity::class,
    ],
    version = 4,
    exportSchema = false,
)
abstract class MiasDatabase : RoomDatabase() {
    abstract fun conversationDao(): ConversationDao
    abstract fun hindsightDao(): HindsightDao
    abstract fun documentDao(): DocumentDao

    companion object {
        /**
         * v1 → v2: add `messages.imagePath` for attached images sent through
         * the vision-in-chat flow. Existing rows get NULL.
         */
        val MIGRATION_1_2: Migration = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE messages ADD COLUMN imagePath TEXT")
            }
        }

        /**
         * v2 → v3: add `messages.reasoningText` so reasoning/JSON is stored
         * separately from the clean conversational `content`. Existing rows
         * get NULL (their `content` is already what was shown).
         */
        val MIGRATION_2_3: Migration = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE messages ADD COLUMN reasoningText TEXT")
            }
        }

        /**
         * v3 → v4: add the RAG knowledge base — `documents` and their
         * `document_chunks` (chunk text + embedding blob). Chunks cascade-delete
         * with their parent document. Existing chats/memory are untouched.
         */
        val MIGRATION_3_4: Migration = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS documents (
                        id TEXT NOT NULL PRIMARY KEY,
                        name TEXT NOT NULL,
                        addedAt INTEGER NOT NULL,
                        charCount INTEGER NOT NULL,
                        chunkCount INTEGER NOT NULL
                    )
                    """.trimIndent(),
                )
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS document_chunks (
                        id TEXT NOT NULL PRIMARY KEY,
                        documentId TEXT NOT NULL,
                        ordinal INTEGER NOT NULL,
                        text TEXT NOT NULL,
                        embedding BLOB,
                        FOREIGN KEY(documentId) REFERENCES documents(id) ON DELETE CASCADE
                    )
                    """.trimIndent(),
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS index_document_chunks_documentId " +
                        "ON document_chunks(documentId)",
                )
            }
        }
    }
}
