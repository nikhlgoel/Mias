package dev.mias.core.data.db

import androidx.room.Database
import androidx.room.RoomDatabase
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
    version = 1,
    exportSchema = false,
)
abstract class MiasDatabase : RoomDatabase() {
    abstract fun conversationDao(): ConversationDao
    abstract fun hindsightDao(): HindsightDao
}
