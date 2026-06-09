package dev.mias.core.inference.orchestrator

import com.google.common.truth.Truth.assertThat
import dev.mias.core.common.model.BrainState
import dev.mias.core.common.model.Stimulus
import dev.mias.core.common.model.StimulusType
import dev.mias.core.inference.InferenceEngine
import dev.mias.core.inference.engine.GoogleAiEdgeEngine
import dev.mias.core.inference.react.ReActEngine
import dev.mias.core.modelhub.manager.ModelManager
import dev.mias.core.resilience.DeviceHealthMonitor
import dev.mias.core.security.GuardrailProcessor
import dev.mias.core.thermal.TawsAction
import dev.mias.core.thermal.TawsGovernor
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
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
    private lateinit var modelManager: ModelManager
    private lateinit var roleClassifier: RoleClassifier
    private lateinit var deviceHealthMonitor: DeviceHealthMonitor
    private lateinit var orchestrator: InferenceOrchestrator

    @BeforeEach
    fun setUp() {
        primaryEngine = mockk(relaxed = true)
        survivalEngine = mockk(relaxed = true)
        reActEngine = mockk(relaxed = true)
        tawsGovernor = mockk(relaxed = true)
        guardrailProcessor = mockk(relaxed = true)
        modelManager = mockk(relaxed = true)
        roleClassifier = mockk(relaxed = true)
        deviceHealthMonitor = mockk(relaxed = true)

        orchestrator = InferenceOrchestrator(
            primaryEngine = primaryEngine,
            survivalEngine = survivalEngine,
            reActEngine = reActEngine,
            tawsGovernor = tawsGovernor,
            guardrailProcessor = guardrailProcessor,
            modelManager = modelManager,
            roleClassifier = roleClassifier,
            deviceHealthMonitor = deviceHealthMonitor,
        )
    }

    /**
     * Call the now-internal suspend [InferenceOrchestrator.selectEngine] directly.
     * A relaxed [roleClassifier] returns null, so role inference falls through to
     * the keyword heuristic — CHAT for this neutral stimulus, which keeps the NPU
     * path eligible exactly as the routing tests expect.
     */
    private fun callSelectEngine(action: TawsAction): Pair<InferenceEngine, BrainState> =
        runBlocking {
            orchestrator.selectEngine(
                action,
                Stimulus(type = StimulusType.USER_MESSAGE, content = "hello"),
            )
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
