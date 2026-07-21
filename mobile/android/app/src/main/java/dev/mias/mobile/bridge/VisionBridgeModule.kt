package dev.mias.mobile.bridge

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
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
import dev.mias.core.common.MiasResult
import dev.mias.core.inference.vision.MediaPipeVisionEngine
import dev.mias.core.inference.vision.VisionModelSupport
import dev.mias.core.modelhub.manager.ModelManager
import dev.mias.core.modelhub.model.ModelRole
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import java.util.concurrent.ConcurrentHashMap

/**
 * On-device vision: describe/answer about an image with the installed VISION
 * model (a MediaPipe .task bundle, e.g. Gemma 3n). Streams token deltas as
 * `MiasVision.step` events, mirroring the inference module's contract.
 */
class VisionBridgeModule(
    private val reactContext: ReactApplicationContext,
) : ReactContextBaseJavaModule(reactContext) {

    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface VisionEntryPoint {
        fun visionEngine(): MediaPipeVisionEngine
        fun modelManager(): ModelManager
    }

    private val entry by lazy {
        EntryPointAccessors.fromApplication(
            reactContext.applicationContext,
            VisionEntryPoint::class.java,
        )
    }
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val jobs = ConcurrentHashMap<String, Job>()

    override fun getName(): String = NAME

    /** True if a compatible vision (.task) model is installed and assigned. */
    @ReactMethod
    fun hasVisionModel(promise: Promise) {
        scope.launch {
            val model = runCatching { entry.modelManager().getModelForRole(ModelRole.VISION) }.getOrNull()
            val ok = model != null && VisionModelSupport.isTaskBundle(model.localPath)
            promise.resolve(ok)
        }
    }

    /** Stream a description/answer for [imageUri] with [prompt]. */
    @ReactMethod
    fun describe(requestId: String, imageUri: String, prompt: String, promise: Promise) {
        val job = scope.launch {
            try {
                val model = entry.modelManager().getModelForRole(ModelRole.VISION)
                if (model == null || !VisionModelSupport.isTaskBundle(model.localPath)) {
                    emit(requestId, "error") {
                        putString(
                            "text",
                            "No vision model installed. Open Models and install a Vision (.task) bundle like Gemma 3n.",
                        )
                    }
                    return@launch
                }
                val bitmap = decodeImage(imageUri)
                if (bitmap == null) {
                    emit(requestId, "error") { putString("text", "Couldn't read that image.") }
                    return@launch
                }
                val effectivePrompt = prompt.ifBlank { "What's in this image?" }
                entry.visionEngine().processStream(model.localPath, bitmap, effectivePrompt).collect { chunk ->
                    when (chunk) {
                        is MiasResult.Success -> emit(requestId, "delta") { putString("text", chunk.data) }
                        is MiasResult.Error -> emit(requestId, "error") { putString("text", chunk.message) }
                    }
                }
                emit(requestId, "final") {}
            } catch (t: Throwable) {
                if (t !is kotlinx.coroutines.CancellationException) {
                    emit(requestId, "error") { putString("text", t.message ?: "vision failed") }
                }
            } finally {
                jobs.remove(requestId)
            }
        }
        jobs[requestId] = job
        promise.resolve(null)
    }

    @ReactMethod
    fun stop(requestId: String) {
        jobs.remove(requestId)?.cancel()
    }

    private fun decodeImage(uri: String): Bitmap? = runCatching {
        when {
            uri.startsWith("content://") ->
                reactContext.contentResolver.openInputStream(Uri.parse(uri))?.use { BitmapFactory.decodeStream(it) }
            uri.startsWith("file://") -> BitmapFactory.decodeFile(Uri.parse(uri).path)
            else -> BitmapFactory.decodeFile(uri)
        }
    }.getOrNull()

    @ReactMethod fun addListener(@Suppress("UNUSED_PARAMETER") eventName: String) = Unit
    @ReactMethod fun removeListeners(@Suppress("UNUSED_PARAMETER") count: Double) = Unit

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

    override fun invalidate() {
        scope.cancel()
        super.invalidate()
    }

    companion object {
        const val NAME = "MiasVision"
        const val EVENT_NAME = "MiasVision.step"
    }
}
