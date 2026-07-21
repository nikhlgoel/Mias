package dev.mias.mobile.bridge

import com.facebook.react.bridge.Promise
import com.facebook.react.bridge.ReactApplicationContext
import com.facebook.react.bridge.ReactContextBaseJavaModule
import com.facebook.react.bridge.ReactMethod
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import dev.mias.core.data.preferences.MiasPreferences
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import org.json.JSONObject

/** App preferences (DataStore) for the RN app — same store the Kotlin app uses. */
class PrefsBridgeModule(
    private val reactContext: ReactApplicationContext,
) : ReactContextBaseJavaModule(reactContext) {

    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface PrefsEntryPoint {
        fun prefs(): MiasPreferences
    }

    private val prefs by lazy {
        EntryPointAccessors.fromApplication(
            reactContext.applicationContext,
            PrefsEntryPoint::class.java,
        ).prefs()
    }
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun getName(): String = NAME

    @ReactMethod
    fun getPrefs(promise: Promise) {
        scope.launch {
            try {
                val p = prefs.prefsFlow.first()
                promise.resolve(
                    JSONObject()
                        .put("desktopHost", p.desktopHost)
                        .put("desktopPort", p.desktopPort)
                        .put("desktopToken", p.desktopToken)
                        .put("personaId", p.personaId)
                        .put("useDocuments", p.useDocuments)
                        .toString(),
                )
            } catch (t: Throwable) {
                promise.reject("prefs_get", t.message, t)
            }
        }
    }

    @ReactMethod
    fun setDesktopEndpoint(host: String, port: Double, token: String, promise: Promise) {
        scope.launch {
            runCatching { prefs.setDesktopEndpoint(host, port.toInt(), token) }
            promise.resolve(null)
        }
    }

    @ReactMethod
    fun setPersonaId(id: String, promise: Promise) {
        scope.launch {
            runCatching { prefs.setPersonaId(id) }
            promise.resolve(null)
        }
    }

    @ReactMethod
    fun setUseDocuments(enabled: Boolean, promise: Promise) {
        scope.launch {
            runCatching { prefs.setUseDocuments(enabled) }
            promise.resolve(null)
        }
    }

    override fun invalidate() {
        scope.cancel()
        super.invalidate()
    }

    companion object {
        const val NAME = "MiasPrefs"
    }
}
