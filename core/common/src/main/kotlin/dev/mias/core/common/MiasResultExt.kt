package dev.mias.core.common

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map

/** Map a successful result to another type. */
inline fun <T, R> MiasResult<T>.map(transform: (T) -> R): MiasResult<R> = when (this) {
    is MiasResult.Success -> MiasResult.Success(transform(data))
    is MiasResult.Error -> this
}

/** FlatMap for chaining results. */
inline fun <T, R> MiasResult<T>.flatMap(transform: (T) -> MiasResult<R>): MiasResult<R> =
    when (this) {
        is MiasResult.Success -> transform(data)
        is MiasResult.Error -> this
    }

/** Get data or a default. */
fun <T> MiasResult<T>.getOrDefault(default: T): T = when (this) {
    is MiasResult.Success -> data
    is MiasResult.Error -> default
}

/** Get data or null. */
fun <T> MiasResult<T>.getOrNull(): T? = when (this) {
    is MiasResult.Success -> data
    is MiasResult.Error -> null
}

/** Wrap a suspending block into a MiasResult. */
suspend inline fun <T> runCatchingMias(block: () -> T): MiasResult<T> =
    try {
        MiasResult.Success(block())
    } catch (e: Exception) {
        MiasResult.Error(e.message ?: "Unknown error", e)
    }

/** Map a Flow of T to Flow of MiasResult<T>, catching errors. */
fun <T> Flow<T>.asMiasResult(): Flow<MiasResult<T>> =
    map<T, MiasResult<T>> { MiasResult.Success(it) }
        .catch { emit(MiasResult.Error(it.message ?: "Unknown error", it)) }
