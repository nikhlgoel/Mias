package dev.kid.core.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import dev.kid.core.data.db.entity.HindsightUserEntity
import dev.kid.core.data.db.entity.MentalModelEntity
import dev.kid.core.data.db.entity.ObservationEntity
import dev.kid.core.data.db.entity.RawFactEntity

@Dao
interface HindsightDao {

    // ── Raw Facts ────────────────────────────────────────────────────

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertFact(fact: RawFactEntity)

    @Query("SELECT * FROM raw_facts WHERE isDeprecated = 0 ORDER BY timestamp DESC LIMIT :limit")
    suspend fun getRecentFacts(limit: Int = 20): List<RawFactEntity>

    @Query("SELECT * FROM raw_facts WHERE isDeprecated = 0")
    suspend fun getAllActiveFacts(): List<RawFactEntity>

    @Query(
        "SELECT * FROM raw_facts WHERE isDeprecated = 0 AND content LIKE '%' || :query || '%' " +
            "ORDER BY timestamp DESC LIMIT :limit",
    )
    suspend fun searchFacts(query: String, limit: Int = 10): List<RawFactEntity>

    @Query("UPDATE raw_facts SET isDeprecated = 1 WHERE id = :factId")
    suspend fun deprecateFact(factId: String)

    @Query("SELECT COUNT(*) FROM raw_facts WHERE isDeprecated = 0")
    suspend fun activeFactCount(): Int

    @Query(
        "SELECT * FROM raw_facts WHERE isDeprecated = 0 AND sourceUserId = :userId " +
            "ORDER BY timestamp DESC LIMIT :limit",
    )
    suspend fun getFactsByUser(userId: String, limit: Int = 20): List<RawFactEntity>

    // ── Observations ─────────────────────────────────────────────────

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertObservation(observation: ObservationEntity)

    @Query("SELECT * FROM observations ORDER BY updatedAt DESC LIMIT :limit")
    suspend fun getRecentObservations(limit: Int = 10): List<ObservationEntity>

    @Query("SELECT * FROM observations")
    suspend fun getAllObservations(): List<ObservationEntity>

    @Query(
        "SELECT * FROM observations WHERE content LIKE '%' || :query || '%' " +
            "ORDER BY confidence DESC LIMIT :limit",
    )
    suspend fun searchObservations(query: String, limit: Int = 5): List<ObservationEntity>

    @Query("SELECT * FROM observations WHERE id = :id")
    suspend fun getObservationById(id: String): ObservationEntity?

    // ── Mental Models ────────────────────────────────────────────────

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertMentalModel(model: MentalModelEntity)

    @Query("SELECT * FROM mental_models ORDER BY strength DESC LIMIT :limit")
    suspend fun getStrongestModels(limit: Int = 5): List<MentalModelEntity>

    @Query("SELECT * FROM mental_models")
    suspend fun getAllMentalModels(): List<MentalModelEntity>

    @Query(
        "SELECT * FROM mental_models WHERE content LIKE '%' || :query || '%' " +
            "ORDER BY strength DESC LIMIT :limit",
    )
    suspend fun searchModels(query: String, limit: Int = 3): List<MentalModelEntity>

    @Query("SELECT * FROM mental_models WHERE id = :id")
    suspend fun getModelById(id: String): MentalModelEntity?

    // ── Users ────────────────────────────────────────────────────────

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertUser(user: HindsightUserEntity)

    @Query("SELECT * FROM hindsight_users")
    suspend fun getAllUsers(): List<HindsightUserEntity>

    @Query("SELECT * FROM hindsight_users WHERE id = :id")
    suspend fun getUserById(id: String): HindsightUserEntity?
}
