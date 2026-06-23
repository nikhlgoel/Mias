package dev.mias.core.resilience

import dev.mias.core.common.MiasResult
import dev.mias.core.common.di.IoDispatcher
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * In-memory priority queue for deferrable operations (network uploads, retry
 * of failed actions, …).
 *
 * Semantics:
 *  - Operations marked [QueuedOperation.requiresNetwork] wait while offline and
 *    are **drained automatically when connectivity returns** (the queue
 *    observes [ConnectivityMonitor]).
 *  - Each operation runs through [RetryExecutor] with its own [RetryConfig];
 *    the final outcome (success or exhausted retries) is delivered to the
 *    enqueuer's `onResult` callback — never silently dropped.
 *  - [Priority] orders *dispatch* (CRITICAL first); ready operations execute
 *    concurrently, so it does not guarantee completion order.
 *  - The queue is in-memory only: operations do not survive a process restart.
 *    Callers needing durability must persist their own state and re-enqueue.
 */
@Singleton
class OperationQueue @Inject constructor(
    private val retryExecutor: RetryExecutor,
    private val connectivityMonitor: ConnectivityMonitor,
    @IoDispatcher ioDispatcher: CoroutineDispatcher,
) {
    private val scope = CoroutineScope(SupervisorJob() + ioDispatcher)
    private val mutex = Mutex()
    private val pending = mutableListOf<QueuedOperation>()

    private val _queueSize = MutableStateFlow(0)
    val queueSize: StateFlow<Int> = _queueSize.asStateFlow()

    init {
        // Without this, an operation enqueued while offline would sit in
        // `pending` forever — nothing else re-triggers processing when the
        // network comes back.
        scope.launch {
            connectivityMonitor.observe().collect { state ->
                if (state.isConnected) processQueue()
            }
        }
    }

    /**
     * Enqueue an operation. It runs as soon as its conditions are met
     * (connectivity for network ops; immediately otherwise).
     *
     * @param onResult invoked exactly once with the terminal outcome — Success
     *   after the block completes, or Error once retries are exhausted.
     * @return an id usable with [cancel] while the operation is still pending.
     */
    suspend fun enqueue(
        tag: String,
        priority: Priority = Priority.NORMAL,
        requiresNetwork: Boolean = false,
        retryConfig: RetryConfig = RetryConfig(),
        onResult: (MiasResult<Unit>) -> Unit = {},
        block: suspend () -> Unit,
    ): String {
        val id = UUID.randomUUID().toString()
        val op = QueuedOperation(id, tag, priority, requiresNetwork, retryConfig, onResult, block)

        mutex.withLock {
            pending.add(op)
            pending.sortBy { it.priority.ordinal }
            _queueSize.value = pending.size
        }

        processQueue()
        return id
    }

    /** Cancel a queued operation by ID. No-op if it already started. */
    suspend fun cancel(id: String) {
        mutex.withLock {
            pending.removeAll { it.id == id }
            _queueSize.value = pending.size
        }
    }

    /** Dispatch every operation whose conditions are currently met. */
    private fun processQueue() {
        scope.launch {
            val ops = mutex.withLock {
                val ready = pending.filter { op ->
                    !op.requiresNetwork || connectivityMonitor.isOnline
                }
                pending.removeAll(ready.toSet())
                _queueSize.value = pending.size
                ready
            }

            for (op in ops) {
                scope.launch {
                    val result = retryExecutor.withRetry(op.retryConfig) {
                        op.block()
                    }
                    // Surface the terminal outcome; a queue that swallows
                    // failures teaches callers to distrust it.
                    runCatching { op.onResult(result.unit()) }
                }
            }
        }
    }

    private fun <T> MiasResult<T>.unit(): MiasResult<Unit> = when (this) {
        is MiasResult.Success -> MiasResult.Success(Unit)
        is MiasResult.Error -> this
    }
}

enum class Priority {
    CRITICAL,
    HIGH,
    NORMAL,
    LOW,
    BACKGROUND,
}

private class QueuedOperation(
    val id: String,
    val tag: String,
    val priority: Priority,
    val requiresNetwork: Boolean,
    val retryConfig: RetryConfig,
    val onResult: (MiasResult<Unit>) -> Unit,
    val block: suspend () -> Unit,
)
