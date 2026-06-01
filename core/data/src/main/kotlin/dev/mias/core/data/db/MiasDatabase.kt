package dev.mias.core.data.db

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import dev.mias.core.data.db.dao.ConversationDao
import dev.mias.core.data.db.dao.HindsightDao
import dev.mias.core.data.db.entity.ConversationEntity
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
    ],
    version = 3,
    exportSchema = false,
)
abstract class MiasDatabase : RoomDatabase() {
    abstract fun conversationDao(): ConversationDao
    abstract fun hindsightDao(): HindsightDao

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
    }
}
