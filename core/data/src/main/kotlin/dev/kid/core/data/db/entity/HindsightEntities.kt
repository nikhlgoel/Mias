package dev.kid.core.data.db.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/** Hindsight Tier 1: Raw timestamped facts. */
@Entity(tableName = "raw_facts")
data class RawFactEntity(
    @PrimaryKey val id: String,
    val content: String,
    val sourceUserId: String,
    val conversationId: String?,
    val timestamp: Long,
    val isDeprecated: Boolean = false,
)

/** Hindsight Tier 2: Observations derived from raw facts. */
@Entity(tableName = "observations")
data class ObservationEntity(
    @PrimaryKey val id: String,
    val content: String,
    val confidence: Float,
    /** Comma-separated fact IDs that contributed to this observation. */
    val factIds: String,
    val createdAt: Long,
    val updatedAt: Long,
)

/** Hindsight Tier 3: Mental models — highest-level abstractions. */
@Entity(tableName = "mental_models")
data class MentalModelEntity(
    @PrimaryKey val id: String,
    val content: String,
    /** Comma-separated observation IDs. */
    val observationIds: String,
    val strength: Float,
    val createdAt: Long,
    val updatedAt: Long,
    val version: Int = 1,
)
