package xyz.sattar.javid.proqueue.core.utils

import kotlin.coroutines.cancellation.CancellationException

/**
 * [runCatching] for suspending code: a [CancellationException] is rethrown
 * instead of being captured, so swallowing failures can never swallow the
 * cancellation of the surrounding scope (which leaves a cancelled coroutine
 * running on, and makes `withTimeout` / ViewModel teardown silently ineffective).
 */
inline fun <T> runCatchingCancellable(block: () -> T): Result<T> =
    try {
        Result.success(block())
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Result.failure(e)
    }
