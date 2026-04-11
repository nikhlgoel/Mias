package dev.kid.core.thermal

import android.content.Context
import android.os.BatteryManager
import android.os.PowerManager
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import dev.kid.core.common.di.IoDispatcher
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Reads hardware thermal & battery telemetry from Android APIs.
 * Emits periodic [ThermalSnapshot] via [observe].
 */
@Singleton
class ThermalMonitor @Inject constructor(
    @ApplicationContext private val context: Context,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) {
    private val batteryManager by lazy {
        context.getSystemService(Context.BATTERY_SERVICE) as BatteryManager
    }
    private val powerManager by lazy {
        context.getSystemService(Context.POWER_SERVICE) as PowerManager
    }

    @Volatile
    private var currentThermalStatus: ThermalStatus = ThermalStatus.NONE

    private val thermalListener = PowerManager.OnThermalStatusChangedListener { status ->
        currentThermalStatus = mapThermalStatus(status)
    }

    fun startMonitoring() {
        powerManager.addThermalStatusListener(thermalListener)
    }

    fun stopMonitoring() {
        powerManager.removeThermalStatusListener(thermalListener)
    }

    /** Observe thermal snapshots every [intervalMs]. */
    fun observe(intervalMs: Long = 3000L): Flow<ThermalSnapshot> = flow {
        while (true) {
            emit(snapshot())
            delay(intervalMs)
        }
    }.flowOn(ioDispatcher)

    /** Take a single snapshot right now. */
    fun snapshot(): ThermalSnapshot {
        val batteryLevel = batteryManager.getIntProperty(
            BatteryManager.BATTERY_PROPERTY_CAPACITY,
        )
        val isCharging = batteryManager.isCharging
        // Battery temperature comes in tenths of degrees from BatteryManager
        val batteryTempRaw = batteryManager.getIntProperty(
            BatteryManager.BATTERY_PROPERTY_TEMPERATURE,
        )
        val batteryTemp = if (batteryTempRaw > 0) batteryTempRaw / 10f else 25f

        // SoC/skin temps — use PowerManager thermal status as proxy
        // Real per-zone temps require HardwarePropertiesManager (system apps)
        val estimatedSocTemp = estimateSocTemp(currentThermalStatus, batteryTemp)
        val estimatedSkinTemp = estimatedSocTemp - 5f

        return ThermalSnapshot(
            socTempCelsius = estimatedSocTemp,
            skinTempCelsius = estimatedSkinTemp,
            batteryTempCelsius = batteryTemp,
            batteryLevel = batteryLevel.coerceIn(0, 100),
            isCharging = isCharging,
            thermalStatus = currentThermalStatus,
        )
    }

    private fun estimateSocTemp(status: ThermalStatus, batteryTemp: Float): Float =
        when (status) {
            ThermalStatus.NONE -> (batteryTemp + 3f).coerceAtLeast(30f)
            ThermalStatus.LIGHT -> 38f.coerceAtLeast(batteryTemp + 5f)
            ThermalStatus.MODERATE -> 42f.coerceAtLeast(batteryTemp + 8f)
            ThermalStatus.SEVERE -> 48f.coerceAtLeast(batteryTemp + 12f)
            ThermalStatus.CRITICAL -> 55f
            ThermalStatus.EMERGENCY -> 60f
            ThermalStatus.SHUTDOWN -> 65f
        }

    private fun mapThermalStatus(androidStatus: Int): ThermalStatus = when (androidStatus) {
        PowerManager.THERMAL_STATUS_NONE -> ThermalStatus.NONE
        PowerManager.THERMAL_STATUS_LIGHT -> ThermalStatus.LIGHT
        PowerManager.THERMAL_STATUS_MODERATE -> ThermalStatus.MODERATE
        PowerManager.THERMAL_STATUS_SEVERE -> ThermalStatus.SEVERE
        PowerManager.THERMAL_STATUS_CRITICAL -> ThermalStatus.CRITICAL
        PowerManager.THERMAL_STATUS_EMERGENCY -> ThermalStatus.EMERGENCY
        PowerManager.THERMAL_STATUS_SHUTDOWN -> ThermalStatus.SHUTDOWN
        else -> ThermalStatus.NONE
    }
}
