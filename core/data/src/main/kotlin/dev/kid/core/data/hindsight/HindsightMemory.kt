package dev.kid.core.data.hindsight

import dev.kid.core.common.KidResult
import dev.kid.core.common.di.IoDispatcher
import dev.kid.core.common.model.TrustLevel
import dev.kid.core.common.runCatchingKid
import dev.kid.core.data.db.dao.HindsightDao
import dev.kid.core.data.db.entity.MentalModelEntity
import dev.kid.core.data.db.entity.ObservationEntity
import dev.kid.core.data.db.entity.RawFactEntity
import dev.kid.core.language.StructuredIntent
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Hindsight Memory — biomimetic episodic knowledge system.
 *
 * Three-tier hierarchy:
 *   Tier 1  Raw Facts     — timestamped ground truth
 *   Tier 2  Observations  — patterns derived from facts
 *   Tier 3  Mental Models — highest-level beliefs
 *
 * Graph-of-Thought: queries traverse facts → observations → models
 * to build temporally-ordered subgraphs for LLM context.
 */
@Singleton
class HindsightMemory @Inject constructor(
    private val dao: HindsightDao,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) {
    /** Store a raw fact from an interaction. */
    suspend fun storeFact(
        content: String,
        sourceUserId: String = "owner",
        conversationId: String? = null,
    ): KidResult<RawFactEntity> = withContext(ioDispatcher) {
        runCatchingKid {
            val fact = RawFactEntity(
                id = UUID.randomUUID().toString(),
                content = content,
                sourceUserId = sourceUserId,
                conversationId = conversationId,
                timestamp = System.currentTimeMillis(),
            )
            dao.insertFact(fact)
            fact
        }
    }

    /** Query the knowledge graph for context relevant to a prompt. */
    suspend fun query(
        text: String,
        trustLevel: TrustLevel = TrustLevel.OWNER,
    ): KidResult<HindsightContext> = withContext(ioDispatcher) {
        runCatchingKid {
            // Tier 1: Find matching facts
            val facts = dao.searchFacts(text, limit = 8)
            // Tier 2: Find related observations
            val observations = dao.searchObservations(text, limit = 5)
            // Tier 3: Find relevant mental models
            val models = dao.searchModels(text, limit = 3)
            // Also include recent facts for recency context
            val recentFacts = dao.getRecentFacts(limit = 5)

            HindsightContext(
                relevantFacts = (facts + recentFacts).distinctBy { it.id },
                observations = observations,
                mentalModels = models,
            )
        }
    }

    /**
     * Query memory using structured user intent signals.
     *
     * The retrieval strategy first uses normalized text, then expands with
     * extracted entities (file names, formats, URLs, app names) to improve
     * match quality without requiring embeddings.
     */
    suspend fun query(
        intent: StructuredIntent,
        trustLevel: TrustLevel = TrustLevel.OWNER,
    ): KidResult<HindsightContext> = withContext(ioDispatcher) {
        runCatchingKid {
            val queryTerms = linkedSetOf<String>()
            if (intent.cleanedText.isNotBlank()) {
                queryTerms += intent.cleanedText
            }
            queryTerms += intent.intentType.value
            queryTerms += intent.entities.values

            val factMatches = mutableListOf<RawFactEntity>()
            val observationMatches = mutableListOf<ObservationEntity>()
            val modelMatches = mutableListOf<MentalModelEntity>()

            queryTerms
                .filter { it.isNotBlank() }
                .take(6)
                .forEach { term ->
                    factMatches += dao.searchFacts(term, limit = 4)
                    observationMatches += dao.searchObservations(term, limit = 3)
                    modelMatches += dao.searchModels(term, limit = 2)
                }

            val recentFacts = dao.getRecentFacts(limit = 5)

            HindsightContext(
                relevantFacts = (factMatches + recentFacts).distinctBy { it.id }.take(12),
                observations = observationMatches.distinctBy { it.id }.sortedByDescending { it.confidence }.take(8),
                mentalModels = modelMatches.distinctBy { it.id }.sortedByDescending { it.strength }.take(5),
            )
        }
    }

    /** Store a new observation derived from facts. */
    suspend fun storeObservation(
        content: String,
        confidence: Float,
        factIds: List<String>,
    ): KidResult<Unit> = withContext(ioDispatcher) {
        runCatchingKid {
            dao.upsertObservation(
                ObservationEntity(
                    id = UUID.randomUUID().toString(),
                    content = content,
                    confidence = confidence,
                    factIds = factIds.joinToString(","),
                    createdAt = System.currentTimeMillis(),
                    updatedAt = System.currentTimeMillis(),
                ),
            )
        }
    }

    /** Store or update a mental model. */
    suspend fun storeMentalModel(
        content: String,
        strength: Float,
        observationIds: List<String>,
    ): KidResult<Unit> = withContext(ioDispatcher) {
        runCatchingKid {
            dao.upsertMentalModel(
                MentalModelEntity(
                    id = UUID.randomUUID().toString(),
                    content = content,
                    observationIds = observationIds.joinToString(","),
                    strength = strength,
                    createdAt = System.currentTimeMillis(),
                    updatedAt = System.currentTimeMillis(),
                ),
            )
        }
    }

    /** Get total fact count (for triggering reflect). */
    suspend fun activeFactCount(): Int = withContext(ioDispatcher) {
        dao.activeFactCount()
    }
}

/** Context assembled from Hindsight Memory for LLM prompt injection. */
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
