package dev.kid.app.ui.agent

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.kid.core.agent.model.AgentStatus
import dev.kid.core.agent.model.AgentTaskResult
import dev.kid.core.common.KidResult
import dev.kid.core.agent.orchestrator.AgentOrchestrator
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class AgentUiState(
    val status: AgentStatus = AgentStatus(),
    val recentResults: List<AgentTaskResult> = emptyList(),
    val availableTools: List<String> = emptyList(),
    val manualInput: String = "",
    val selectedTool: String? = null,
    val errorMessage: String? = null,
)

@HiltViewModel
class AgentViewModel @Inject constructor(
    private val orchestrator: AgentOrchestrator,
) : ViewModel() {

    private val _manualInput = MutableStateFlow("")
    private val _selectedTool = MutableStateFlow<String?>(null)
    private val _error = MutableStateFlow<String?>(null)

    val uiState: StateFlow<AgentUiState> = combine(
        orchestrator.status,
        orchestrator.recentResults,
        _manualInput,
        _selectedTool,
        _error,
    ) { status, results, input, tool, error ->
        AgentUiState(
            status = status,
            recentResults = results,
            availableTools = orchestrator.availableTools,
            manualInput = input,
            selectedTool = tool,
            errorMessage = error,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = AgentUiState(availableTools = orchestrator.availableTools),
    )

    fun onManualInput(text: String) {
        _manualInput.value = text
    }

    fun onToolSelected(tool: String?) {
        _selectedTool.value = tool
    }

    fun executeManualTask() {
        val tool = _selectedTool.value ?: return
        val input = _manualInput.value.trim().ifEmpty { return }

        viewModelScope.launch {
            _error.value = null
            when (val result = orchestrator.execute(tool, mapOf("input" to input, "query" to input, "expression" to input))) {
                is KidResult.Error -> _error.value = result.message
                is KidResult.Success -> { /* success handled via recentResults StateFlow */ }
            }
            _manualInput.value = ""
        }
    }

    fun dismissError() {
        _error.value = null
    }
}
