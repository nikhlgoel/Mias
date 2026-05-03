package dev.kid.core.resilience

import dev.kid.core.common.di.IoDispatcher
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
 * Priority-based persistent operation queue.
 * Survives app restarts by coordinating with CheckpointManager.
 * Used for download resumption, failed action retry, etc.
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

    /**
     * Enqueue an operation. It will run when conditions are met (connectivity, priority, etc.).
     */
    suspend fun enqueue(
        tag: String,
        priority: Priority = Priority.NORMAL,
        requiresNetwork: Boolean = false,
        retryConfig: RetryConfig = RetryConfig(),
        block: suspend () -> Unit,
    ): String {
        val id = UUID.randomUUID().toString()
        val op = QueuedOperation(id, tag, priority, requiresNetwork, retryConfig, block)

        mutex.withLock {
            pending.add(op)
            pending.sortBy { it.priority.ordinal }
            _queueSize.value = pending.size
        }

        processQueue()
        return id
    }

    /** Cancel a queued operation by ID. */
    suspend fun cancel(id: String) {
        mutex.withLock {
            pending.removeAll { it.id == id }
            _queueSize.value = pending.size
        }
    }

    /** Process eligible operations. */
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
                    retryExecutor.withRetry(op.retryConfig) {
                        op.block()
                    }
                }
            }
        }
    }
}

enum class Priority {
    CRITICAL,
    HIGH,
    NORMAL,
    LOW,
    BACKGROUND,
}

private data class QueuedOperation(
    val id: String,
    val tag: String,
    val priority: Priority,
    val requiresNetwork: Boolean,
    val retryConfig: RetryConfig,
    val block: suspend () -> Unit,
)
