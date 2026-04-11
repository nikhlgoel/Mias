package dev.kid.core.data.db

import androidx.room.Database
import androidx.room.RoomDatabase
import dev.kid.core.data.db.dao.ConversationDao
import dev.kid.core.data.db.dao.HindsightDao
import dev.kid.core.data.db.entity.ConversationEntity
import dev.kid.core.data.db.entity.HindsightUserEntity
import dev.kid.core.data.db.entity.MentalModelEntity
import dev.kid.core.data.db.entity.MessageEntity
import dev.kid.core.data.db.entity.ObservationEntity
import dev.kid.core.data.db.entity.RawFactEntity

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
abstract class KidDatabase : RoomDatabase() {
    abstract fun conversationDao(): ConversationDao
    abstract fun hindsightDao(): HindsightDao
}
