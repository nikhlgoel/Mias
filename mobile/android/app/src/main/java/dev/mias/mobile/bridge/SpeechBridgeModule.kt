package dev.mias.mobile.bridge

import com.facebook.react.bridge.Arguments
import com.facebook.react.bridge.Promise
import com.facebook.react.bridge.ReactApplicationContext
import com.facebook.react.bridge.ReactContextBaseJavaModule
import com.facebook.react.bridge.ReactMethod
import com.facebook.react.bridge.WritableMap
import com.facebook.react.modules.core.DeviceEventManagerModule
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import dev.mias.core.speech.SpeechEngine
import dev.mias.core.speech.SpeechLanguage
import dev.mias.core.speech.TtsEngine
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Speech-to-text (Android SpeechRecognizer) + text-to-speech for the RN app.
 *
 * STT emits `MiasSpeech.event` device events: partial/final transcripts, state
 * changes, and errors, streamed from the SpeechEngine's StateFlows. The
 * recognizer itself runs on the main thread (Android requirement).
 */
class SpeechBridgeModule(
    private val reactContext: ReactApplicationContext,
) : ReactContextBaseJavaModule(reactContext) {

    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface SpeechEntryPoint {
        fun speech(): SpeechEngine
        fun tts(): TtsEngine
    }

    private val entry by lazy {
        EntryPointAccessors.fromApplication(
            reactContext.applicationContext,
            SpeechEntryPoint::class.java,
        )
    }
    private val engine get() = entry.speech()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var observing = false

    override fun getName(): String = NAME

    private fun observeOnce() {
        if (observing) return
        observing = true
        scope.launch {
            engine.result.filterNotNull().collect { r ->
                emit(if (r.isFinal) "final" else "partial") {
                    putString("text", r.transcription)
                    putDouble("confidence", r.confidence.toDouble())
                }
            }
        }
        scope.launch {
            engine.state.collect { s -> emit("state") { putString("state", s.name) } }
        }
        scope.launch {
            engine.error.filterNotNull().collect { e -> emit("error") { putString("text", e) } }
        }
    }

    @ReactMethod
    fun startListening(languageCode: String, promise: Promise) {
        observeOnce()
        if (languageCode.isNotBlank()) {
            runCatching { engine.setLanguage(SpeechLanguage.fromCode(languageCode)) }
        }
        scope.launch {
            // SpeechRecognizer must start on the main thread.
            withContext(Dispatchers.Main) { runCatching { engine.startListening() } }
            promise.resolve(null)
        }
    }

    @ReactMethod
    fun stopListening(promise: Promise) {
        scope.launch {
            val result = withContext(Dispatchers.Main) { runCatching { engine.stopListening() } }
            promise.resolve(result.getOrNull()?.let { "" } ?: "")
        }
    }

    @ReactMethod
    fun cancel(promise: Promise) {
        scope.launch {
            withContext(Dispatchers.Main) { runCatching { engine.cancel() } }
            promise.resolve(null)
        }
    }

    @ReactMethod
    fun speak(text: String, promise: Promise) {
        scope.launch {
            withContext(Dispatchers.Main) { runCatching { entry.tts().speak(text) } }
            promise.resolve(null)
        }
    }

    @ReactMethod
    fun stopSpeaking(promise: Promise) {
        scope.launch {
            withContext(Dispatchers.Main) { runCatching { entry.tts().stop() } }
            promise.resolve(null)
        }
    }

    @ReactMethod fun addListener(@Suppress("UNUSED_PARAMETER") eventName: String) = Unit
    @ReactMethod fun removeListeners(@Suppress("UNUSED_PARAMETER") count: Double) = Unit

    private inline fun emit(kind: String, fill: WritableMap.() -> Unit) {
        val map = Arguments.createMap().apply { putString("kind", kind); fill() }
        reactContext
            .getJSModule(DeviceEventManagerModule.RCTDeviceEventEmitter::class.java)
            .emit(EVENT_NAME, map)
    }

    override fun invalidate() {
        scope.cancel()
        super.invalidate()
    }

    companion object {
        const val NAME = "MiasSpeech"
        const val EVENT_NAME = "MiasSpeech.event"
    }
}
