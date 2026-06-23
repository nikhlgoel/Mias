package dev.mias.core.resilience

import com.google.common.truth.Truth.assertThat
import dev.mias.core.common.MiasResult
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

@DisplayName("OperationQueue")
class OperationQueueTest {

    /** Queue wired to a mocked connectivity monitor with controllable state. */
    private fun TestScope.makeQueue(
        observeFlow: Flow<ConnectivityState> = emptyFlow(),
        onlineProvider: () -> Boolean = { true },
    ): OperationQueue {
        val dispatcher = UnconfinedTestDispatcher(testScheduler)
        val monitor = mockk<ConnectivityMonitor>()
        every { monitor.observe() } returns observeFlow
        every { monitor.isOnline } answers { onlineProvider() }
        return OperationQueue(
            retryExecutor = RetryExecutor(dispatcher),
            connectivityMonitor = monitor,
            ioDispatcher = dispatcher,
        )
    }

    @Test
    fun `runs an enqueued operation`() = runTest {
        val queue = makeQueue()
        var ran = false

        queue.enqueue(tag = "t") { ran = true }
        advanceUntilIdle()

        assertThat(ran).isTrue()
        assertThat(queue.queueSize.value).isEqualTo(0)
    }

    @Test
    fun `network operation waits while offline and drains on reconnect`() = runTest {
        var online = false
        val connectivity = MutableSharedFlow<ConnectivityState>()
        val queue = makeQueue(observeFlow = connectivity, onlineProvider = { online })
        var ran = false

        queue.enqueue(tag = "upload", requiresNetwork = true) { ran = true }
        advanceUntilIdle()

        // Offline: still pending, not executed, not dropped.
        assertThat(ran).isFalse()
        assertThat(queue.queueSize.value).isEqualTo(1)

        // Connectivity returns → the queue drains itself.
        online = true
        connectivity.emit(ConnectivityState(isConnected = true))
        advanceUntilIdle()

        assertThat(ran).isTrue()
        assertThat(queue.queueSize.value).isEqualTo(0)
    }

    @Test
    fun `cancel removes a pending operation`() = runTest {
        var online = false
        val queue = makeQueue(onlineProvider = { online })
        var ran = false

        val id = queue.enqueue(tag = "t", requiresNetwork = true) { ran = true }
        advanceUntilIdle()
        queue.cancel(id)

        // Even once conditions are met, the cancelled op must not run.
        online = true
        queue.enqueue(tag = "trigger") { }
        advanceUntilIdle()

        assertThat(ran).isFalse()
    }

    @Test
    fun `delivers terminal failure through onResult instead of dropping it`() = runTest {
        val queue = makeQueue()
        var outcome: MiasResult<Unit>? = null

        queue.enqueue(
            tag = "t",
            retryConfig = RetryConfig(maxAttempts = 2, initialDelayMs = 1),
            onResult = { outcome = it },
        ) {
            throw RuntimeException("boom")
        }
        advanceUntilIdle()

        assertThat(outcome).isInstanceOf(MiasResult.Error::class.java)
    }

    @Test
    fun `delivers success through onResult`() = runTest {
        val queue = makeQueue()
        var outcome: MiasResult<Unit>? = null

        queue.enqueue(tag = "t", onResult = { outcome = it }) { }
        advanceUntilIdle()

        assertThat(outcome).isInstanceOf(MiasResult.Success::class.java)
    }

    @Test
    fun `a failing operation does not affect its siblings`() = runTest {
        val queue = makeQueue()
        var secondRan = false

        queue.enqueue(tag = "bad", retryConfig = RetryConfig(maxAttempts = 1)) {
            throw RuntimeException("boom")
        }
        queue.enqueue(tag = "good") { secondRan = true }
        advanceUntilIdle()

        assertThat(secondRan).isTrue()
    }
}
