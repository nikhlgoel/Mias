package dev.kid.core.evolution.model

import kotlinx.serialization.Serializable

// ─── Evolution Task Types ───────────────────────────────────────────────────

enum class EvolutionTaskType {
    CONSOLIDATE_MEMORIES,   // Tier 1→2→3 in HindsightMemory
    ANALYZE_PATTERNS,       // Detect recurring topics / emotional arcs
    OPTIMIZE_PERSONALITY,   // Adjust soul trait weights based on feedback
    PRUNE_OLD_FACTS,        // Remove stale data to keep memory lean
    REFLECT_ON_ERRORS,      // Review low-confidence or failed responses
    UPDATE_MODEL_PREFERENCE,// Re-evaluate which models work best for which tasks
    INDEX_NEW_KNOWLEDGE,    // Summarize recent web fetches into long-term knowledge
}

data class EvolutionTask(
    val id: String,
    val type: EvolutionTaskType,
    val priority: Int = 5,          // 1 = urgent, 10 = background
    val triggeredBy: String = "scheduler",
    val createdAt: Long = System.currentTimeMillis(),
)

// ─── Evolution Results ───────────────────────────────────────────────────────

sealed interface EvolutionResult {
    data class Consolidated(
        val factsProcessed: Int,
        val observationsCreated: Int,
        val mentalModelsUpdated: Int,
    ) : EvolutionResult

    data class PatternsFound(
        val patterns: List<BehaviorPattern>,
    ) : EvolutionResult

    data class PersonalityAdjusted(
        val adjustments: Map<String, Float>,
        val reason: String,
    ) : EvolutionResult

    data class PruningComplete(
        val itemsRemoved: Int,
    ) : EvolutionResult

    data class Skipped(val reason: String) : EvolutionResult
    data class Failed(val error: String) : EvolutionResult
}

// ─── Behavior Pattern ────────────────────────────────────────────────────────

@Serializable
data class BehaviorPattern(
    val id: String,
    val description: String,
    val frequency: Int,
    val confidence: Float,           // 0.0–1.0
    val category: PatternCategory,
    val firstObserved: Long,
    val lastObserved: Long,
)

enum class PatternCategory {
    TOPIC_AFFINITY,         // User frequently asks about certain subjects
    EMOTIONAL_STATE,        // Time-of-day emotional patterns
    TASK_TYPE,              // Code vs chat vs research vs creative
    RESPONSE_PREFERENCE,    // Short/long answers, formal/casual tone
    ERROR_PATTERN,          // Common misunderstandings to avoid
    WORKFLOW,               // User's typical task sequences
}

// ─── Learning Config ──────────────────────────────────────────────────────────

data class LearningConfig(
    val enableBackgroundEvolution: Boolean = false,
    val consolidationIntervalHours: Int = 6,
    val patternIntervalHours: Int = 12,
    val pruningIntervalDays: Int = 7,
    val minConversationsBeforeEvolution: Int = 3,
    val requireWifi: Boolean = true,
    val requireCharging: Boolean = false,
)

// ─── Evolution Session ─────────────────────────────────────────────────────

data class EvolutionSession(
    val id: String,
    val startedAt: Long = System.currentTimeMillis(),
    val completedTasks: MutableList<EvolutionTaskType> = mutableListOf(),
    val totalInsightsGained: Int = 0,
    val errors: MutableList<String> = mutableListOf(),
) {
    val isSuccess: Boolean get() = errors.isEmpty()
}
