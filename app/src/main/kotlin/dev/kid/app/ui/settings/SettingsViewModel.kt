package dev.kid.app.ui.settings

import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.kid.core.common.model.BrainState
import dev.kid.core.inference.orchestrator.InferenceOrchestrator
import dev.kid.core.soul.SoulEngine
import dev.kid.core.soul.model.SoulTrait
import dev.kid.core.thermal.TawsGovernor
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import javax.inject.Inject

data class SettingsUiState(
    val brainState: BrainState = BrainState.GEMMA_NPU,
    val thermalTemp: Float = 32f,
    val batteryLevel: Int = 100,
    val isDesktopReachable: Boolean = false,
    val soulTraits: Map<SoulTrait, Float> = emptyMap(),
    val showReActSteps: Boolean = false,
    val desktopHostname: String = "desktop-g15",
    val modelInfo: ModelInfo = ModelInfo(),
)

data class ModelInfo(
    val primaryModel: String = "Gemma-4-e4b",
    val survivalModel: String = "MobileLLM-R1.5",
    val desktopModel: String = "Qwen3-Coder-Next",
    val primaryQuant: String = "INT4",
    val contextLength: String = "32K",
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val orchestrator: InferenceOrchestrator,
    private val soulEngine: SoulEngine,
    private val tawsGovernor: TawsGovernor,
) : ViewModel() {

    private val _uiState = MutableStateFlow(loadCurrentState())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    fun refreshState() {
        _uiState.update { loadCurrentState() }
    }

    private fun loadCurrentState(): SettingsUiState {
        val thermal = tawsGovernor.latestSnapshot
        return SettingsUiState(
            brainState = orchestrator.brainState.value,
            thermalTemp = thermal?.socTempCelsius ?: 32f,
            batteryLevel = thermal?.batteryLevel ?: 100,
            isDesktopReachable = orchestrator.desktopEngine != null,
            soulTraits = soulEngine.state.value.blendCoefficients,
        )
    }
}
