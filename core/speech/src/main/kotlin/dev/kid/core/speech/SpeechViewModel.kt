package dev.kid.core.speech

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.kid.core.common.KidResult
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * ViewModel for managing speech-to-text operations in UI
 */
@HiltViewModel
class SpeechViewModel @Inject constructor(
    private val speechEngine: SpeechEngine,
) : ViewModel() {
    
    private val _isListening = MutableStateFlow(false)
    val isListening: StateFlow<Boolean> = _isListening.asStateFlow()
    
    private val _transcription = MutableStateFlow("")
    val transcription: StateFlow<String> = _transcription.asStateFlow()
    
    private val _confidence = MutableStateFlow(0f)
    val confidence: StateFlow<Float> = _confidence.asStateFlow()
    
    private val _detectedLanguage = MutableStateFlow("English")
    val detectedLanguage: StateFlow<String> = _detectedLanguage.asStateFlow()
    
    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()
    
    private val _selectedLanguage = MutableStateFlow(SpeechLanguage.ENGLISH_US)
    val selectedLanguage: StateFlow<SpeechLanguage> = _selectedLanguage.asStateFlow()
    
    private val _autoDetectEnabled = MutableStateFlow(true)
    val autoDetectEnabled: StateFlow<Boolean> = _autoDetectEnabled.asStateFlow()
    
    private val _availableLanguages = MutableStateFlow<List<SpeechLanguage>>(emptyList())
    val availableLanguages: StateFlow<List<SpeechLanguage>> = _availableLanguages.asStateFlow()
    
    init {
        _availableLanguages.value = speechEngine.getAvailableLanguages()
        _selectedLanguage.value = speechEngine.getCurrentLanguage()
        
        viewModelScope.launch {
            speechEngine.state.collect { state ->
                _isListening.value = state == SpeechState.LISTENING
                if (state == SpeechState.ERROR) {
                    _error.value = "Speech recognition failed. Try again."
                }
            }
        }
        
        viewModelScope.launch {
            speechEngine.result.collect { result ->
                if (result != null) {
                    _transcription.value = result.transcription
                    _confidence.value = result.confidence
                    _detectedLanguage.value = result.languageDetected
                }
            }
        }
        
        viewModelScope.launch {
            speechEngine.error.collect { error ->
                _error.value = error
            }
        }
    }
    
    /**
     * Start listening for speech
     */
    fun startListening() {
        viewModelScope.launch {
            val result = speechEngine.startListening()
            if (result is KidResult.Error) {
                _error.value = result.message
            }
        }
    }
    
    /**
     * Stop listening and finalize transcription
     */
    fun stopListening() {
        viewModelScope.launch {
            val result = speechEngine.stopListening()
            when (result) {
                is KidResult.Success -> {
                    // Transcription is already in _transcription via Flow
                }
                is KidResult.Error -> {
                    _error.value = result.message
                }
            }
        }
    }
    
    /**
     * Cancel ongoing speech recognition
     */
    fun cancel() {
        viewModelScope.launch {
            speechEngine.cancel()
            _transcription.value = ""
            _error.value = null
        }
    }
    
    /**
     * Set the recognition language
     */
    fun setLanguage(language: SpeechLanguage) {
        _selectedLanguage.value = language
        speechEngine.setLanguage(language)
    }
    
    /**
     * Toggle auto-language detection
     */
    fun setAutoDetect(enabled: Boolean) {
        _autoDetectEnabled.value = enabled
        speechEngine.setAutoDetect(enabled)
    }
    
    /**
     * Clear current transcription
     */
    fun clearTranscription() {
        _transcription.value = ""
        _error.value = null
    }
    
    /**
     * Get transcribed text for sending as message
     */
    fun getTranscribedMessage(): String = _transcription.value

    override fun onCleared() {
        speechEngine.release()
        super.onCleared()
    }
}
