package dev.kid.core.resilience

import dev.kid.core.common.KidResult
import dev.kid.core.common.di.IoDispatcher
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.min
import kotlin.math.pow

/**
 * Configurable retry policy with exponential backoff + jitter.
 * Used across all network-dependent operations.
 */
data class RetryConfig(
    val maxAttempts: Int = 3,
    val initialDelayMs: Long = 1_000,
    val maxDelayMs: Long = 30_000,
    val backoffMultiplier: Double = 2.0,
    val jitterFraction: Double = 0.1,
    val retryOn: (Throwable) -> Boolean = { true },
)

@Singleton
class RetryExecutor @Inject constructor(
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) {
    /**
     * Execute [block] with automatic retry on failure.
     * Returns the result of the first successful attempt or the last error.
     */
    suspend fun <T> withRetry(
        config: RetryConfig = RetryConfig(),
        block: suspend (attempt: Int) -> T,
    ): KidResult<T> = withContext(ioDispatcher) {
        var lastException: Throwable? = null

        for (attempt in 1..config.maxAttempts) {
            try {
                val result = block(attempt)
                return@withContext KidResult.Success(result)
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e // Never retry cancellation
            } catch (e: Throwable) {
                lastException = e
                if (attempt == config.maxAttempts || !config.retryOn(e)) {
                    break
                }
                val baseDelay = config.initialDelayMs *
                    config.backoffMultiplier.pow((attempt - 1).toDouble())
                val jitter = baseDelay * config.jitterFraction * Math.random()
                val delayMs = min((baseDelay + jitter).toLong(), config.maxDelayMs)
                delay(delayMs)
            }
        }

        KidResult.Error(
            message = "Failed after ${config.maxAttempts} attempts: ${lastException?.message}",
            cause = lastException,
        )
    }
}
