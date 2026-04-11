package dev.kid.core.common

import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

@DisplayName("KidResult")
class KidResultTest {

    @Nested
    @DisplayName("Success")
    inner class SuccessTests {
        @Test
        fun `wraps data correctly`() {
            val result = KidResult.Success("hello")
            assertThat(result.data).isEqualTo("hello")
        }

        @Test
        fun `is instance of KidResult`() {
            val result: KidResult<Int> = KidResult.Success(42)
            assertThat(result).isInstanceOf(KidResult.Success::class.java)
        }

        @Test
        fun `equality by value`() {
            val a = KidResult.Success(1)
            val b = KidResult.Success(1)
            assertThat(a).isEqualTo(b)
        }
    }

    @Nested
    @DisplayName("Error")
    inner class ErrorTests {
        @Test
        fun `wraps message`() {
            val result = KidResult.Error("something failed")
            assertThat(result.message).isEqualTo("something failed")
        }

        @Test
        fun `wraps cause`() {
            val cause = RuntimeException("root")
            val result = KidResult.Error("wrapper", cause)
            assertThat(result.cause).isEqualTo(cause)
        }

        @Test
        fun `cause defaults to null`() {
            val result = KidResult.Error("no cause")
            assertThat(result.cause).isNull()
        }

        @Test
        fun `is instance of KidResult`() {
            val result: KidResult<Nothing> = KidResult.Error("fail")
            assertThat(result).isInstanceOf(KidResult.Error::class.java)
        }
    }

    @Test
    @DisplayName("when expression exhaustive matching")
    fun `when expression covers both branches`() {
        val result: KidResult<String> = KidResult.Success("data")
        val output = when (result) {
            is KidResult.Success -> result.data
            is KidResult.Error -> result.message
        }
        assertThat(output).isEqualTo("data")
    }
}
