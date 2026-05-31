package dev.mias.core.modelhub.db

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [
        InstalledModelEntity::class,
        DownloadQueueEntity::class,
        RoleAssignmentEntity::class,
    ],
    version = 3,
    exportSchema = false,
)
abstract class ModelHubDatabase : RoomDatabase() {
    abstract fun modelDao(): ModelDao

    companion object {

        /**
         * v2 → v3: introduce the `role_assignments` table.
         *
         * Replaces the legacy single-valued `installed_models.assignedRole`
         * column. The legacy column is left in place (still in the entity)
         * so existing rows aren't disturbed by the migration; the schema
         * just stops being the source of truth for routing. Existing
         * non-null assignments are copied into the new table as
         * auto-selected (isUserPinned = 0). A future migration may drop
         * the legacy column once we're confident no caller reads it.
         */
        val MIGRATION_2_3: Migration = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `role_assignments` (
                        `role` TEXT NOT NULL,
                        `modelId` TEXT NOT NULL,
                        `isUserPinned` INTEGER NOT NULL,
                        `assignedAt` INTEGER NOT NULL,
                        PRIMARY KEY(`role`)
                    )
                    """.trimIndent(),
                )

                // Copy any existing single-valued assignments forward so
                // users with previously-assigned models don't have to
                // re-run auto-assign on first launch.
                val now = System.currentTimeMillis()
                db.execSQL(
                    """
                    INSERT OR IGNORE INTO role_assignments (role, modelId, isUserPinned, assignedAt)
                    SELECT assignedRole, id, 0, $now
                    FROM installed_models
                    WHERE assignedRole IS NOT NULL
                    """.trimIndent(),
                )
            }
        }
    }
}
