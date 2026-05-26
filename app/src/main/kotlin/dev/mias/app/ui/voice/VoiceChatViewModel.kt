package dev.mias.app.ui.voice

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.mias.core.common.MiasResult
import dev.mias.core.common.di.IoDispatcher
import dev.mias.core.inference.orchestrator.InferenceOrchestrator
import dev.mias.core.inference.react.ReActStep
import dev.mias.core.speech.SpeechEngine
import dev.mias.core.speech.SpeechState
import dev.mias.core.speech.TtsEngine
import dev.mias.core.common.model.Stimulus
import dev.mias.core.common.model.StimulusType
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
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher
) : ViewModel() {

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
            val responseBuilder = StringBuilder()
            val stimulus = Stimulus(
                type = StimulusType.USER_MESSAGE,
                content = text,
            )
            try {
                orchestrator.process(stimulus).collect { step ->
                    when (step) {
                        is ReActStep.FinalAnswer -> {
                            responseBuilder.append(step.response)
                            _aiResponse.value = responseBuilder.toString()
                            ttsEngine.speak(step.response, flush = true)
                        }
                        is ReActStep.TokenChunk -> {
                            responseBuilder.append(step.text)
                            _aiResponse.value = responseBuilder.toString()
                        }
                        else -> Unit
                    }
                }
            } finally {
                _isProcessing.value = false
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        speechEngine.release()
        ttsEngine.release()
    }
}
