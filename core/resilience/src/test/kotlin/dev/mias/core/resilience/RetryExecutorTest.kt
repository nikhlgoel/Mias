package dev.mias.core.resilience

import com.google.common.truth.Truth.assertThat
import dev.mias.core.common.MiasResult
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

@DisplayName("RetryExecutor")
class RetryExecutorTest {

    @Test
    fun `returns success on first attempt without retrying`() = runTest {
        val executor = RetryExecutor(UnconfinedTestDispatcher(testScheduler))
        var calls = 0

        val result = executor.withRetry { calls++; "ok" }

        assertThat(result).isInstanceOf(MiasResult.Success::class.java)
        assertThat((result as MiasResult.Success).data).isEqualTo("ok")
        assertThat(calls).isEqualTo(1)
    }

    @Test
    fun `retries transient failures until success`() = runTest {
        val executor = RetryExecutor(UnconfinedTestDispatcher(testScheduler))
        var calls = 0

        val result = executor.withRetry(
            RetryConfig(maxAttempts = 3, initialDelayMs = 10),
        ) {
            calls++
            if (calls < 3) throw RuntimeException("transient")
            "recovered"
        }

        assertThat(result).isInstanceOf(MiasResult.Success::class.java)
        assertThat(calls).isEqualTo(3)
    }

    @Test
    fun `returns error with cause after exhausting attempts`() = runTest {
        val executor = RetryExecutor(UnconfinedTestDispatcher(testScheduler))
        var calls = 0

        val result = executor.withRetry(
            RetryConfig(maxAttempts = 2, initialDelayMs = 1),
        ) {
            calls++
            throw IllegalStateException("permanent")
        }

        assertThat(calls).isEqualTo(2)
        assertThat(result).isInstanceOf(MiasResult.Error::class.java)
        val error = result as MiasResult.Error
        assertThat(error.message).contains("2 attempts")
        assertThat(error.cause).isInstanceOf(IllegalStateException::class.java)
    }

    @Test
    fun `does not retry when retryOn rejects the failure`() = runTest {
        val executor = RetryExecutor(UnconfinedTestDispatcher(testScheduler))
        var calls = 0

        val result = executor.withRetry(
            RetryConfig(maxAttempts = 5, initialDelayMs = 1, retryOn = { false }),
        ) {
            calls++
            throw RuntimeException("not retryable")
        }

        assertThat(calls).isEqualTo(1)
        assertThat(result).isInstanceOf(MiasResult.Error::class.java)
    }

    @Test
    fun `propagates cancellation instead of converting it to an error`() = runTest {
        val executor = RetryExecutor(UnconfinedTestDispatcher(testScheduler))
        var cancelled = false

        try {
            executor.withRetry<String> { throw CancellationException("stop") }
        } catch (_: CancellationException) {
            cancelled = true
        }

        assertThat(cancelled).isTrue()
    }
}
