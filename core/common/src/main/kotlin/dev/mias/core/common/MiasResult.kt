package dev.mias.core.common

/**
 * Sealed result type for operations across the Mias ecosystem.
 * Used by all core modules to represent success/failure without exceptions.
 */
sealed interface MiasResult<out T> {
    data class Success<T>(val data: T) : MiasResult<T>
    data class Error(val message: String, val cause: Throwable? = null) : MiasResult<Nothing>
}
