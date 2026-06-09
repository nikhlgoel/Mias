package dev.mias.core.inference.orchestrator

import dev.mias.core.modelhub.model.ModelCapabilityProfile
import dev.mias.core.resilience.DeviceHealth
import dev.mias.core.thermal.TawsAction

/**
 * How a turn is executed:
 *  - [AGENTIC]: the model drives a ReAct tool loop — it decides and emits
 *    tool calls itself, reads observations, and iterates. The preferred path.
 *  - [DETERMINISTIC]: the app pre-runs any needed tool and feeds the result in;
 *    the model only writes a clean answer. The safe fallback for weak models,
 *    low-tier devices, or a thermally/battery-stressed moment.
 */
enum class ExecutionMode { AGENTIC, DETERMINISTIC }

/**
 * Static hardware class, derived once from a [DeviceHealth] snapshot.
 * RAM total and core count don't change at runtime, so this is stable.
 */
enum class DeviceTier {
    LOW,
    MID,
    HIGH,
    ;

    companion object {
        /** Below this much total RAM (MB) a device is [LOW] regardless of cores. */
        const val LOW_RAM_MAX_MB = 4_096

        /** [HIGH] requires at least this much RAM (MB) and [HIGH_MIN_CORES] cores. */
        const val HIGH_RAM_MIN_MB = 8_192
        const val HIGH_MIN_CORES = 8
        const val LOW_MAX_CORES = 4

        fun from(health: DeviceHealth): DeviceTier = when {
            health.isLowRamDevice ||
                health.totalRamMb < LOW_RAM_MAX_MB ||
                health.cpuCores <= LOW_MAX_CORES -> LOW
            health.totalRamMb >= HIGH_RAM_MIN_MB && health.cpuCores >= HIGH_MIN_CORES -> HIGH
            else -> MID
        }
    }
}

/**
 * Decides [ExecutionMode] from the model, the device tier, and the live
 * thermal/battery action. Pure and side-effect free so the whole decision
 * matrix is unit-testable.
 *
 * Policy (agentic-preferred): run the agentic loop *unless* one of the guard
 * conditions forces the deterministic fallback.
 */
object ExecutionPolicy {

    fun decide(
        profile: ModelCapabilityProfile,
        deviceTier: DeviceTier,
        tawsAction: TawsAction,
    ): ExecutionMode {
        // 1. Model floor — a very small model can't drive a tool loop, ever.
        if (!profile.supportsToolCalls) return ExecutionMode.DETERMINISTIC

        // 2. Device floor — a low-tier device shouldn't pay for multi-pass agentic.
        if (deviceTier == DeviceTier.LOW) return ExecutionMode.DETERMINISTIC

        // 3. Live stress — when TAWS is throttling or in survival, collapse to a
        //    single pass to cut heat/battery/latency. Desktop offload has headroom,
        //    so it stays agentic.
        if (tawsAction == TawsAction.THROTTLE_PRIMARY || tawsAction == TawsAction.SWITCH_SURVIVAL) {
            return ExecutionMode.DETERMINISTIC
        }

        // 4. Otherwise: the preferred agentic path.
        return ExecutionMode.AGENTIC
    }

    /**
     * Whether to constrain agentic output with the GBNF grammar. Token-level
     * JSON masking is slow on weak CPUs, so it's only worth it on [DeviceTier.HIGH]
     * hardware that can absorb the cost; MID runs agentic with the lenient parser
     * + per-turn fallback instead.
     */
    fun shouldUseGrammar(mode: ExecutionMode, deviceTier: DeviceTier): Boolean =
        mode == ExecutionMode.AGENTIC && deviceTier == DeviceTier.HIGH
}
