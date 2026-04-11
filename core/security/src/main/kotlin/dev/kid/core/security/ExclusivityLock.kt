package dev.kid.core.security

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Exclusivity Lock — lockout mechanism after repeated auth failures.
 *
 * After [MAX_ATTEMPTS] consecutive failures, the device enters a lockout
 * period that doubles with each successive lockout. This prevents brute-force
 * attacks on biometric bypass / safe word entry.
 */
@Singleton
class ExclusivityLock @Inject constructor() {

    private val _lockState = MutableStateFlow(LockState.UNLOCKED)
    val lockState: StateFlow<LockState> = _lockState.asStateFlow()

    private var consecutiveFailures = 0
    private var lockoutCount = 0
    private var lockoutExpiresAt = 0L

    /** Record a successful authentication — resets all failure counters. */
    fun recordSuccess() {
        consecutiveFailures = 0
        lockoutCount = 0
        lockoutExpiresAt = 0L
        _lockState.value = LockState.UNLOCKED
    }

    /** Record a failed authentication attempt. */
    fun recordFailure() {
        consecutiveFailures++

        if (consecutiveFailures >= MAX_ATTEMPTS) {
            lockoutCount++
            val lockoutDurationMs = BASE_LOCKOUT_MS * (1L shl (lockoutCount - 1).coerceAtMost(6))
            lockoutExpiresAt = System.currentTimeMillis() + lockoutDurationMs
            _lockState.value = LockState.LOCKED_OUT
        }
    }

    /** Check if the lockout has expired and update state accordingly. */
    fun checkLockoutExpiry(): Boolean {
        if (_lockState.value != LockState.LOCKED_OUT) return true
        return if (System.currentTimeMillis() >= lockoutExpiresAt) {
            consecutiveFailures = 0
            _lockState.value = LockState.UNLOCKED
            true
        } else {
            false
        }
    }

    /** Get remaining lockout time in milliseconds. */
    fun remainingLockoutMs(): Long {
        if (_lockState.value != LockState.LOCKED_OUT) return 0L
        return (lockoutExpiresAt - System.currentTimeMillis()).coerceAtLeast(0L)
    }

    /** Check if currently locked out. */
    fun isLockedOut(): Boolean = _lockState.value == LockState.LOCKED_OUT && !checkLockoutExpiry()

    enum class LockState {
        /** Normal operation — authentication permitted. */
        UNLOCKED,

        /** Locked out after too many failures. */
        LOCKED_OUT,
    }

    companion object {
        const val MAX_ATTEMPTS = 3
        const val BASE_LOCKOUT_MS = 30_000L // 30 seconds base
    }
}
