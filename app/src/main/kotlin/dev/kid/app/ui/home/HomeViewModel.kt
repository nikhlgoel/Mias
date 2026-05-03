package dev.kid.app.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.kid.core.common.getOrDefault
import dev.kid.core.common.model.BrainState
import dev.kid.core.common.model.CognitionState
import dev.kid.core.data.ConversationRepository
import dev.kid.core.inference.orchestrator.InferenceOrchestrator
import dev.kid.core.soul.SoulEngine
import dev.kid.core.soul.model.Sentiment
import dev.kid.core.thermal.TawsGovernor
import dev.kid.core.ui.components.Nudge
import dev.kid.core.ui.components.NudgeType
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.UUID
import javax.inject.Inject

data class HomeUiState(
    val brainState: BrainState = BrainState.GEMMA_NPU,
    val cognitionState: CognitionState = CognitionState.IDLE,
    val greeting: String = "Hey there",
    val subtitle: String = "What's on your mind?",
    val nudges: List<Nudge> = emptyList(),
    val recentConversationCount: Int = 0,
    val thermalTemp: Float = 32f,
    val batteryLevel: Int = 100,
    val sentiment: Sentiment = Sentiment.NEUTRAL,
)

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val orchestrator: InferenceOrchestrator,
    private val soulEngine: SoulEngine,
    private val tawsGovernor: TawsGovernor,
    private val conversationRepository: ConversationRepository,
) : ViewModel() {

    private val _nudges = MutableStateFlow(generateInitialNudges())

    val uiState: StateFlow<HomeUiState> = combine(
        orchestrator.brainState,
        orchestrator.cognitionState,
        soulEngine.state,
        _nudges,
    ) { brain, cognition, soul, nudges ->
        val thermal = tawsGovernor.latestSnapshot
        HomeUiState(
            brainState = brain,
            cognitionState = cognition,
            greeting = buildGreeting(soul.detectedSentiment),
            subtitle = buildSubtitle(brain, soul.detectedSentiment),
            nudges = nudges,
            thermalTemp = thermal?.socTempCelsius ?: 32f,
            batteryLevel = thermal?.batteryLevel ?: 100,
            sentiment = soul.detectedSentiment,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = HomeUiState(),
    )

    fun dismissNudge(id: String) {
        _nudges.value = _nudges.value.filter { it.id != id }
    }

    private fun buildGreeting(sentiment: Sentiment): String = when (sentiment) {
        Sentiment.HAPPY -> "Hey, looking good!"
        Sentiment.EXCITED -> "Let's gooo!"
        Sentiment.FRUSTRATED -> "I'm here for you"
        Sentiment.SAD -> "Hey, I'm here"
        Sentiment.STRESSED -> "Take a breath"
        Sentiment.CURIOUS -> "Let's explore"
        Sentiment.IN_FLOW -> "Flow mode"
        Sentiment.NEUTRAL -> "Hey there"
    }

    private fun buildSubtitle(brain: BrainState, sentiment: Sentiment): String = when (brain) {
        BrainState.GEMMA_NPU -> "Full power on NPU"
        BrainState.MOBILELLM_SURVIVAL -> "Keeping it light"
        BrainState.QWEN_DESKTOP -> "Connected to desktop"
        BrainState.QWEN_WAKING -> "Waking up the big brain..."
        BrainState.DEGRADED -> "Running minimal — plug in soon"
    }

    private fun generateInitialNudges(): List<Nudge> = listOf(
        Nudge(
            id = UUID.randomUUID().toString(),
            type = NudgeType.GREETING,
            title = "All systems online",
            body = "Gemma NPU is warmed up and ready. Everything runs locally.",
            priority = 0.9f,
        ),
        Nudge(
            id = UUID.randomUUID().toString(),
            type = NudgeType.INSIGHT,
            title = "Zero cloud",
            body = "Your data never leaves this device. Private by design.",
            priority = 0.7f,
        ),
    )
}
