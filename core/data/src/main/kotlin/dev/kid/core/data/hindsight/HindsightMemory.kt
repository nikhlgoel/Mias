package dev.kid.core.data.hindsight

import dev.kid.core.common.KidResult
import dev.kid.core.common.di.IoDispatcher
import dev.kid.core.common.model.EmbeddingProvider
import dev.kid.core.common.model.TrustLevel
import dev.kid.core.common.runCatchingKid
import dev.kid.core.common.util.cosineSimilarity
import dev.kid.core.common.util.toByteArray
import dev.kid.core.common.util.toFloatArray
import dev.kid.core.data.db.dao.HindsightDao
import dev.kid.core.data.db.entity.MentalModelEntity
import dev.kid.core.data.db.entity.ObservationEntity
import dev.kid.core.data.db.entity.RawFactEntity
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
    ): KidResult<RawFactEntity> = withContext(ioDispatcher) {
        runCatchingKid {
            // Retrieve embedding for the fact before saving
            val embeddingResult = embeddingProvider.getEmbedding(content)
            val embeddingBytes = if (embeddingResult is KidResult.Success) {
                embeddingResult.data.toByteArray()
            } else null

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

    suspend fun query(
        text: String,
        trustLevel: TrustLevel = TrustLevel.OWNER,
    ): KidResult<HindsightContext> = withContext(ioDispatcher) {
        runCatchingKid {
            val queryEmbeddingResult = embeddingProvider.getEmbedding(text)
            
            // If the embedding model is active, do vector search, otherwise fallback to SQL LIKE search.
            if (queryEmbeddingResult is KidResult.Success) {
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
    ): KidResult<Unit> = withContext(ioDispatcher) {
        runCatchingKid {
            val embeddingResult = embeddingProvider.getEmbedding(content)
            val embeddingBytes = if (embeddingResult is KidResult.Success) {
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

    suspend fun storeMentalModel(
        content: String,
        strength: Float,
        observationIds: List<String>,
    ): KidResult<Unit> = withContext(ioDispatcher) {
        runCatchingKid {
            val embeddingResult = embeddingProvider.getEmbedding(content)
            val embeddingBytes = if (embeddingResult is KidResult.Success) {
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
