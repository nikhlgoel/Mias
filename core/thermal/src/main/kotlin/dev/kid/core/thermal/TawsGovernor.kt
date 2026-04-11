package dev.kid.core.thermal

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/**
 * TAWS — Thermal-Aware Workload Scheduler.
 *
 * Decides which brain the InferenceOrchestrator should use based on
 * real-time thermal and battery state. Uses hysteresis to prevent
 * oscillation: switch TO survival at 42°C, back at 38°C.
 */
@Singleton
class TawsGovernor @Inject constructor(
    private val thermalMonitor: ThermalMonitor,
) {
    @Volatile
    private var inSurvivalMode = false

    /** The latest thermal snapshot, or null if unavailable. */
    val latestSnapshot: ThermalSnapshot?
        get() = try { thermalMonitor.snapshot() } catch (_: Exception) { null }

    /** Continuously observe recommended actions. */
    fun observeActions(): Flow<TawsAction> =
        thermalMonitor.observe().map { decide(it) }

    /** Decide action for a given snapshot. */
    fun decide(snapshot: ThermalSnapshot): TawsAction {
        // Emergency conditions — always offload or degrade
        if (snapshot.thermalStatus >= ThermalStatus.CRITICAL) {
            inSurvivalMode = true
            return TawsAction.SWITCH_SURVIVAL
        }

        // Power cut mode — battery dangerously low
        if (snapshot.batteryLevel < 10 && !snapshot.isCharging) {
            inSurvivalMode = true
            return TawsAction.SWITCH_SURVIVAL
        }

        // Hysteresis: enter survival at 42°C
        if (snapshot.socTempCelsius >= SURVIVAL_ENTER_TEMP) {
            inSurvivalMode = true
            return TawsAction.SWITCH_SURVIVAL
        }

        // Hysteresis: exit survival at 38°C (only if battery is OK)
        if (inSurvivalMode && snapshot.socTempCelsius < SURVIVAL_EXIT_TEMP &&
            snapshot.batteryLevel > 25
        ) {
            inSurvivalMode = false
            return TawsAction.CONTINUE_PRIMARY
        }

        // Still in survival zone (between 38-42°C while previously triggered)
        if (inSurvivalMode) {
            return TawsAction.SWITCH_SURVIVAL
        }

        // Low battery but not critical — throttle instead of switch
        if (snapshot.batteryLevel < 20 && !snapshot.isCharging) {
            return TawsAction.THROTTLE_PRIMARY
        }

        // Moderate thermal — throttle to prevent escalation
        if (snapshot.thermalStatus >= ThermalStatus.MODERATE) {
            return TawsAction.THROTTLE_PRIMARY
        }

        return TawsAction.CONTINUE_PRIMARY
    }

    /** Whether we're currently in thermal survival mode. */
    fun isInSurvivalMode(): Boolean = inSurvivalMode

    /** Force exit survival (e.g., after user acknowledges). */
    fun resetSurvivalMode() {
        inSurvivalMode = false
    }

    companion object {
        const val SURVIVAL_ENTER_TEMP = 42f
        const val SURVIVAL_EXIT_TEMP = 38f
    }
}

/** Enable comparison for ThermalStatus enum values. */
private operator fun ThermalStatus.compareTo(other: ThermalStatus): Int =
    this.ordinal.compareTo(other.ordinal)
