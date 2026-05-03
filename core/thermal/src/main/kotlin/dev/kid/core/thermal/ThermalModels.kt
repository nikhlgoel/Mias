package dev.kid.core.thermal

/** Snapshot of device thermal and power state. */
data class ThermalSnapshot(
    val socTempCelsius: Float,
    val skinTempCelsius: Float,
    val batteryTempCelsius: Float,
    val batteryLevel: Int,
    val isCharging: Boolean,
    val thermalStatus: ThermalStatus,
    val timestamp: Long = System.currentTimeMillis(),
)

enum class ThermalStatus {
    NONE,
    LIGHT,
    MODERATE,
    SEVERE,
    CRITICAL,
    EMERGENCY,
    SHUTDOWN,
}

/** TAWS decision — what the orchestrator should do. */
enum class TawsAction {
    /** Continue with primary model (Gemma NPU). */
    CONTINUE_PRIMARY,

    /** Throttle primary model (reduce max tokens, slower gen). */
    THROTTLE_PRIMARY,

    /** Switch to MobileLLM survival mode. */
    SWITCH_SURVIVAL,

    /** Offload to desktop if available. */
    OFFLOAD_DESKTOP,
}
