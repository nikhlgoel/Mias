package dev.mias.core.inference.orchestrator

import dev.mias.core.common.MiasResult
import dev.mias.core.inference.engine.EmbeddingEngine
import dev.mias.core.modelhub.manager.ModelManager
import dev.mias.core.modelhub.model.ModelRole
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.sqrt

/**
 * Embedding-cosine role classifier — phase 2 of model role routing
 * ([dev.mias.core.inference.orchestrator.InferenceOrchestrator.inferRole]).
 *
 * Compares the user's prompt against a small set of cached role exemplars
 * using cosine similarity over Nomic Embed v1.5 (or whatever model is
 * installed in the EMBEDDING role). Returns the best-matching role, or
 * null when:
 *   - no embedding model is installed,
 *   - the embedding model failed to load,
 *   - the best score is below [SIMILARITY_THRESHOLD] (avoids confident
 *     misroutes on ambiguous prompts — caller falls back to keywords).
 *
 * Cost on a typical phone: one embed per prompt (~10–50 ms on Nomic int4).
 * Exemplars are embedded once on first call and cached for the process
 * lifetime.
 */
@Singleton
class RoleClassifier @Inject constructor(
    private val embeddingEngine: EmbeddingEngine,
    private val modelManager: ModelManager,
) {

    private val initLock = Mutex()

    @Volatile
    private var loadAttempted = false

    @Volatile
    private var loadedExemplars: Map<ModelRole, List<FloatArray>> = emptyMap()

    /**
     * Classify [prompt] into a [ModelRole]. Returns null when classification
     * isn't possible (no embedding model) or when the best score is below
     * [SIMILARITY_THRESHOLD].
     */
    suspend fun classify(prompt: String): ModelRole? {
        if (prompt.isBlank()) return null
        if (!ensureReady()) return null

        val promptVec = when (val r = embeddingEngine.getEmbedding(prompt)) {
            is MiasResult.Success -> r.data
            is MiasResult.Error -> return null
        }

        var bestRole: ModelRole? = null
        var bestScore = -1f
        for ((role, vectors) in loadedExemplars) {
            val score = vectors.maxOf { cosine(promptVec, it) }
            if (score > bestScore) {
                bestScore = score
                bestRole = role
            }
        }
        return if (bestScore >= SIMILARITY_THRESHOLD) bestRole else null
    }

    /**
     * Lazy-load the embedding model and embed exemplars. Returns true only
     * when the cache is non-empty and ready to use.
     *
     * Distinguishes "no embedding model installed yet" (transient — we'll
     * retry on the next prompt, so installing Nomic mid-session activates
     * the classifier) from "model present but failed to load / embed"
     * (latched off via [loadAttempted] so we don't hammer a broken model
     * on every message).
     */
    private suspend fun ensureReady(): Boolean {
        if (loadedExemplars.isNotEmpty()) return true
        if (loadAttempted) return false
        initLock.withLock {
            if (loadedExemplars.isNotEmpty()) return true
            if (loadAttempted) return false

            // Not installed yet — transient, do NOT latch off.
            val embeddingModel = modelManager.getModelForRole(ModelRole.EMBEDDING)
                ?: return false

            // From here on, a failure is a real failure → latch off.
            loadAttempted = true

            if (!embeddingEngine.isModelLoaded()) {
                val load = embeddingEngine.loadModel(embeddingModel.localPath)
                if (load is MiasResult.Error) return false
            }

            val cached = mutableMapOf<ModelRole, List<FloatArray>>()
            for ((role, samples) in EXEMPLARS) {
                val vectors = samples.mapNotNull { text ->
                    when (val r = embeddingEngine.getEmbedding(text)) {
                        is MiasResult.Success -> r.data
                        is MiasResult.Error -> null
                    }
                }
                if (vectors.isNotEmpty()) cached[role] = vectors
            }
            loadedExemplars = cached
            return cached.isNotEmpty()
        }
    }

    private fun cosine(a: FloatArray, b: FloatArray): Float {
        if (a.size != b.size) return -1f
        var dot = 0f
        var na = 0f
        var nb = 0f
        for (i in a.indices) {
            val av = a[i]
            val bv = b[i]
            dot += av * bv
            na += av * av
            nb += bv * bv
        }
        val denom = sqrt(na.toDouble()) * sqrt(nb.toDouble())
        return if (denom == 0.0) 0f else (dot / denom).toFloat()
    }

    companion object {
        /**
         * Empirical floor — below this we don't trust the match and fall
         * back to keywords. Tune as routing quality is measured in the
         * wild.
         */
        private const val SIMILARITY_THRESHOLD: Float = 0.45f

        private val EXEMPLARS: Map<ModelRole, List<String>> = mapOf(
            ModelRole.CHAT to listOf(
                "Hi, how are you doing today?",
                "Let's talk about something interesting.",
                "I just want to chat for a bit.",
            ),
            ModelRole.CODE to listOf(
                "Write a Kotlin function that sorts a list of integers.",
                "Debug this Python stack trace.",
                "Refactor this code to be more readable.",
            ),
            ModelRole.RESEARCH to listOf(
                "Summarize this article for me.",
                "What does recent research say about sleep quality?",
                "Compare these two products and tell me the trade-offs.",
            ),
            ModelRole.REASONING to listOf(
                "Solve this math problem step by step.",
                "Walk me through the logic of this decision.",
                "Calculate the optimal route given these constraints.",
            ),
            ModelRole.CREATIVE to listOf(
                "Write a short poem about autumn.",
                "Help me brainstorm names for a side project.",
                "Tell me a story about a curious cat.",
            ),
        )
    }
}
