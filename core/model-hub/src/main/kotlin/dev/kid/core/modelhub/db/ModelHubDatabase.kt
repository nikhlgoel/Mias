package dev.kid.core.modelhub.db

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(
    entities = [
        InstalledModelEntity::class,
        DownloadQueueEntity::class,
    ],
    version = 1,
    exportSchema = false,
)
abstract class ModelHubDatabase : RoomDatabase() {
    abstract fun modelDao(): ModelDao
}
