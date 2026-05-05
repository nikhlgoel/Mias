package dev.mias.core.common

import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

@DisplayName("MiasResult")
class KidResultTest {

    @Nested
    @DisplayName("Success")
    inner class SuccessTests {
        @Test
        fun `wraps data correctly`() {
            val result = MiasResult.Success("hello")
            assertThat(result.data).isEqualTo("hello")
        }

        @Test
        fun `is instance of MiasResult`() {
            val result: MiasResult<Int> = MiasResult.Success(42)
            assertThat(result).isInstanceOf(MiasResult.Success::class.java)
        }

        @Test
        fun `equality by value`() {
            val a = MiasResult.Success(1)
            val b = MiasResult.Success(1)
            assertThat(a).isEqualTo(b)
        }
    }

    @Nested
    @DisplayName("Error")
    inner class ErrorTests {
        @Test
        fun `wraps message`() {
            val result = MiasResult.Error("something failed")
            assertThat(result.message).isEqualTo("something failed")
        }

        @Test
        fun `wraps cause`() {
            val cause = RuntimeException("root")
            val result = MiasResult.Error("wrapper", cause)
            assertThat(result.cause).isEqualTo(cause)
        }

        @Test
        fun `cause defaults to null`() {
            val result = MiasResult.Error("no cause")
            assertThat(result.cause).isNull()
        }

        @Test
        fun `is instance of MiasResult`() {
            val result: MiasResult<Nothing> = MiasResult.Error("fail")
            assertThat(result).isInstanceOf(MiasResult.Error::class.java)
        }
    }

    @Test
    @DisplayName("when expression exhaustive matching")
    fun `when expression covers both branches`() {
        val result: MiasResult<String> = MiasResult.Success("data")
        val output = when (result) {
            is MiasResult.Success -> result.data
            is MiasResult.Error -> result.message
        }
        assertThat(output).isEqualTo("data")
    }
}
