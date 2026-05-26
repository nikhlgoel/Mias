package dev.mias.app.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.mias.core.common.model.BrainState
import dev.mias.core.common.model.CognitionState
import dev.mias.core.data.ConversationRepository
import dev.mias.core.inference.orchestrator.InferenceOrchestrator
import dev.mias.core.modelhub.manager.ModelManager
import dev.mias.core.modelhub.model.InstalledModel
import dev.mias.core.thermal.TawsGovernor
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import java.util.Calendar
import javax.inject.Inject

data class HomeUiState(
    val brainState: BrainState = BrainState.GEMMA_NPU,
    val cognitionState: CognitionState = CognitionState.IDLE,
    val greeting: String = "Welcome",
    val subtitle: String = "Tap the orb when you're ready to begin",
    val installedModels: List<InstalledModel> = emptyList(),
    val recentConversationCount: Int = 0,
    val isReady: Boolean = false,
)

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val orchestrator: InferenceOrchestrator,
    private val tawsGovernor: TawsGovernor,
    private val conversationRepository: ConversationRepository,
    private val modelManager: ModelManager,
) : ViewModel() {

    val uiState: StateFlow<HomeUiState> = combine(
        orchestrator.brainState,
        orchestrator.cognitionState,
        modelManager.installedModels,
        conversationRepository.getConversations().map { it.size },
    ) { brain, cognition, installed, recentCount ->
        val ready = installed.isNotEmpty()
        HomeUiState(
            brainState = brain,
            cognitionState = cognition,
            greeting = timeBasedGreeting(),
            subtitle = buildSubtitle(ready, installed.size, recentCount),
            installedModels = installed,
            recentConversationCount = recentCount,
            isReady = ready,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = HomeUiState(),
    )

    private fun buildSubtitle(ready: Boolean, installedCount: Int, recentCount: Int): String {
        if (!ready) {
            return "No model is installed yet. Visit Models to choose one that suits your device."
        }
        val modelLabel = if (installedCount == 1) "1 model ready" else "$installedCount models ready"
        if (recentCount == 0) {
            return "$modelLabel. Tap the orb to start a conversation."
        }
        val chatLabel = if (recentCount == 1) "1 past conversation" else "$recentCount past conversations"
        return "$modelLabel · $chatLabel"
    }

    private fun timeBasedGreeting(): String {
        val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        return when (hour) {
            in 5..11 -> "Good morning"
            in 12..16 -> "Good afternoon"
            in 17..21 -> "Good evening"
            else -> "Hi"
        }
    }
}
