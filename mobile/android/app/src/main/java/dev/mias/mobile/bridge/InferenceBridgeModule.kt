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
import dev.mias.core.common.model.Stimulus
import dev.mias.core.common.model.StimulusType
import dev.mias.core.inference.orchestrator.InferenceOrchestrator
import dev.mias.core.inference.react.ReActStep
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import java.util.concurrent.ConcurrentHashMap

/**
 * Streams on-device inference into JS.
 *
 * Wraps `InferenceOrchestrator.process(...): Flow<ReActStep>` and forwards every
 * step as a `MiasInference.step` device event tagged with the caller's
 * `requestId`. Token events carry **incremental deltas** (the ReAct TokenChunk
 * contract) — JS appends, it never receives cumulative text.
 *
 * Event payload: { requestId, kind, text?, tool?, from?, to? } with kind one of
 * token | thought | action | observation | final | modelSwitch | error | done.
 */
class InferenceBridgeModule(
    private val reactContext: ReactApplicationContext,
) : ReactContextBaseJavaModule(reactContext) {

    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface InferenceEntryPoint {
        fun orchestrator(): InferenceOrchestrator
    }

    private val orchestrator: InferenceOrchestrator by lazy {
        EntryPointAccessors.fromApplication(
            reactContext.applicationContext,
            InferenceEntryPoint::class.java,
        ).orchestrator()
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val jobs = ConcurrentHashMap<String, Job>()

    override fun getName(): String = NAME

    @ReactMethod
    fun warmUp(promise: Promise) {
        scope.launch {
            runCatching { orchestrator.warmUp() }
            promise.resolve(null)
        }
    }

    /**
     * Start a streamed turn. Resolves the promise immediately after launch
     * (acknowledgement); all output arrives as `step` events for [requestId].
     */
    @ReactMethod
    fun send(requestId: String, prompt: String, systemPrompt: String, promise: Promise) {
        val job = scope.launch {
            try {
                orchestrator.process(
                    stimulus = Stimulus(type = StimulusType.USER_MESSAGE, content = prompt),
                    systemPrompt = systemPrompt.ifBlank { InferenceOrchestrator.DEFAULT_SYSTEM_PROMPT },
                ).collect { step -> emitStep(requestId, step) }
                emit(requestId, "done") {}
            } catch (t: Throwable) {
                if (t !is kotlinx.coroutines.CancellationException) {
                    emit(requestId, "error") { putString("text", t.message ?: "inference failed") }
                }
            } finally {
                jobs.remove(requestId)
            }
        }
        jobs[requestId] = job
        promise.resolve(null)
    }

    /** Stop button: cancels the flow; the engine's awaitClose aborts native generation. */
    @ReactMethod
    fun stop(requestId: String) {
        jobs.remove(requestId)?.cancel()
    }

    private fun emitStep(requestId: String, step: ReActStep) {
        when (step) {
            is ReActStep.TokenChunk -> emit(requestId, "token") { putString("text", step.text) }
            is ReActStep.Thought -> emit(requestId, "thought") { putString("text", step.reasoning) }
            is ReActStep.Action -> emit(requestId, "action") {
                putString("tool", step.tool)
                putString("text", step.input.toString())
            }
            is ReActStep.Observation -> emit(requestId, "observation") { putString("text", step.result) }
            is ReActStep.FinalAnswer -> emit(requestId, "final") { putString("text", step.response) }
            is ReActStep.ModelSwitch -> emit(requestId, "modelSwitch") {
                putString("from", step.from.name)
                putString("to", step.to.name)
            }
        }
    }

    private inline fun emit(requestId: String, kind: String, fill: WritableMap.() -> Unit) {
        val map = Arguments.createMap().apply {
            putString("requestId", requestId)
            putString("kind", kind)
            fill()
        }
        reactContext
            .getJSModule(DeviceEventManagerModule.RCTDeviceEventEmitter::class.java)
            .emit(EVENT_NAME, map)
    }

    // Required for NativeEventEmitter (no-ops; RN manages listener counts in JS).
    @ReactMethod
    fun addListener(@Suppress("UNUSED_PARAMETER") eventName: String) = Unit

    @ReactMethod
    fun removeListeners(@Suppress("UNUSED_PARAMETER") count: Double) = Unit

    override fun invalidate() {
        scope.cancel()
        super.invalidate()
    }

    companion object {
        const val NAME = "MiasInference"
        const val EVENT_NAME = "MiasInference.step"
    }
}
