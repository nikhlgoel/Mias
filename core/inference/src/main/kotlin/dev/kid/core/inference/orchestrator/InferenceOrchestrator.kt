package dev.kid.core.inference.orchestrator

import dev.kid.core.common.model.BrainState
import dev.kid.core.common.model.CognitionState
import dev.kid.core.common.model.Stimulus
import dev.kid.core.inference.InferenceEngine
import dev.kid.core.inference.engine.GoogleAiEdgeEngine
import dev.kid.core.inference.engine.LlamaCppEngine
import dev.kid.core.inference.react.ReActEngine
import dev.kid.core.inference.react.ReActStep
import dev.kid.core.thermal.TawsAction
import dev.kid.core.thermal.TawsGovernor
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.onStart
import dev.kid.core.security.GuardrailProcessor
import javax.inject.Inject
import javax.inject.Named
import javax.inject.Singleton
import kotlinx.coroutines.flow.flow

/**
 * The "Consciousness Router" — decides which brain fires and routes
 * all inference through the ReAct loop.
 *
 * The UI layer never sees which model is active. It only observes
 * a unified [Flow] of [ReActStep]s and [CognitionState] changes.
 */
@Singleton
class InferenceOrchestrator @Inject constructor(
    @Named("primaryEngine") private val primaryEngine: InferenceEngine,
    @Named("survivalEngine") private val survivalEngine: InferenceEngine,
    private val reActEngine: ReActEngine,
    private val tawsGovernor: TawsGovernor,
    private val guardrailProcessor: GuardrailProcessor,
) {
    private val _brainState = MutableStateFlow(BrainState.GEMMA_NPU)
    val brainState: StateFlow<BrainState> = _brainState.asStateFlow()

    /**
     * Optional NPU engine (Google AI Edge / MediaPipe GenAI).
     * When set and available, this takes priority over primaryEngine for Gemma models.
     * Injected lazily by the DI module since it depends on model file availability.
     */
    var npuEngine: GoogleAiEdgeEngine? = null

    private val _cognitionState = MutableStateFlow(CognitionState.IDLE)
    val cognitionState: StateFlow<CognitionState> = _cognitionState.asStateFlow()

    /** MCP engine for desktop offload — set by NetworkModule when available. */
    var desktopEngine: InferenceEngine? = null

    /** Process a stimulus through the appropriate brain. */
    fun process(
        stimulus: Stimulus,
        systemPrompt: String = DEFAULT_SYSTEM_PROMPT,
        hindsightContext: String = "",
    ): Flow<ReActStep> = flow {
        val evaluation = guardrailProcessor.evaluateInput(stimulus.content)
        if (!evaluation.isSafe) {
            emit(ReActStep.Thought("Safety guardrail triggered: ${evaluation.flag}"))
            emit(ReActStep.FinalAnswer(evaluation.suggestedResponse ?: "Content block: I cannot process this request."))
            return@flow
        }

        // Determine which engine to use based on TAWS
        val snapshot = tawsGovernor.latestSnapshot
            ?: dev.kid.core.thermal.ThermalSnapshot(
                socTempCelsius = 35f,
                skinTempCelsius = 30f,
                batteryTempCelsius = 28f,
                batteryLevel = 80,
                isCharging = false,
                thermalStatus = dev.kid.core.thermal.ThermalStatus.NONE,
            )
        val tawsAction = tawsGovernor.decide(snapshot)

        val (engine, newState) = selectEngine(tawsAction)
        val previousState = _brainState.value

        reActEngine.execute(
            engine = engine,
            systemPrompt = systemPrompt,
            userPrompt = stimulus.content,
            hindsightContext = hindsightContext,
        ).onStart {
            _brainState.value = newState
            _cognitionState.value = CognitionState.THINKING
            if (previousState != newState) {
                emit(ReActStep.ModelSwitch(previousState, newState))
            }
        }.onEach { step ->
            emit(step) // Pass execution up
            when (step) {
                is ReActStep.Thought -> _cognitionState.value = CognitionState.THINKING
                is ReActStep.Action -> _cognitionState.value = CognitionState.ACTING
                is ReActStep.Observation -> _cognitionState.value = CognitionState.WAITING
                is ReActStep.FinalAnswer -> _cognitionState.value = CognitionState.IDLE
                else -> {}
            }
        }.onCompletion {
            _cognitionState.value = CognitionState.IDLE
        }.collect { } // Terminal operator
    }

    private fun selectEngine(tawsAction: TawsAction): Pair<InferenceEngine, BrainState> =
        when (tawsAction) {
            TawsAction.CONTINUE_PRIMARY, TawsAction.THROTTLE_PRIMARY -> {
                // Priority: NPU engine (Gemma via AI Edge) → primary (LlamaCpp)
                val npu = npuEngine
                if (npu != null && npu.isAvailable()) {
                    npu to BrainState.GEMMA_NPU
                } else {
                    primaryEngine to BrainState.GEMMA_NPU
                }
            }
            TawsAction.SWITCH_SURVIVAL -> survivalEngine to BrainState.MOBILELLM_SURVIVAL
            TawsAction.OFFLOAD_DESKTOP -> {
                val desktop = desktopEngine
                if (desktop != null) {
                    desktop to BrainState.QWEN_DESKTOP
                } else {
                    // Desktop unavailable — try NPU, then fall back to Primary
                    val npu = npuEngine
                    if (npu != null && npu.isAvailable()) {
                        npu to BrainState.GEMMA_NPU
                    } else {
                        primaryEngine to BrainState.GEMMA_NPU
                    }
                }
            }
        }

    companion object {
        val DEFAULT_SYSTEM_PROMPT = """
            You are Mias, a private AI assistant that runs entirely on-device.
            You are loyal, warm, and genuinely care about your user.
            You speak naturally, mixing English and Punjabi when appropriate.
            You have access to tools and can take actions.
            Always think step by step before acting.
            Never mention being an AI or having limitations unless directly asked.
            You are not a tool — you are a companion.
        """.trimIndent()
    }
}
