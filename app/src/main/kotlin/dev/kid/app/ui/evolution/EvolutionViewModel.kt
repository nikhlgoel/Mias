package dev.kid.app.ui.evolution

import android.content.Context
import android.content.Intent
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import dev.kid.core.evolution.EvolutionEngine
import dev.kid.core.evolution.model.BehaviorPattern
import dev.kid.core.evolution.model.EvolutionSession
import dev.kid.core.evolution.service.EvolutionService
import dev.kid.core.evolution.service.EvolutionWorker
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class EvolutionUiState(
    val isEvolutionRunning: Boolean = false,
    val isBackgroundEnabled: Boolean = false,
    val currentSession: EvolutionSession? = null,
    val patterns: List<BehaviorPattern> = emptyList(),
    val statusMessage: String? = null,
)

@HiltViewModel
class EvolutionViewModel @Inject constructor(
    private val engine: EvolutionEngine,
    @ApplicationContext private val context: Context,
) : ViewModel() {

    private val _backgroundEnabled = MutableStateFlow(false)
    private val _statusMessage = MutableStateFlow<String?>(null)

    val uiState: StateFlow<EvolutionUiState> = combine(
        engine.isRunning,
        engine.currentSession,
        _backgroundEnabled,
        _statusMessage,
    ) { running, session, bgEnabled, status ->
        EvolutionUiState(
            isEvolutionRunning = running,
            isBackgroundEnabled = bgEnabled,
            currentSession = session,
            patterns = emptyList(),
            statusMessage = status,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = EvolutionUiState(),
    )

    fun runNow() {
        viewModelScope.launch {
            _statusMessage.value = "Starting evolution cycle…"
            val session = engine.runFullCycle()
            _statusMessage.value = if (session.errors.isEmpty()) {
                "Cycle complete — ${session.completedTasks.size} tasks done"
            } else {
                "Cycle finished with ${session.errors.size} errors"
            }
        }
    }

    fun toggleBackgroundEvolution(enable: Boolean) {
        _backgroundEnabled.value = enable
        if (enable) {
            // Schedule periodic background worker
            EvolutionWorker.scheduleIfNotRunning(context)
            // Start foreground service for "always on" mode
            val intent = Intent(context, EvolutionService::class.java)
            context.startForegroundService(intent)
            _statusMessage.value = "Background evolution enabled"
        } else {
            EvolutionWorker.cancel(context)
            val intent = Intent(context, EvolutionService::class.java)
            context.stopService(intent)
            _statusMessage.value = "Background evolution disabled"
        }
    }

    fun clearStatus() {
        _statusMessage.value = null
    }
}
