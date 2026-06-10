package dev.mias.core.inference

import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test

class InferenceErrorTest {

    @Test
    fun `classify passes through an existing InferenceError`() {
        val err = InferenceError.OutOfMemory
        assertThat(InferenceError.classify(err)).isSameInstanceAs(err)
    }

    @Test
    fun `classify maps memory failures to OutOfMemory`() {
        assertThat(InferenceError.classify(RuntimeException("std::bad_alloc")))
            .isInstanceOf(InferenceError.OutOfMemory::class.java)
        assertThat(InferenceError.classify(OutOfMemoryError("Failed to allocate")))
            .isInstanceOf(InferenceError.OutOfMemory::class.java)
    }

    @Test
    fun `classify maps corruption to a non-retryable load failure`() {
        val e = InferenceError.classify(RuntimeException("the file may be corrupt"))
        assertThat(e).isInstanceOf(InferenceError.ModelLoadFailed::class.java)
        assertThat(e.recoverable).isFalse()
    }

    @Test
    fun `classify falls back to GenerationFailed`() {
        assertThat(InferenceError.classify(RuntimeException("something odd happened")))
            .isInstanceOf(InferenceError.GenerationFailed::class.java)
    }

    @Test
    fun `recoverable flags are sensible`() {
        assertThat(InferenceError.ModelFileInvalid("/x", 0L).recoverable).isFalse()
        assertThat(InferenceError.ModelNotLoaded.recoverable).isFalse()
        assertThat(InferenceError.OutOfMemory.recoverable).isTrue()
        assertThat(InferenceError.GenerationFailed("x").recoverable).isTrue()
    }
}
