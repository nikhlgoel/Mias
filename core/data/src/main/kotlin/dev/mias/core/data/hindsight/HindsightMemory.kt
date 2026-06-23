package dev.mias.core.data.hindsight

import dev.mias.core.common.MiasResult
import dev.mias.core.common.di.IoDispatcher
import dev.mias.core.common.model.EmbeddingProvider
import dev.mias.core.common.model.TrustLevel
import dev.mias.core.common.runCatchingMias
import dev.mias.core.common.util.cosineSimilarity
import dev.mias.core.common.util.toByteArray
import dev.mias.core.common.util.toFloatArray
import dev.mias.core.data.db.dao.HindsightDao
import dev.mias.core.data.db.entity.MentalModelEntity
import dev.mias.core.data.db.entity.ObservationEntity
import dev.mias.core.data.db.entity.RawFactEntity
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class HindsightMemory @Inject constructor(
    private val dao: HindsightDao,
    private val embeddingProvider: EmbeddingProvider,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) {
    suspend fun storeFact(
        content: String,
        sourceUserId: String = "owner",
        conversationId: String? = null,
    ): MiasResult<RawFactEntity> = withContext(ioDispatcher) {
        runCatchingMias {
            // Retrieve embedding for the fact before saving
            val embeddingResult = embeddingProvider.getEmbedding(content)
            val embedding = if (embeddingResult is MiasResult.Success) {
                embeddingResult.data
            } else null
            val embeddingBytes = embedding?.toByteArray()

            // Dedup: skip insert if a near-duplicate already exists (cosine > 0.92)
            if (embedding != null && isDuplicate(embedding)) {
                // Return a synthetic entity to signal dedup without inserting
                return@runCatchingMias RawFactEntity(
                    id = "dedup-skipped",
                    content = content,
                    sourceUserId = sourceUserId,
                    conversationId = conversationId,
                    timestamp = System.currentTimeMillis(),
                    embedding = embeddingBytes,
                )
            }

            val fact = RawFactEntity(
                id = UUID.randomUUID().toString(),
                content = content,
                sourceUserId = sourceUserId,
                conversationId = conversationId,
                timestamp = System.currentTimeMillis(),
                embedding = embeddingBytes,
            )
            dao.insertFact(fact)
            fact
        }
    }

    /**
     * Check if a new embedding is a near-duplicate of any recent fact.
     * Uses cosine similarity with a threshold of 0.92 against the last 50 facts.
     */
    private suspend fun isDuplicate(newEmbedding: FloatArray): Boolean {
        val recentFacts = dao.getRecentFactsWithEmbeddings(limit = 50)
        return recentFacts.any { fact ->
            val existingEmbedding = fact.embedding?.toFloatArray() ?: return@any false
            existingEmbedding.cosineSimilarity(newEmbedding) > DEDUP_SIMILARITY_THRESHOLD
        }
    }

    suspend fun query(
        text: String,
        trustLevel: TrustLevel = TrustLevel.OWNER,
    ): MiasResult<HindsightContext> = withContext(ioDispatcher) {
        runCatchingMias {
            val queryEmbeddingResult = embeddingProvider.getEmbedding(text)
            
            // If the embedding model is active, do vector search, otherwise fallback to SQL LIKE search.
            if (queryEmbeddingResult is MiasResult.Success) {
                val queryVec = queryEmbeddingResult.data
                
                // Tier 1: Vector Search on facts
                val allFacts = dao.getAllActiveFacts()
                val sortedFacts = allFacts.mapNotNull { fact -> 
                    fact.embedding?.toFloatArray()?.let { vec -> fact to vec.cosineSimilarity(queryVec) }
                }.sortedByDescending { it.second }.take(8).map { it.first }

                // Tier 2: Vector Search on observations
                val allObs = dao.getAllObservations()
                val sortedObs = allObs.mapNotNull { obs ->
                    obs.embedding?.toFloatArray()?.let { vec -> obs to vec.cosineSimilarity(queryVec) }
                }.sortedByDescending { it.second }.take(5).map { it.first }

                // Tier 3: Vector Search on mental models
                val allModels = dao.getAllMentalModels()
                val sortedModels = allModels.mapNotNull { mod ->
                    mod.embedding?.toFloatArray()?.let { vec -> mod to vec.cosineSimilarity(queryVec) }
                }.sortedByDescending { it.second }.take(3).map { it.first }

                val recentFacts = dao.getRecentFacts(limit = 5)

                HindsightContext(
                    relevantFacts = (sortedFacts + recentFacts).distinctBy { it.id },
                    observations = sortedObs,
                    mentalModels = sortedModels,
                )
            } else {
                // Fallback to text matching
                val facts = dao.searchFacts(text, limit = 8)
                val observations = dao.searchObservations(text, limit = 5)
                val models = dao.searchModels(text, limit = 3)
                val recentFacts = dao.getRecentFacts(limit = 5)

                HindsightContext(
                    relevantFacts = (facts + recentFacts).distinctBy { it.id },
                    observations = observations,
                    mentalModels = models,
                )
            }
        }
    }

    suspend fun storeObservation(
        content: String,
        confidence: Float,
        factIds: List<String>,
    ): MiasResult<Unit> = withContext(ioDispatcher) {
        runCatchingMias {
            val embeddingResult = embeddingProvider.getEmbedding(content)
            val embeddingBytes = if (embeddingResult is MiasResult.Success) {
                embeddingResult.data.toByteArray()
            } else null

            dao.upsertObservation(
                ObservationEntity(
                    id = UUID.randomUUID().toString(),
                    content = content,
                    confidence = confidence,
                    factIds = factIds.joinToString(","),
                    createdAt = System.currentTimeMillis(),
                    updatedAt = System.currentTimeMillis(),
                    embedding = embeddingBytes,
                ),
            )
        }
    }

    /**
     * Persist one distilled user memory ("User's name is Nikhil", "User is
     * building an Android app called Mias") so future fresh conversations can
     * recall it.
     *
     * Stored as a mental model: that table feeds the "What I Know About You"
     * section of [HindsightContext.toPromptString], which the chat pipeline
     * already injects every turn — so recall needs no extra wiring.
     *
     * Dedup: a memory whose embedding is near-identical (≥ [DEDUP_SIMILARITY_THRESHOLD])
     * to an existing one is skipped — telling Mias your name twice must not
     * create two entries. Returns true when a new memory was stored.
     */
    suspend fun storeUserMemory(
        content: String,
        confidence: Float = DEFAULT_MEMORY_CONFIDENCE,
    ): MiasResult<Boolean> = withContext(ioDispatcher) {
        runCatchingMias {
            val trimmed = content.trim()
            if (trimmed.isBlank()) return@runCatchingMias false

            val embedding = when (val r = embeddingProvider.getEmbedding(trimmed)) {
                is MiasResult.Success -> r.data
                // No embedding model → store anyway (text recall still finds it);
                // dedup falls back to exact text matching below.
                is MiasResult.Error -> null
            }

            val existing = dao.getAllMentalModels()
            val duplicate = existing.any { model ->
                val sameText = model.content.equals(trimmed, ignoreCase = true)
                val sameMeaning = embedding != null &&
                    model.embedding?.toFloatArray()
                        ?.takeIf { it.size == embedding.size }
                        ?.cosineSimilarity(embedding)
                        ?.let { it >= DEDUP_SIMILARITY_THRESHOLD } == true
                sameText || sameMeaning
            }
            if (duplicate) return@runCatchingMias false

            dao.upsertMentalModel(
                MentalModelEntity(
                    id = UUID.randomUUID().toString(),
                    content = trimmed,
                    observationIds = "",
                    strength = confidence,
                    createdAt = System.currentTimeMillis(),
                    updatedAt = System.currentTimeMillis(),
                    embedding = embedding?.toByteArray(),
                ),
            )
            true
        }
    }

    suspend fun storeMentalModel(
        content: String,
        strength: Float,
        observationIds: List<String>,
    ): MiasResult<Unit> = withContext(ioDispatcher) {
        runCatchingMias {
            val embeddingResult = embeddingProvider.getEmbedding(content)
            val embeddingBytes = if (embeddingResult is MiasResult.Success) {
                embeddingResult.data.toByteArray()
            } else null

            dao.upsertMentalModel(
                MentalModelEntity(
                    id = UUID.randomUUID().toString(),
                    content = content,
                    observationIds = observationIds.joinToString(","),
                    strength = strength,
                    createdAt = System.currentTimeMillis(),
                    updatedAt = System.currentTimeMillis(),
                    embedding = embeddingBytes,
                ),
            )
        }
    }

    suspend fun activeFactCount(): Int = withContext(ioDispatcher) {
        dao.activeFactCount()
    }

    companion object {
        /** Cosine similarity threshold above which a new fact is considered a duplicate. */
        private const val DEDUP_SIMILARITY_THRESHOLD = 0.92f

        /** Default confidence for distilled user memories. */
        private const val DEFAULT_MEMORY_CONFIDENCE = 0.8f
    }
}

data class HindsightContext(
    val relevantFacts: List<RawFactEntity>,
    val observations: List<ObservationEntity>,
    val mentalModels: List<MentalModelEntity>,
) {
    fun toPromptString(): String = buildString {
        if (mentalModels.isNotEmpty()) {
            appendLine("## What I Know About You")
            mentalModels.forEach { appendLine("- ${it.content} (confidence: ${it.strength})") }
        }
        if (observations.isNotEmpty()) {
            appendLine("## Recent Observations")
            observations.forEach { appendLine("- ${it.content}") }
        }
        if (relevantFacts.isNotEmpty()) {
            appendLine("## Relevant Memories")
            relevantFacts.take(8).forEach { appendLine("- ${it.content}") }
        }
    }

    val isEmpty: Boolean get() = relevantFacts.isEmpty() && observations.isEmpty() && mentalModels.isEmpty()
}
