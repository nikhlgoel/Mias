package dev.mias.core.inference.orchestrator

import com.google.common.truth.Truth.assertThat
import dev.mias.core.modelhub.model.ModelCapabilityProfile
import dev.mias.core.resilience.DeviceHealth
import dev.mias.core.thermal.TawsAction
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

@DisplayName("ExecutionPolicy")
class ExecutionPolicyTest {

    private fun profile(toolCapable: Boolean) = ModelCapabilityProfile(
        paramsB = if (toolCapable) 4f else 0.5f,
        contextWindow = 4096,
        supportsToolCalls = toolCapable,
        supportsVision = false,
        recommendedMaxTokens = 1024,
    )

    @Nested
    @DisplayName("decide")
    inner class Decide {

        @Test
        fun `weak model is always deterministic`() {
            val mode = ExecutionPolicy.decide(
                profile(toolCapable = false),
                DeviceTier.HIGH,
                TawsAction.CONTINUE_PRIMARY,
            )
            assertThat(mode).isEqualTo(ExecutionMode.DETERMINISTIC)
        }

        @Test
        fun `low-tier device is deterministic even with a capable model`() {
            val mode = ExecutionPolicy.decide(
                profile(toolCapable = true),
                DeviceTier.LOW,
                TawsAction.CONTINUE_PRIMARY,
            )
            assertThat(mode).isEqualTo(ExecutionMode.DETERMINISTIC)
        }

        @Test
        fun `capable model on a healthy mid device is agentic`() {
            val mode = ExecutionPolicy.decide(
                profile(toolCapable = true),
                DeviceTier.MID,
                TawsAction.CONTINUE_PRIMARY,
            )
            assertThat(mode).isEqualTo(ExecutionMode.AGENTIC)
        }

        @Test
        fun `capable model on a high device is agentic`() {
            val mode = ExecutionPolicy.decide(
                profile(toolCapable = true),
                DeviceTier.HIGH,
                TawsAction.CONTINUE_PRIMARY,
            )
            assertThat(mode).isEqualTo(ExecutionMode.AGENTIC)
        }

        @Test
        fun `thermal throttle forces deterministic`() {
            val mode = ExecutionPolicy.decide(
                profile(toolCapable = true),
                DeviceTier.HIGH,
                TawsAction.THROTTLE_PRIMARY,
            )
            assertThat(mode).isEqualTo(ExecutionMode.DETERMINISTIC)
        }

        @Test
        fun `survival mode forces deterministic`() {
            val mode = ExecutionPolicy.decide(
                profile(toolCapable = true),
                DeviceTier.MID,
                TawsAction.SWITCH_SURVIVAL,
            )
            assertThat(mode).isEqualTo(ExecutionMode.DETERMINISTIC)
        }

        @Test
        fun `desktop offload stays agentic`() {
            val mode = ExecutionPolicy.decide(
                profile(toolCapable = true),
                DeviceTier.HIGH,
                TawsAction.OFFLOAD_DESKTOP,
            )
            assertThat(mode).isEqualTo(ExecutionMode.AGENTIC)
        }
    }

    @Nested
    @DisplayName("DeviceTier.from")
    inner class TierFrom {

        private fun health(
            totalRamMb: Int,
            cpuCores: Int,
            isLowRamDevice: Boolean = false,
        ) = DeviceHealth(
            availableRamMb = totalRamMb / 2,
            totalRamMb = totalRamMb,
            storageFreeBytes = 10_000_000_000L,
            storageUsedByModelsBytes = 0L,
            cpuCores = cpuCores,
            processId = 1,
            isLowMemory = false,
            isLowRamDevice = isLowRamDevice,
        )

        @Test
        fun `low-ram device is LOW`() {
            assertThat(DeviceTier.from(health(12_288, 8, isLowRamDevice = true)))
                .isEqualTo(DeviceTier.LOW)
        }

        @Test
        fun `under 4GB is LOW`() {
            assertThat(DeviceTier.from(health(3_072, 8))).isEqualTo(DeviceTier.LOW)
        }

        @Test
        fun `four cores or fewer is LOW`() {
            assertThat(DeviceTier.from(health(8_192, 4))).isEqualTo(DeviceTier.LOW)
        }

        @Test
        fun `6GB 8-core is MID`() {
            assertThat(DeviceTier.from(health(6_144, 8))).isEqualTo(DeviceTier.MID)
        }

        @Test
        fun `8GB 8-core is HIGH`() {
            assertThat(DeviceTier.from(health(8_192, 8))).isEqualTo(DeviceTier.HIGH)
        }
    }

    @Nested
    @DisplayName("shouldUseGrammar")
    inner class Grammar {

        @Test
        fun `grammar only on high-tier agentic`() {
            assertThat(ExecutionPolicy.shouldUseGrammar(ExecutionMode.AGENTIC, DeviceTier.HIGH)).isTrue()
            assertThat(ExecutionPolicy.shouldUseGrammar(ExecutionMode.AGENTIC, DeviceTier.MID)).isFalse()
            assertThat(ExecutionPolicy.shouldUseGrammar(ExecutionMode.DETERMINISTIC, DeviceTier.HIGH)).isFalse()
        }
    }
}
