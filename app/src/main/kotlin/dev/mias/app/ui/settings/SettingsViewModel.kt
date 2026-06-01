package dev.mias.app.ui.settings

import android.content.Intent
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.mias.core.agent.storage.StorageAccessManager
import dev.mias.core.common.model.BrainState
import dev.mias.core.data.preferences.MiasPreferences
import dev.mias.core.data.preferences.MiasPrefs
import dev.mias.core.inference.orchestrator.InferenceOrchestrator
import dev.mias.core.modelhub.manager.ModelManager
import dev.mias.core.modelhub.model.InstalledModel
import dev.mias.core.modelhub.model.ModelRole
import dev.mias.core.speech.SpeechEngine
import dev.mias.core.speech.SpeechLanguage
import dev.mias.core.thermal.TawsGovernor
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SettingsUiState(
    val brainState: BrainState = BrainState.GEMMA_NPU,
    val thermalTempC: Float? = null,
    val batteryLevel: Int? = null,
    val isDesktopReachable: Boolean = false,
    val installedModels: List<InstalledModel> = emptyList(),
    val roleAssignments: Map<ModelRole, String?> = emptyMap(),
    val speechLanguage: SpeechLanguage = SpeechLanguage.ENGLISH_US,
    val speechAutoDetect: Boolean = true,
    val huggingFaceToken: String = "",
    val desktopHost: String = "",
    val desktopPort: Int = MiasPrefs.DEFAULT_DESKTOP_PORT,
    val desktopToken: String = "",
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val orchestrator: InferenceOrchestrator,
    private val tawsGovernor: TawsGovernor,
    private val speechEngine: SpeechEngine,
    private val modelManager: ModelManager,
    private val preferences: MiasPreferences,
    private val storageAccess: StorageAccessManager,
) : ViewModel() {

    /** Current storage reach, recomputed on read (changes after the user grants access). */
    fun storageAccessSummary(): String = storageAccess.describeAccess()
    fun hasAllFilesAccess(): Boolean = storageAccess.hasAllFilesAccess()
    fun allFilesAccessIntent(): Intent? = storageAccess.allFilesAccessSettingsIntent()

    private val _speechAutoDetect = MutableStateFlow(true)

    val uiState: StateFlow<SettingsUiState> = combine(
        orchestrator.brainState,
        modelManager.installedModels,
        modelManager.roleAssignments,
        _speechAutoDetect,
        preferences.prefsFlow,
    ) { brain, installed, assignments, autoDetect, prefs ->
        val snapshot = tawsGovernor.latestSnapshot
        SettingsUiState(
            brainState = brain,
            thermalTempC = snapshot?.socTempCelsius,
            batteryLevel = snapshot?.batteryLevel,
            isDesktopReachable = orchestrator.desktopEngine != null,
            installedModels = installed,
            roleAssignments = ModelRole.entries.associateWith { role -> assignments[role] },
            speechLanguage = speechEngine.getCurrentLanguage(),
            speechAutoDetect = autoDetect,
            huggingFaceToken = prefs.huggingFaceToken,
            desktopHost = prefs.desktopHost,
            desktopPort = prefs.desktopPort,
            desktopToken = prefs.desktopToken,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = SettingsUiState(),
    )

    fun setSpeechLanguage(language: SpeechLanguage) {
        speechEngine.setLanguage(language)
    }

    fun setSpeechAutoDetect(enabled: Boolean) {
        speechEngine.setAutoDetect(enabled)
        _speechAutoDetect.value = enabled
    }

    fun setHuggingFaceToken(token: String) {
        viewModelScope.launch { preferences.setHuggingFaceToken(token) }
    }

    fun setDesktopEndpoint(host: String, port: Int, token: String) {
        viewModelScope.launch { preferences.setDesktopEndpoint(host, port, token) }
    }
}
