package dev.kid.core.common

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map

/** Map a successful result to another type. */
inline fun <T, R> KidResult<T>.map(transform: (T) -> R): KidResult<R> = when (this) {
    is KidResult.Success -> KidResult.Success(transform(data))
    is KidResult.Error -> this
}

/** FlatMap for chaining results. */
inline fun <T, R> KidResult<T>.flatMap(transform: (T) -> KidResult<R>): KidResult<R> =
    when (this) {
        is KidResult.Success -> transform(data)
        is KidResult.Error -> this
    }

/** Get data or a default. */
fun <T> KidResult<T>.getOrDefault(default: T): T = when (this) {
    is KidResult.Success -> data
    is KidResult.Error -> default
}

/** Get data or null. */
fun <T> KidResult<T>.getOrNull(): T? = when (this) {
    is KidResult.Success -> data
    is KidResult.Error -> null
}

/** Wrap a suspending block into a KidResult. */
suspend inline fun <T> runCatchingKid(block: () -> T): KidResult<T> =
    try {
        KidResult.Success(block())
    } catch (e: Exception) {
        KidResult.Error(e.message ?: "Unknown error", e)
    }

/** Map a Flow of T to Flow of KidResult<T>, catching errors. */
fun <T> Flow<T>.asKidResult(): Flow<KidResult<T>> =
    map<T, KidResult<T>> { KidResult.Success(it) }
        .catch { emit(KidResult.Error(it.message ?: "Unknown error", it)) }
