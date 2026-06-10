package dev.mias.core.inference

import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test

class SamplingProfilesTest {

    @Test
    fun `maps model families by name`() {
        assertThat(SamplingProfiles.forModel("Qwen2.5 1.5B Instruct")).isEqualTo(SamplingProfiles.QWEN)
        assertThat(SamplingProfiles.forModel("Qwen2.5 Coder 3B Instruct")).isEqualTo(SamplingProfiles.CODE)
        assertThat(SamplingProfiles.forModel("Phi-3.5 Mini Instruct")).isEqualTo(SamplingProfiles.PHI)
        assertThat(SamplingProfiles.forModel("Gemma 3n E2B")).isEqualTo(SamplingProfiles.DEFAULT)
        assertThat(SamplingProfiles.forModel("Llama 3.2 3B")).isEqualTo(SamplingProfiles.DEFAULT)
    }

    @Test
    fun `code wins over qwen when both match`() {
        // A Qwen *coder* model should get code sampling, not chat sampling.
        assertThat(SamplingProfiles.forModel("qwen2.5-coder")).isEqualTo(SamplingProfiles.CODE)
    }

    @Test
    fun `default reproduces the historical baseline`() {
        val d = SamplingProfiles.DEFAULT
        assertThat(d.temperature).isEqualTo(0.7f)
        assertThat(d.topK).isEqualTo(40)
        assertThat(d.topP).isEqualTo(0.9f)
        assertThat(d.repeatPenalty).isEqualTo(1.17f)
        assertThat(d.repeatLastN).isEqualTo(64)
    }

    @Test
    fun `code sampling is near-greedy`() {
        assertThat(SamplingProfiles.CODE.temperature).isLessThan(SamplingProfiles.DEFAULT.temperature)
    }
}
