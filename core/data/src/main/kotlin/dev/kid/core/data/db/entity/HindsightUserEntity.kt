package dev.kid.core.data.db.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/** User identity in Circle of Trust memory system. */
@Entity(tableName = "hindsight_users")
data class HindsightUserEntity(
    @PrimaryKey val id: String,
    val alias: String,
    val trustLevel: String,
    val relationWeight: Float,
    val createdAt: Long,
)
