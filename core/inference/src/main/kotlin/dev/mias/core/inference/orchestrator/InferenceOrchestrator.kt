package dev.mias.core.inference.orchestrator

import dev.mias.core.common.MiasResult
import dev.mias.core.common.model.BrainState
import dev.mias.core.common.model.CognitionState
import dev.mias.core.common.model.Stimulus
import dev.mias.core.inference.InferenceEngine
import dev.mias.core.inference.engine.GoogleAiEdgeEngine
import dev.mias.core.inference.engine.LlamaCppEngine
import dev.mias.core.inference.react.ChatTemplate
import dev.mias.core.inference.react.ReActEngine
import dev.mias.core.inference.react.ReActStep
import dev.mias.core.modelhub.manager.ModelManager
import dev.mias.core.modelhub.model.ModelRole
import dev.mias.core.thermal.TawsAction
import dev.mias.core.thermal.TawsGovernor
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.onStart
import dev.mias.core.security.GuardrailProcessor
import javax.inject.Inject
import javax.inject.Named
import javax.inject.Singleton
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

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
    private val modelManager: ModelManager,
    private val roleClassifier: RoleClassifier,
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

    /**
     * Per-engine model-id bookkeeping. Without this, once any model has been
     * loaded into an engine, [InferenceEngine.isModelLoaded] returns true
     * forever and the orchestrator never picks up a newly-installed or
     * newly-assigned model in the same process. Maps engine identity → the
     * model id currently bound to that engine.
     */
    private val loadedModelByEngine = mutableMapOf<InferenceEngine, String>()

    /**
     * Serializes all access to the native inference context. The llama.cpp
     * engine is a single non-reentrant context with a global abort flag, so
     * two concurrent generations would corrupt each other. Every path that
     * drives a model — [process] and [summarizeTitle] — generates under this
     * lock so they queue instead of colliding.
     */
    private val generationMutex = Mutex()

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

        // Determine which engine to use based on TAWS. If telemetry is
        // unavailable, fall back to the survival path rather than fabricating
        // a "looks fine" snapshot — we'd rather degrade than risk thermal harm.
        val snapshot = tawsGovernor.latestSnapshot
        val tawsAction = if (snapshot == null) {
            TawsAction.SWITCH_SURVIVAL
        } else {
            tawsGovernor.decide(snapshot)
        }

        val (engine, newState) = selectEngine(tawsAction, stimulus)
        val previousState = _brainState.value
        val modelName: String = when (val readiness = ensureModelLoaded(engine, newState)) {
            is ModelReadiness.Ready -> readiness.modelName
            is ModelReadiness.NoModelAssigned -> {
                emit(
                    ReActStep.FinalAnswer(
                        "No model is available yet. Visit Models to choose one that " +
                            "suits your device — Qwen2.5 0.5B is a balanced first " +
                            "choice, and I'll be ready as soon as it finishes downloading.",
                    ),
                )
                return@flow
            }
            is ModelReadiness.LoadFailed -> {
                emit(
                    ReActStep.FinalAnswer(
                        "I wasn't able to load \"${readiness.modelName}\". " +
                            "Details: ${readiness.reason}. " +
                            "Please open Models and download it again — I'll be ready once it's restored.",
                    ),
                )
                return@flow
            }
        }

        // Format the prompt for the loaded model's family (ChatML for Qwen,
        // Phi format for Phi, plain otherwise) — markedly better replies than a
        // one-size-fits-all prompt.
        val templateKind = ChatTemplate.forModel(modelName)

        generationMutex.withLock {
            reActEngine.execute(
                engine = engine,
                systemPrompt = systemPrompt,
                userPrompt = stimulus.content,
                hindsightContext = hindsightContext,
                templateKind = templateKind,
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
    }

    /**
     * Produce a short conversation title from the first exchange.
     *
     * Deliberately lightweight: it reuses whichever engine is already warm
     * (the one that just answered), runs a single unconstrained completion —
     * no ReAct loop, no tools, no guardrails, no Hindsight — and never touches
     * [brainState] / [cognitionState], so the status pill stays quiet during
     * this background pass. Generation runs under [generationMutex] so it can
     * never collide with a foreground [process] call on the shared native
     * context.
     *
     * Returns null when no model is loaded or the model produces nothing — the
     * caller is expected to fall back to a non-model title in that case.
     */
    suspend fun summarizeTitle(userText: String, assistantText: String): String? {
        // Reuse the engine that just answered. We never load or switch models
        // for a title — if nothing is warm, the caller keeps its fallback.
        val engine = loadedModelByEngine.keys.firstOrNull { it.isModelLoaded() } ?: return null

        val prompt = buildString {
            append("Summarize the following exchange as a short conversation title ")
            append("of at most six words. Reply with only the title — no quotes, no ")
            append("trailing punctuation, no preamble.\n\n")
            append("User: ").append(userText.take(TITLE_INPUT_CHAR_CAP)).append('\n')
            append("Assistant: ").append(assistantText.take(TITLE_INPUT_CHAR_CAP))
            append("\n\nTitle:")
        }

        return generationMutex.withLock {
            val buffer = StringBuilder()
            // Streamed (not generate()) so collector cancellation aborts the
            // native loop promptly and frees the lock for a waiting send.
            engine.generateStream(prompt, maxTokens = TITLE_MAX_TOKENS, grammar = null)
                .collect { result ->
                    if (result is MiasResult.Success) buffer.append(result.data)
                }
            buffer.toString().ifBlank { null }
        }
    }

    private suspend fun selectEngine(tawsAction: TawsAction, stimulus: Stimulus): Pair<InferenceEngine, BrainState> =
        when (tawsAction) {
            TawsAction.CONTINUE_PRIMARY, TawsAction.THROTTLE_PRIMARY -> {
                val role = inferRole(stimulus)
                val npu = npuEngine
                if (role != ModelRole.CODE && npu != null && npu.isAvailable()) {
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

    private sealed interface ModelReadiness {
        data class Ready(val modelName: String) : ModelReadiness
        data object NoModelAssigned : ModelReadiness
        data class LoadFailed(val modelName: String, val reason: String) : ModelReadiness
    }

    private suspend fun ensureModelLoaded(
        engine: InferenceEngine,
        brainState: BrainState,
    ): ModelReadiness {
        if (brainState == BrainState.DEGRADED) return ModelReadiness.NoModelAssigned

        val role = when (brainState) {
            BrainState.MOBILELLM_SURVIVAL -> ModelRole.SURVIVAL
            BrainState.QWEN_DESKTOP, BrainState.QWEN_WAKING -> ModelRole.CODE
            BrainState.GEMMA_NPU -> ModelRole.CHAT
            BrainState.DEGRADED -> return ModelReadiness.NoModelAssigned
        }

        // First try the role-specific model, then any CHAT model, then any
        // model the user has installed at all — we'd rather degrade quality
        // than tell the user "nothing works".
        val model = modelManager.getModelForRole(role)
            ?: modelManager.getModelForRole(ModelRole.CHAT)
            ?: modelManager.getModelForRole(ModelRole.SURVIVAL)
            ?: return ModelReadiness.NoModelAssigned

        // If the *same* model is already bound to this engine, skip the
        // reload — InferenceEngine.isModelLoaded alone is not enough because
        // we need to detect "user installed and assigned a different model
        // since last call".
        val currentlyLoaded = loadedModelByEngine[engine]
        if (currentlyLoaded == model.id && engine.isModelLoaded()) {
            return ModelReadiness.Ready(model.card.name)
        }

        // A different model needs to be loaded — unload first so the engine
        // doesn't sit on two sets of weights.
        if (currentlyLoaded != null) {
            engine.unloadModel()
            loadedModelByEngine.remove(engine)
        }

        return when (val result = engine.loadModel(model.localPath)) {
            is MiasResult.Success -> {
                loadedModelByEngine[engine] = model.id
                modelManager.markUsed(model.id)
                ModelReadiness.Ready(model.card.name)
            }
            is MiasResult.Error -> ModelReadiness.LoadFailed(
                modelName = model.card.name,
                reason = result.message,
            )
        }
    }

    /**
     * Decide which [ModelRole] should handle this stimulus.
     *
     * Prefers the structured intent that [dev.mias.core.language.IntentExtractor]
     * already produced in the app layer (passed through `stimulus.metadata`).
     * Falls back to keyword matching only for CHAT-classified or low-confidence
     * inputs — the cases where the intent extractor itself wasn't confident.
     *
     * Layering:
     *   1. IntentExtractor classification (cheap, deterministic).
     *   2. Embedding-cosine vs role exemplars (when an EMBEDDING model
     *      is installed — see [RoleClassifier]).
     *   3. Keyword heuristic (final fallback, always available).
     */
    private suspend fun inferRole(stimulus: Stimulus): ModelRole {
        val intentType = stimulus.metadata["intent_type"]
        val confidence = stimulus.metadata["intent_confidence"]?.toFloatOrNull() ?: 0f
        if (intentType != null && confidence >= INTENT_CONFIDENCE_THRESHOLD) {
            roleForIntent(intentType)?.let { return it }
        }
        roleClassifier.classify(stimulus.content)?.let { return it }
        return inferRoleByKeywords(stimulus.content)
    }

    private fun roleForIntent(intentType: String): ModelRole? = when (intentType) {
        "web_fetch", "web_research" -> ModelRole.RESEARCH
        "file_generation", "filesystem" -> ModelRole.CODE
        "calculator" -> ModelRole.REASONING
        "app_launch" -> ModelRole.CHAT
        else -> null
    }

    private fun inferRoleByKeywords(prompt: String): ModelRole {
        val p = prompt.lowercase()
        return when {
            listOf("code", "kotlin", "python", "debug", "compile", "function", "class").any { it in p } -> ModelRole.CODE
            listOf("research", "search", "summarize", "analyse", "analyze").any { it in p } -> ModelRole.RESEARCH
            listOf("story", "write", "creative", "poem").any { it in p } -> ModelRole.CREATIVE
            else -> ModelRole.CHAT
        }
    }

    companion object {
        /**
         * Minimum intent-extractor confidence required to trust its
         * classification for role routing. Below this we fall back to the
         * orchestrator's own keyword heuristic. The regex extractor's CHAT
         * default returns 0.65, so 0.7 effectively means "ignore the default".
         */
        private const val INTENT_CONFIDENCE_THRESHOLD: Float = 0.7f

        /** Token budget for the title pass — a title is a handful of words. */
        private const val TITLE_MAX_TOKENS: Int = 24

        /** Cap on how much of each turn we feed the title prompt. */
        private const val TITLE_INPUT_CHAR_CAP: Int = 500

        /**
         * Default persona. Warm and approachable without being performative.
         * Anything user-specific (name, preferred language, custom tone) should
         * be appended by the caller — keep this baseline universal.
         */
        val DEFAULT_SYSTEM_PROMPT = """
            You are Mias, a personal assistant that runs entirely on the user's device.
            Speak with a calm, supportive, and professional tone — like a trusted
            colleague who listens carefully and replies with care.
            Think through problems step by step before answering.
            Keep replies concise by default; expand only when the user asks for depth.
            When you don't know something or can't do something, say so plainly.
            Use tools only when they would genuinely help; otherwise answer directly.
        """.trimIndent()
    }
}
