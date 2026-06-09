package dev.mias.core.inference.orchestrator

import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test

class AgentReliabilityTrackerTest {

    private val modelId = "qwen-2b"

    @Test
    fun `a model with no history is not demoted`() {
        val tracker = AgentReliabilityTracker()
        assertThat(tracker.isDemoted(modelId)).isFalse()
    }

    @Test
    fun `a single failure does not demote`() {
        val tracker = AgentReliabilityTracker()
        tracker.record(modelId, success = false)
        assertThat(tracker.isDemoted(modelId)).isFalse()
    }

    @Test
    fun `two failures in the window demote`() {
        val tracker = AgentReliabilityTracker()
        tracker.record(modelId, success = false)
        tracker.record(modelId, success = false)
        assertThat(tracker.isDemoted(modelId)).isTrue()
    }

    @Test
    fun `two of the last three failing demotes`() {
        val tracker = AgentReliabilityTracker()
        tracker.record(modelId, success = true)
        tracker.record(modelId, success = false)
        tracker.record(modelId, success = false)
        assertThat(tracker.isDemoted(modelId)).isTrue()
    }

    @Test
    fun `mostly succeeding stays agentic`() {
        val tracker = AgentReliabilityTracker()
        tracker.record(modelId, success = false)
        tracker.record(modelId, success = true)
        tracker.record(modelId, success = true)
        assertThat(tracker.isDemoted(modelId)).isFalse()
    }

    @Test
    fun `old failures age out of the window`() {
        val tracker = AgentReliabilityTracker()
        tracker.record(modelId, success = false)
        tracker.record(modelId, success = false)
        // Two recent successes push the failures out of the 3-wide window.
        tracker.record(modelId, success = true)
        tracker.record(modelId, success = true)
        assertThat(tracker.isDemoted(modelId)).isFalse()
    }

    @Test
    fun `reset clears history`() {
        val tracker = AgentReliabilityTracker()
        tracker.record(modelId, success = false)
        tracker.record(modelId, success = false)
        tracker.reset(modelId)
        assertThat(tracker.isDemoted(modelId)).isFalse()
    }

    @Test
    fun `histories are independent per model`() {
        val tracker = AgentReliabilityTracker()
        tracker.record("bad", success = false)
        tracker.record("bad", success = false)
        tracker.record("good", success = true)
        tracker.record("good", success = true)
        assertThat(tracker.isDemoted("bad")).isTrue()
        assertThat(tracker.isDemoted("good")).isFalse()
    }
}
