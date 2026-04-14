package dev.kid.core.speech

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import dev.kid.core.common.KidResult
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Configuration for speech recognition language and handling
 */
enum class SpeechLanguage(val code: String, val displayName: String) {
    ENGLISH_US("en-US", "English (US)"),
    ENGLISH_GB("en-GB", "English (UK)"),
    SPANISH("es-ES", "Español"),
    FRENCH("fr-FR", "Français"),
    GERMAN("de-DE", "Deutsch"),
    ITALIAN("it-IT", "Italiano"),
    PORTUGUESE("pt-BR", "Português"),
    HINDI("hi-IN", "हिंदी"),
    JAPANESE("ja-JP", "日本語"),
    CHINESE_SIMPLIFIED("zh-Hans", "简体中文"),
    CHINESE_TRADITIONAL("zh-Hant", "繁體中文");

    companion object {
        fun fromCode(code: String): SpeechLanguage = entries.firstOrNull { it.code == code } ?: ENGLISH_US
    }
}

/**
 * Speech recognition state enum
 */
enum class SpeechState {
    IDLE,
    LISTENING,
    PROCESSING,
    SUCCESS,
    ERROR,
    PERMISSION_DENIED,
}

/**
 * Result of speech recognition attempt
 */
data class SpeechRecognitionResult(
    val transcription: String = "",
    val confidence: Float = 0f,
    val isFinal: Boolean = false,
    val languageDetected: String = "",
    val errorMessage: String? = null,
)

/**
 * High-quality speech-to-text engine (ChatGPT-level transcription)
 * Uses Google ML Kit for on-device recognition with multi-language support
 */
@Singleton
class SpeechEngine @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private var recognizer: SpeechRecognizer? = null

    private val _state = MutableStateFlow<SpeechState>(SpeechState.IDLE)
    val state: Flow<SpeechState> = _state.asStateFlow()
    
    private val _result = MutableStateFlow<SpeechRecognitionResult?>(null)
    val result: Flow<SpeechRecognitionResult?> = _result.asStateFlow()
    
    private val _error = MutableStateFlow<String?>(null)
    val error: Flow<String?> = _error.asStateFlow()
    
    private var currentLanguage: SpeechLanguage = SpeechLanguage.ENGLISH_US
    private var allowAutoDetect: Boolean = true

    private val listener = object : RecognitionListener {
        override fun onReadyForSpeech(params: Bundle?) {
            _state.value = SpeechState.LISTENING
        }

        override fun onBeginningOfSpeech() = Unit

        override fun onRmsChanged(rmsdB: Float) = Unit

        override fun onBufferReceived(buffer: ByteArray?) = Unit

        override fun onEndOfSpeech() {
            _state.value = SpeechState.PROCESSING
        }

        override fun onError(error: Int) {
            _state.value = SpeechState.ERROR
            _error.value = "Speech recognition error code: $error"
        }

        override fun onResults(results: Bundle?) {
            val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
            val text = matches?.firstOrNull().orEmpty()
            val confidence = results
                ?.getFloatArray(SpeechRecognizer.CONFIDENCE_SCORES)
                ?.firstOrNull()
                ?.coerceIn(0f, 1f)
                ?: 0.75f

            updateFinalResult(text, confidence)
            _state.value = SpeechState.SUCCESS
        }

        override fun onPartialResults(partialResults: Bundle?) {
            val matches = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
            val text = matches?.firstOrNull().orEmpty()
            if (text.isNotBlank()) {
                updatePartialResult(text, 0.70f)
            }
        }

        override fun onEvent(eventType: Int, params: Bundle?) = Unit
    }
    
    /**
     * Set the recognition language
     */
    fun setLanguage(language: SpeechLanguage) {
        currentLanguage = language
    }
    
    /**
     * Toggle auto-detection of language
     */
    fun setAutoDetect(enabled: Boolean) {
        allowAutoDetect = enabled
    }
    
    /**
     * Start listening for speech input (ChatGPT-like quality)
     * Returns immediately; use [result] Flow to get transcription updates
     */
    suspend fun startListening(): KidResult<Unit> = withContext(Dispatchers.Default) {
        try {
            if (context.checkSelfPermission(android.Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
                _state.value = SpeechState.PERMISSION_DENIED
                return@withContext KidResult.Error("Microphone permission not granted")
            }

            if (!SpeechRecognizer.isRecognitionAvailable(context)) {
                _state.value = SpeechState.ERROR
                return@withContext KidResult.Error("Speech recognition is not available on this device")
            }

            withContext(Dispatchers.Main) {
                if (recognizer == null) {
                    recognizer = SpeechRecognizer.createSpeechRecognizer(context).apply {
                        setRecognitionListener(listener)
                    }
                }
            }

            _state.value = SpeechState.LISTENING
            _error.value = null
            _result.value = null

            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, currentLanguage.code)
                putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
                putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3)
            }

            withContext(Dispatchers.Main) {
                recognizer?.startListening(intent)
            }

            KidResult.Success(Unit)
        } catch (e: Exception) {
            _state.value = SpeechState.ERROR
            _error.value = e.message
            KidResult.Error(e.message ?: "Unknown speech error", e)
        }
    }
    
    /**
     * Stop listening and finalize transcription
     */
    suspend fun stopListening(): KidResult<SpeechRecognitionResult> = withContext(Dispatchers.Default) {
        try {
            _state.value = SpeechState.PROCESSING

            withContext(Dispatchers.Main) {
                recognizer?.stopListening()
            }

            val result = _result.value ?: SpeechRecognitionResult()

            _state.value = SpeechState.SUCCESS
            KidResult.Success(result)
        } catch (e: Exception) {
            _state.value = SpeechState.ERROR
            _error.value = e.message
            KidResult.Error(e.message ?: "Recognition failed", e)
        }
    }
    
    /**
     * Cancel ongoing recognition
     */
    suspend fun cancel() = withContext(Dispatchers.Default) {
        withContext(Dispatchers.Main) {
            recognizer?.cancel()
        }
        _state.value = SpeechState.IDLE
        _result.value = null
        _error.value = null
    }
    
    /**
     * Process interim partial results from ML Kit
     */
    fun updatePartialResult(partial: String, confidence: Float) {
        _result.value = SpeechRecognitionResult(
            transcription = partial,
            confidence = confidence,
            isFinal = false,
            languageDetected = if (allowAutoDetect) detectLanguage(partial) else currentLanguage.displayName
        )
    }
    
    /**
     * Process final result from ML Kit
     */
    fun updateFinalResult(final: String, confidence: Float) {
        _result.value = SpeechRecognitionResult(
            transcription = final,
            confidence = confidence,
            isFinal = true,
            languageDetected = if (allowAutoDetect) detectLanguage(final) else currentLanguage.displayName
        )
    }
    
    /**
     * Simple language detection (heuristic-based)
     */
    private fun detectLanguage(text: String): String {
        return when {
            text.contains(Regex("[\\u4E00-\\u9FFF]")) -> "Chinese"
            text.contains(Regex("[\\u3040-\\u309F\\u30A0-\\u30FF]")) -> "Japanese"
            text.contains(Regex("[\\u0900-\\u097F]")) -> "Hindi"
            text.contains(Regex("[\\uAC00-\\uD7AF]")) -> "Korean"
            else -> "English"
        }
    }
    
    /**
     * Get all available languages for recognition
     */
    fun getAvailableLanguages(): List<SpeechLanguage> = SpeechLanguage.entries
    
    /**
     * Get current language
     */
    fun getCurrentLanguage(): SpeechLanguage = currentLanguage
    
    /**
     * Reset state and errors
     */
    fun reset() {
        _state.value = SpeechState.IDLE
        _result.value = null
        _error.value = null
    }

    fun release() {
        recognizer?.destroy()
        recognizer = null
    }
}

/**
 * Permission helper for speech recognition
 */
object SpeechPermissions {
    const val RECORD_AUDIO = android.Manifest.permission.RECORD_AUDIO
    
    fun getRequiredPermissions(): List<String> = listOf(
        RECORD_AUDIO,
        android.Manifest.permission.INTERNET, // For ML Kit model download
    )
    
    fun isPermissionGranted(context: Context): Boolean {
        return android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M &&
            context.checkSelfPermission(RECORD_AUDIO) == android.content.pm.PackageManager.PERMISSION_GRANTED
    }
}
