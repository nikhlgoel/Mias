package dev.mias.mobile.bridge

import com.facebook.react.bridge.Promise
import com.facebook.react.bridge.ReactApplicationContext
import com.facebook.react.bridge.ReactContextBaseJavaModule
import com.facebook.react.bridge.ReactMethod
import com.facebook.react.bridge.WritableMap
import com.facebook.react.bridge.Arguments
import com.facebook.react.modules.core.DeviceEventManagerModule
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import dev.mias.core.common.MiasResult
import dev.mias.core.modelhub.auth.HuggingFaceAuth
import dev.mias.core.modelhub.model.DownloadState
import dev.mias.core.modelhub.model.InstalledModel
import dev.mias.core.modelhub.model.ModelCard
import dev.mias.core.modelhub.model.ModelRole
import dev.mias.core.modelhub.manager.BrowseItem
import dev.mias.core.modelhub.manager.ModelManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject

/**
 * The model hub / Brain Market for the RN app: installed models, curated browse,
 * install/pause/resume/cancel with **live download-progress events**, role
 * assignment, storage, and the optional HuggingFace token.
 *
 * Download progress: `MiasModelHub.download` device events carry the whole
 * active-downloads map each time it changes (from ModelManager.activeDownloads).
 */
class ModelHubBridgeModule(
    private val reactContext: ReactApplicationContext,
) : ReactContextBaseJavaModule(reactContext) {

    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface ModelHubEntryPoint {
        fun modelManager(): ModelManager
        fun huggingFaceAuth(): HuggingFaceAuth
    }

    private val entry by lazy {
        EntryPointAccessors.fromApplication(
            reactContext.applicationContext,
            ModelHubEntryPoint::class.java,
        )
    }
    private val manager get() = entry.modelManager()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var downloadsObserved = false

    override fun getName(): String = NAME

    override fun initialize() {
        super.initialize()
        if (downloadsObserved) return
        downloadsObserved = true
        // Stream the active-downloads map to JS as it changes.
        scope.launch {
            manager.activeDownloads.collect { map ->
                val arr = JSONArray()
                for (d in map.values) arr.put(downloadJson(d))
                emit("download") { putString("downloads", arr.toString()) }
            }
        }
    }

    @ReactMethod
    fun installedModels(promise: Promise) {
        scope.launch {
            try {
                val list = manager.installedModels.first()
                val assignments = manager.roleAssignments.first()
                val arr = JSONArray()
                for (m in list) arr.put(installedJson(m, assignments))
                promise.resolve(arr.toString())
            } catch (t: Throwable) {
                promise.reject("modelhub_installed", t.message, t)
            }
        }
    }

    /** Curated + already-installed catalogue (BrowseItem). */
    @ReactMethod
    fun browseCurated(promise: Promise) {
        scope.launch {
            try {
                val items = manager.browseCurated()
                val arr = JSONArray()
                for (it in items) arr.put(browseJson(it))
                promise.resolve(arr.toString())
            } catch (t: Throwable) {
                promise.reject("modelhub_browse", t.message, t)
            }
        }
    }

    /** Install a curated model by id (looked up in the curated catalogue). */
    @ReactMethod
    fun install(modelId: String, promise: Promise) {
        scope.launch {
            try {
                val item = manager.browseCurated().firstOrNull { it.card.id == modelId }
                if (item == null) {
                    promise.reject("modelhub_install", "Unknown model id: $modelId")
                    return@launch
                }
                when (val res = manager.installModel(item.card)) {
                    is MiasResult.Success -> promise.resolve(null)
                    is MiasResult.Error -> promise.reject("modelhub_install", res.message)
                }
            } catch (t: Throwable) {
                promise.reject("modelhub_install", t.message, t)
            }
        }
    }

    @ReactMethod fun pauseDownload(modelId: String, promise: Promise) {
        scope.launch { runCatching { manager.pauseDownload(modelId) }; promise.resolve(null) }
    }

    @ReactMethod fun resumeDownload(modelId: String, promise: Promise) {
        scope.launch { runCatching { manager.resumeDownload(modelId) }; promise.resolve(null) }
    }

    @ReactMethod fun cancelDownload(modelId: String, promise: Promise) {
        scope.launch { runCatching { manager.cancelDownload(modelId) }; promise.resolve(null) }
    }

    @ReactMethod
    fun assignRole(modelId: String, role: String, promise: Promise) {
        scope.launch {
            val parsed = runCatching { ModelRole.valueOf(role) }.getOrNull()
            if (parsed == null) { promise.reject("modelhub_role", "Unknown role: $role"); return@launch }
            when (val res = manager.assignRole(modelId, parsed)) {
                is MiasResult.Success -> promise.resolve(null)
                is MiasResult.Error -> promise.reject("modelhub_role", res.message)
            }
        }
    }

    @ReactMethod
    fun uninstall(modelId: String, promise: Promise) {
        scope.launch {
            when (val res = manager.uninstallModel(modelId)) {
                is MiasResult.Success -> promise.resolve(null)
                is MiasResult.Error -> promise.reject("modelhub_uninstall", res.message)
            }
        }
    }

    @ReactMethod
    fun totalStorageUsed(promise: Promise) {
        scope.launch { promise.resolve(runCatching { manager.totalStorageUsed().toDouble() }.getOrDefault(0.0)) }
    }

    @ReactMethod
    fun setHuggingFaceToken(token: String, promise: Promise) {
        runCatching { entry.huggingFaceAuth().set(token) }
        promise.resolve(null)
    }

    @ReactMethod
    fun getHuggingFaceToken(promise: Promise) {
        promise.resolve(runCatching { entry.huggingFaceAuth().token }.getOrDefault(""))
    }

    // Required for NativeEventEmitter.
    @ReactMethod fun addListener(@Suppress("UNUSED_PARAMETER") eventName: String) = Unit
    @ReactMethod fun removeListeners(@Suppress("UNUSED_PARAMETER") count: Double) = Unit

    private fun installedJson(m: InstalledModel, assignments: Map<ModelRole, String>): JSONObject {
        val roles = JSONArray()
        for ((role, id) in assignments) if (id == m.id) roles.put(role.name)
        return JSONObject()
            .put("id", m.id)
            .put("name", m.card.name)
            .put("author", m.card.author)
            .put("sizeOnDisk", m.sizeOnDisk)
            .put("parameterCount", m.card.parameterCount)
            .put("quantization", m.card.quantization)
            .put("capabilityRoles", JSONArray(m.card.roles.map { it.name }))
            .put("assignedRoles", roles)
    }

    private fun browseJson(it: BrowseItem): JSONObject = JSONObject()
        .put("id", it.card.id)
        .put("name", it.card.name)
        .put("author", it.card.author)
        .put("description", it.card.description)
        .put("sizeBytes", it.card.sizeBytes)
        .put("parameterCount", it.card.parameterCount)
        .put("quantization", it.card.quantization)
        .put("roles", JSONArray(it.card.roles.map { r -> r.name }))
        .put("isInstalled", it.isInstalled)
        .put("isRecommendedDefault", it.card.isRecommendedDefault)

    private fun downloadJson(d: DownloadState): JSONObject = JSONObject()
        .put("modelId", d.modelId)
        .put("status", d.status.name)
        .put("bytesDownloaded", d.bytesDownloaded)
        .put("totalBytes", d.totalBytes)
        .put("speedBytesPerSec", d.speedBytesPerSec)
        .put("progress", d.progressFraction.toDouble())
        .apply { d.error?.let { put("error", it) } }

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
        const val NAME = "MiasModelHub"
        const val EVENT_NAME = "MiasModelHub.download"
    }
}
