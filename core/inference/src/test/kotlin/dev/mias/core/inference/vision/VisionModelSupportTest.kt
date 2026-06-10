package dev.mias.core.inference.vision

import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test

class VisionModelSupportTest {

    @Test
    fun `task bundles are supported`() {
        assertThat(VisionModelSupport.isTaskBundle("/data/models/gemma-3n.task")).isTrue()
        assertThat(VisionModelSupport.isTaskBundle("/data/models/Gemma.TASK")).isTrue()
    }

    @Test
    fun `gguf and other formats are rejected`() {
        assertThat(VisionModelSupport.isTaskBundle("/data/models/gemma.gguf")).isFalse()
        assertThat(VisionModelSupport.isTaskBundle("/data/models/model.bin")).isFalse()
        assertThat(VisionModelSupport.isTaskBundle("/data/models/no-extension")).isFalse()
        assertThat(VisionModelSupport.isTaskBundle("")).isFalse()
    }

    @Test
    fun `trailing whitespace is tolerated`() {
        assertThat(VisionModelSupport.isTaskBundle("/data/models/x.task  ")).isTrue()
    }
}
