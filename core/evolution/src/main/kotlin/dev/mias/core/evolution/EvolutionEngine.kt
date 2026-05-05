package dev.mias.core.evolution

import dev.mias.core.common.di.IoDispatcher
import dev.mias.core.common.runCatchingMias
import dev.mias.core.data.ConversationRepository
import dev.mias.core.data.hindsight.HindsightMemory
import dev.mias.core.evolution.model.BehaviorPattern
import dev.mias.core.evolution.model.EvolutionResult
import dev.mias.core.evolution.model.EvolutionSession
import dev.mias.core.evolution.model.EvolutionTask
import dev.mias.core.evolution.model.EvolutionTaskType
import dev.mias.core.evolution.model.PatternCategory
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * EvolutionEngine — The self-improvement coordinator.
 *
 * Runs analysis tasks over conversation history, extracts behavioral patterns,
 * consolidates episodic memories into long-term knowledge, and adjusts
 * personality weights to improve response quality over time.
 *
 * This is the "metacognitive" layer — Mias thinking about how it thinks.
 */
@Singleton
class EvolutionEngine @Inject constructor(
    private val hindsightMemory: HindsightMemory,
    private val conversationRepository: ConversationRepository,
    private val conversationAnalyzer: ConversationAnalyzer,
    private val knowledgeConsolidator: KnowledgeConsolidator,
    private val selfOptimizer: SelfOptimizer,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) {
    private val _currentSession = MutableStateFlow<EvolutionSession?>(null)
    val currentSession: StateFlow<EvolutionSession?> = _currentSession.asStateFlow()

    private val _isRunning = MutableStateFlow(false)
    val isRunning: StateFlow<Boolean> = _isRunning.asStateFlow()

    // ── Public API ──────────────────────────────────────────────────────────

    /** Run a full evolution cycle — consolidate, analyze, optimize. */
    suspend fun runFullCycle(): EvolutionSession = withContext(ioDispatcher) {
        if (_isRunning.value) {
            return@withContext EvolutionSession(
                id = "skipped-${System.currentTimeMillis()}",
            ).also { it.errors.add("Evolution already running") }
        }

        val session = EvolutionSession(id = UUID.randomUUID().toString())
        _currentSession.value = session
        _isRunning.value = true

        val tasks = listOf(
            EvolutionTask(UUID.randomUUID().toString(), EvolutionTaskType.CONSOLIDATE_MEMORIES, 1),
            EvolutionTask(UUID.randomUUID().toString(), EvolutionTaskType.ANALYZE_PATTERNS, 2),
            EvolutionTask(UUID.randomUUID().toString(), EvolutionTaskType.OPTIMIZE_PERSONALITY, 3),
        )

        for (task in tasks.sortedBy { it.priority }) {
            val result = executeTask(task)
            when (result) {
                is EvolutionResult.Failed -> session.errors.add("${task.type}: ${result.error}")
                is EvolutionResult.Skipped -> { /* not an error */ }
                else -> session.completedTasks.add(task.type)
            }
        }

        _isRunning.value = false
        _currentSession.value = session
        session
    }

    /** Run only memory consolidation (lighter operation). */
    suspend fun consolidateMemoriesOnly(): EvolutionResult =
        withContext(ioDispatcher) {
            executeTask(
                EvolutionTask(UUID.randomUUID().toString(), EvolutionTaskType.CONSOLIDATE_MEMORIES, 1),
            )
        }

    /** Analyze patterns only — no memory writes. */
    suspend fun analyzeOnly(): EvolutionResult =
        withContext(ioDispatcher) {
            executeTask(
                EvolutionTask(UUID.randomUUID().toString(), EvolutionTaskType.ANALYZE_PATTERNS, 1),
            )
        }

    // ── Private ─────────────────────────────────────────────────────────────

    private suspend fun executeTask(task: EvolutionTask): EvolutionResult = runCatchingMias {
        when (task.type) {
            EvolutionTaskType.CONSOLIDATE_MEMORIES -> {
                val factCount = hindsightMemory.activeFactCount()
                if (factCount < MIN_FACTS_FOR_CONSOLIDATION) {
                    return@runCatchingMias EvolutionResult.Skipped(
                        "Only $factCount facts — need $MIN_FACTS_FOR_CONSOLIDATION to consolidate",
                    )
                }
                knowledgeConsolidator.consolidate()
            }

            EvolutionTaskType.ANALYZE_PATTERNS -> {
                val conversations = conversationRepository.getConversations().first()
                if (conversations.size < MIN_CONVERSATIONS_FOR_ANALYSIS) {
                    return@runCatchingMias EvolutionResult.Skipped(
                        "Only ${conversations.size} conversations — need $MIN_CONVERSATIONS_FOR_ANALYSIS",
                    )
                }
                conversationAnalyzer.analyze(conversations)
            }

            EvolutionTaskType.OPTIMIZE_PERSONALITY ->
                selfOptimizer.optimize()

            EvolutionTaskType.PRUNE_OLD_FACTS ->
                EvolutionResult.PruningComplete(0) // Future: implement pruning in HindsightDao

            EvolutionTaskType.REFLECT_ON_ERRORS,
            EvolutionTaskType.UPDATE_MODEL_PREFERENCE,
            EvolutionTaskType.INDEX_NEW_KNOWLEDGE,
            -> EvolutionResult.Skipped("Task type not yet implemented: ${task.type}")
        }
    }.let { result ->
        when (result) {
            is dev.mias.core.common.MiasResult.Success -> result.data
            is dev.mias.core.common.MiasResult.Error -> EvolutionResult.Failed(result.message)
        }
    }

    companion object {
        private const val MIN_FACTS_FOR_CONSOLIDATION = 10
        private const val MIN_CONVERSATIONS_FOR_ANALYSIS = 3
    }
}
