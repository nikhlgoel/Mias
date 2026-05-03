package dev.kid.core.common

/**
 * Sealed result type for operations across the Kid ecosystem.
 * Used by all core modules to represent success/failure without exceptions.
 */
sealed interface KidResult<out T> {
    data class Success<T>(val data: T) : KidResult<T>
    data class Error(val message: String, val cause: Throwable? = null) : KidResult<Nothing>
}
