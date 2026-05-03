package dev.kid.core.inference.orchestrator

import com.google.common.truth.Truth.assertThat
import dev.kid.core.common.model.BrainState
import dev.kid.core.inference.InferenceEngine
import dev.kid.core.inference.engine.GoogleAiEdgeEngine
import dev.kid.core.inference.react.ReActEngine
import dev.kid.core.security.GuardrailProcessor
import dev.kid.core.thermal.TawsAction
import dev.kid.core.thermal.TawsGovernor
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

/**
 * Tests the InferenceOrchestrator's engine selection logic.
 *
 * The selectEngine method is private, so we test it indirectly by
 * setting up the engine fields and inspecting brainState after selection.
 * We use reflection to call selectEngine directly since the routing logic
 * is the critical behavior under test.
 */
@DisplayName("InferenceOrchestrator engine selection")
class InferenceOrchestratorTest {

    private lateinit var primaryEngine: InferenceEngine
    private lateinit var survivalEngine: InferenceEngine
    private lateinit var reActEngine: ReActEngine
    private lateinit var tawsGovernor: TawsGovernor
    private lateinit var guardrailProcessor: GuardrailProcessor
    private lateinit var orchestrator: InferenceOrchestrator

    @BeforeEach
    fun setUp() {
        primaryEngine = mockk(relaxed = true)
        survivalEngine = mockk(relaxed = true)
        reActEngine = mockk(relaxed = true)
        tawsGovernor = mockk(relaxed = true)
        guardrailProcessor = mockk(relaxed = true)

        orchestrator = InferenceOrchestrator(
            primaryEngine = primaryEngine,
            survivalEngine = survivalEngine,
            reActEngine = reActEngine,
            tawsGovernor = tawsGovernor,
            guardrailProcessor = guardrailProcessor,
        )
    }

    /** Helper to call private selectEngine via reflection. */
    private fun callSelectEngine(action: TawsAction): Pair<InferenceEngine, BrainState> {
        val method = InferenceOrchestrator::class.java.getDeclaredMethod(
            "selectEngine",
            TawsAction::class.java,
        )
        method.isAccessible = true
        @Suppress("UNCHECKED_CAST")
        return method.invoke(orchestrator, action) as Pair<InferenceEngine, BrainState>
    }

    @Nested
    @DisplayName("CONTINUE_PRIMARY / THROTTLE_PRIMARY")
    inner class PrimaryPathTests {

        @Test
        fun `selects NPU engine when available`() {
            val npuEngine = mockk<GoogleAiEdgeEngine>(relaxed = true)
            every { npuEngine.isAvailable() } returns true
            orchestrator.npuEngine = npuEngine

            val (engine, state) = callSelectEngine(TawsAction.CONTINUE_PRIMARY)

            assertThat(engine).isSameInstanceAs(npuEngine)
            assertThat(state).isEqualTo(BrainState.GEMMA_NPU)
        }

        @Test
        fun `falls back to primary when NPU unavailable`() {
            val npuEngine = mockk<GoogleAiEdgeEngine>(relaxed = true)
            every { npuEngine.isAvailable() } returns false
            orchestrator.npuEngine = npuEngine

            val (engine, _) = callSelectEngine(TawsAction.CONTINUE_PRIMARY)

            assertThat(engine).isSameInstanceAs(primaryEngine)
        }

        @Test
        fun `falls back to primary when NPU engine is null`() {
            orchestrator.npuEngine = null

            val (engine, _) = callSelectEngine(TawsAction.THROTTLE_PRIMARY)

            assertThat(engine).isSameInstanceAs(primaryEngine)
        }
    }

    @Nested
    @DisplayName("SWITCH_SURVIVAL")
    inner class SurvivalPathTests {

        @Test
        fun `selects survival engine`() {
            val (engine, state) = callSelectEngine(TawsAction.SWITCH_SURVIVAL)

            assertThat(engine).isSameInstanceAs(survivalEngine)
            assertThat(state).isEqualTo(BrainState.MOBILELLM_SURVIVAL)
        }
    }

    @Nested
    @DisplayName("OFFLOAD_DESKTOP")
    inner class DesktopOffloadTests {

        @Test
        fun `selects desktop engine when available`() {
            val desktopEngine = mockk<InferenceEngine>(relaxed = true)
            orchestrator.desktopEngine = desktopEngine

            val (engine, state) = callSelectEngine(TawsAction.OFFLOAD_DESKTOP)

            assertThat(engine).isSameInstanceAs(desktopEngine)
            assertThat(state).isEqualTo(BrainState.QWEN_DESKTOP)
        }

        @Test
        fun `falls back to NPU when desktop unavailable and NPU available`() {
            orchestrator.desktopEngine = null
            val npuEngine = mockk<GoogleAiEdgeEngine>(relaxed = true)
            every { npuEngine.isAvailable() } returns true
            orchestrator.npuEngine = npuEngine

            val (engine, state) = callSelectEngine(TawsAction.OFFLOAD_DESKTOP)

            assertThat(engine).isSameInstanceAs(npuEngine)
            assertThat(state).isEqualTo(BrainState.GEMMA_NPU)
        }

        @Test
        fun `falls back to primary when both desktop and NPU unavailable`() {
            orchestrator.desktopEngine = null
            orchestrator.npuEngine = null

            val (engine, _) = callSelectEngine(TawsAction.OFFLOAD_DESKTOP)

            assertThat(engine).isSameInstanceAs(primaryEngine)
        }
    }
}
