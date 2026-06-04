package dev.mias.app.ui.voice

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.mias.core.common.MiasResult
import dev.mias.core.common.di.IoDispatcher
import dev.mias.core.inference.orchestrator.InferenceOrchestrator
import dev.mias.core.inference.react.ReActStep
import dev.mias.core.inference.react.ResponseSanitizer
import dev.mias.core.inference.react.StreamingReActParser
import dev.mias.core.speech.SpeechEngine
import dev.mias.core.speech.SpeechState
import dev.mias.core.speech.TtsEngine
import dev.mias.core.common.model.Personas
import dev.mias.core.common.model.Stimulus
import dev.mias.core.common.model.StimulusType
import dev.mias.core.data.preferences.MiasPreferences
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class VoiceChatViewModel @Inject constructor(
    private val speechEngine: SpeechEngine,
    private val ttsEngine: TtsEngine,
    private val orchestrator: InferenceOrchestrator,
    private val miasPreferences: MiasPreferences,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher
) : ViewModel() {

    /** Active persona's system prompt, kept in sync with the saved preference. */
    @Volatile
    private var personaPrompt: String = Personas.DEFAULT.systemPrompt

    private val _isListening = MutableStateFlow(false)
    val isListening: StateFlow<Boolean> = _isListening.asStateFlow()

    private val _transcript = MutableStateFlow("")
    val transcript: StateFlow<String> = _transcript.asStateFlow()

    private val _aiResponse = MutableStateFlow("")
    val aiResponse: StateFlow<String> = _aiResponse.asStateFlow()

    /** True while a model is generating a reply; drives the screen's status label. */
    private val _isProcessing = MutableStateFlow(false)
    val isProcessing: StateFlow<Boolean> = _isProcessing.asStateFlow()

    init {
        // Keep the voice persona in sync with the user's selection.
        viewModelScope.launch {
            miasPreferences.prefsFlow.collect { prefs ->
                personaPrompt = Personas.byId(prefs.personaId).systemPrompt
            }
        }
        // Observe SpeechEngine result directly since StateFlow casting blocked .value
        viewModelScope.launch {
            speechEngine.result.filterNotNull().collectLatest { result ->
                if (result.isFinal) {
                    val text = result.transcription
                    if (text.isNotBlank()) {
                        processUserSpeech(text)
                    }
                    // Auto-restart listening after processing
                    if (_isListening.value) {
                        speechEngine.startListening()
                    }
                } else if (result.transcription.isNotBlank()) {
                    _transcript.value = result.transcription

                    // User started speaking! Interrupt AI!
                    if (ttsEngine.isSpeaking.value) {
                        ttsEngine.stop()
                    }
                }
            }
        }

        viewModelScope.launch {
            speechEngine.state.collectLatest { state ->
                if (state == SpeechState.ERROR) {
                    if (_isListening.value) {
                        // Retry listening
                        speechEngine.startListening()
                    }
                }
            }
        }
    }

    fun toggleDeafMute() {
        if (_isListening.value) {
            stopVoiceSession()
        } else {
            startVoiceSession()
        }
    }

    private fun startVoiceSession() {
        _isListening.value = true
        viewModelScope.launch {
            speechEngine.startListening()
        }
    }

    private fun stopVoiceSession() {
        _isListening.value = false
        viewModelScope.launch {
            speechEngine.stopListening()
            ttsEngine.stop()
        }
    }

    private fun processUserSpeech(text: String) {
        _transcript.value = text
        _aiResponse.value = ""
        _isProcessing.value = true

        viewModelScope.launch {
            // Accumulate the RAW stream privately and only ever surface the
            // parsed, cleaned conversational text — never structural JSON or an
            // echoed system prompt. While the visible part is still empty the
            // screen shows its "Thinking…" state, not the raw tokens.
            val rawBuffer = StringBuilder()
            val stimulus = Stimulus(
                type = StimulusType.USER_MESSAGE,
                content = text,
            )
            try {
                orchestrator.process(stimulus, systemPrompt = personaPrompt).collect { step ->
                    when (step) {
                        is ReActStep.FinalAnswer -> {
                            val clean = stripInstructionEcho(
                                ResponseSanitizer.sanitize(step.response).chatText,
                            )
                            _aiResponse.value = clean
                            if (clean.isNotBlank()) ttsEngine.speak(clean, flush = true)
                        }
                        is ReActStep.TokenChunk -> {
                            rawBuffer.append(step.text)
                            val visible = StreamingReActParser.parse(rawBuffer.toString()).visible
                            _aiResponse.value = stripInstructionEcho(visible)
                        }
                        else -> Unit
                    }
                }
            } finally {
                _isProcessing.value = false
            }
        }
    }

    /**
     * Defensive filter: drop any line that echoes the system prompt, so the
     * persona instructions can never appear on the voice screen even if a weak
     * model regurgitates them.
     */
    private fun stripInstructionEcho(text: String): String {
        if (text.isBlank()) return text
        return text.lineSequence()
            .filterNot { line ->
                val l = line.lowercase()
                INSTRUCTION_SIGNATURES.any { it in l }
            }
            .joinToString("\n")
            .trim()
    }

    override fun onCleared() {
        super.onCleared()
        speechEngine.release()
        ttsEngine.release()
    }

    companion object {
        /** Lowercase fragments that identify a leaked system-prompt line. */
        private val INSTRUCTION_SIGNATURES = listOf(
            "you are mias",
            "personal assistant that runs",
            "runs entirely on",
            "speak with a calm",
            "think through problems",
            "trusted colleague",
            "reply directly in plain",
        )
    }
}
