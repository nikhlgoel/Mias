package dev.mias.mobile.bridge

import com.facebook.react.bridge.Promise
import com.facebook.react.bridge.ReactApplicationContext
import com.facebook.react.bridge.ReactContextBaseJavaModule
import com.facebook.react.bridge.ReactMethod
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import dev.mias.core.thermal.TawsGovernor
import org.json.JSONObject

/** Device health snapshot (SoC/battery temperature, battery level) for the RN app. */
class ThermalBridgeModule(
    private val reactContext: ReactApplicationContext,
) : ReactContextBaseJavaModule(reactContext) {

    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface ThermalEntryPoint {
        fun governor(): TawsGovernor
    }

    private val governor by lazy {
        EntryPointAccessors.fromApplication(
            reactContext.applicationContext,
            ThermalEntryPoint::class.java,
        ).governor()
    }

    override fun getName(): String = NAME

    @ReactMethod
    fun getHealth(promise: Promise) {
        val snapshot = runCatching { governor.latestSnapshot }.getOrNull()
        val json = JSONObject()
        if (snapshot != null) {
            json.put("socTempCelsius", snapshot.socTempCelsius.toDouble())
                .put("batteryTempCelsius", snapshot.batteryTempCelsius.toDouble())
                .put("batteryLevel", snapshot.batteryLevel)
                .put("available", true)
        } else {
            json.put("available", false)
        }
        promise.resolve(json.toString())
    }

    companion object {
        const val NAME = "MiasThermal"
    }
}
