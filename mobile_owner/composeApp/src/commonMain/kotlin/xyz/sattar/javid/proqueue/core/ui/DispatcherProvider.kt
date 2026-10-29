package xyz.sattar.javid.proqueue.core.ui

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers

/**
 * Where a ViewModel runs its background work. The default keeps today's
 * behaviour; tests pass one backed by a TestDispatcher.
 *
 * Only Default/Main: Dispatchers.IO does not exist on wasmJs (see
 * BaseViewModel.doAsyncTask).
 */
interface DispatcherProvider {
    val main: CoroutineDispatcher
    val default: CoroutineDispatcher
}

object DefaultDispatcherProvider : DispatcherProvider {
    override val main: CoroutineDispatcher get() = Dispatchers.Main
    override val default: CoroutineDispatcher get() = Dispatchers.Default
}
