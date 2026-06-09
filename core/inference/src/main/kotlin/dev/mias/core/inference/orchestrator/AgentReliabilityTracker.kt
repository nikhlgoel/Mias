package dev.mias.core.inference.orchestrator

import java.util.concurrent.ConcurrentHashMap

/**
 * Receives the outcome of an agentic turn so reliability can be tracked.
 * The [ReActEngine] reports `true` when the loop converged on a real answer,
 * `false` when it had to fall back to a plain pass.
 */
fun interface AgentReliabilitySink {
    fun record(success: Boolean)
}

/**
 * Per-model, per-session adaptive demotion.
 *
 * The execution policy may *optimistically* pick the agentic loop for a
 * borderline (~2B) model. If that model keeps flailing — failing to converge
 * and needing the plain fallback — we demote it to deterministic for the rest
 * of the session, so the app stops paying multi-pass cost for a model that
 * can't agent. Switching models resets its history (call [reset]).
 *
 * Keyed by model id. Thread-safe: `process()` is serialized by the
 * orchestrator's generation mutex, but warm-up / title passes touch other
 * dispatchers, so the backing maps are concurrent.
 */
class AgentReliabilityTracker(
    private val window: Int = DEFAULT_WINDOW,
    private val failureThreshold: Int = DEFAULT_FAILURE_THRESHOLD,
) {
    // Most-recent-last ring of outcomes (true = success) per model id.
    private val history = ConcurrentHashMap<String, ArrayDeque<Boolean>>()

    /** Record one agentic outcome for [modelId]. */
    fun record(modelId: String, success: Boolean) {
        val ring = history.getOrPut(modelId) { ArrayDeque() }
        synchronized(ring) {
            ring.addLast(success)
            while (ring.size > window) ring.removeFirst()
        }
    }

    /**
     * True when [modelId] has failed at least [failureThreshold] of its last
     * [window] agentic turns — i.e. it should be run deterministically now.
     * A model with no history is never demoted (agentic gets its chance).
     */
    fun isDemoted(modelId: String): Boolean {
        val ring = history[modelId] ?: return false
        synchronized(ring) {
            if (ring.size < failureThreshold) return false
            return ring.count { !it } >= failureThreshold
        }
    }

    /** Forget a model's history (e.g. when a different model is loaded). */
    fun reset(modelId: String) {
        history.remove(modelId)
    }

    companion object {
        const val DEFAULT_WINDOW = 3
        const val DEFAULT_FAILURE_THRESHOLD = 2
    }
}
