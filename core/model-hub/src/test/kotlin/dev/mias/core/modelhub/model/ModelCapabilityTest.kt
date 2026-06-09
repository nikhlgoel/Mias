package dev.mias.core.modelhub.model

import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

@DisplayName("ModelCapability")
class ModelCapabilityTest {

    private fun card(
        parameterCount: String,
        roles: List<ModelRole> = listOf(ModelRole.CHAT),
        runtime: ModelRuntime = ModelRuntime.LLAMA_CPP,
        contextLength: Int = 4096,
    ) = ModelCard(
        id = "test",
        name = "Test Model",
        author = "tester",
        description = "",
        sizeBytes = 1_000_000L,
        quantization = "Q4_K_M",
        format = ModelFormat.GGUF,
        roles = roles,
        contextLength = contextLength,
        parameterCount = parameterCount,
        downloadUrl = "https://example.com/m.gguf",
        sha256 = "",
        license = "apache-2.0",
    )

    @Nested
    @DisplayName("parseParameterCount")
    inner class Parse {

        @Test
        fun `parses billions`() {
            assertThat(parseParameterCount("0.5B")).isEqualTo(0.5f)
            assertThat(parseParameterCount("1.5B")).isEqualTo(1.5f)
            assertThat(parseParameterCount("3.8B")).isEqualTo(3.8f)
            assertThat(parseParameterCount("7B")).isEqualTo(7f)
        }

        @Test
        fun `parses millions into billions`() {
            assertThat(parseParameterCount("270M")).isWithin(0.001f).of(0.27f)
            assertThat(parseParameterCount("500m")).isWithin(0.001f).of(0.5f)
        }

        @Test
        fun `unparseable input is zero`() {
            assertThat(parseParameterCount("")).isEqualTo(0f)
            assertThat(parseParameterCount("unknown")).isEqualTo(0f)
        }
    }

    @Nested
    @DisplayName("capabilityProfile")
    inner class Profile {

        @Test
        fun `small model cannot drive tools`() {
            val p = card("0.5B").capabilityProfile()
            assertThat(p.supportsToolCalls).isFalse()
            assertThat(p.recommendedMaxTokens).isEqualTo(512)
        }

        @Test
        fun `model at the floor can drive tools`() {
            assertThat(card("2B").capabilityProfile().supportsToolCalls).isTrue()
        }

        @Test
        fun `large model can drive tools and gets a larger budget`() {
            val p = card("4B").capabilityProfile()
            assertThat(p.supportsToolCalls).isTrue()
            assertThat(p.recommendedMaxTokens).isEqualTo(1024)
        }

        @Test
        fun `1_5B is below the floor`() {
            assertThat(card("1.5B").capabilityProfile().supportsToolCalls).isFalse()
        }

        @Test
        fun `vision requires a MediaPipe runtime`() {
            val visionTask = card(
                "4B",
                roles = listOf(ModelRole.VISION),
                runtime = ModelRuntime.GOOGLE_AI_EDGE,
            ).capabilityProfile()
            assertThat(visionTask.supportsVision).isTrue()
            // A vision model is not routed through the text tool loop.
            assertThat(visionTask.supportsToolCalls).isFalse()

            val visionGguf = card(
                "4B",
                roles = listOf(ModelRole.VISION),
                runtime = ModelRuntime.LLAMA_CPP,
            ).capabilityProfile()
            assertThat(visionGguf.supportsVision).isFalse()
        }

        @Test
        fun `context window falls back when unset`() {
            assertThat(card("4B", contextLength = 0).capabilityProfile().contextWindow)
                .isEqualTo(4096)
        }
    }
}
