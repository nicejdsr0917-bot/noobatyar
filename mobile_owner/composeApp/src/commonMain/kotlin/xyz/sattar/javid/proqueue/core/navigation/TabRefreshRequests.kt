package xyz.sattar.javid.proqueue.core.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.filterNotNull

/**
 * Double-tapping a bottom-bar tab asks that tab's screen to reload.
 *
 * A pending-request slot rather than a SharedFlow: when the double-tap lands on
 * a tab that isn't open yet, the first tap only starts navigating there and the
 * screen may not be collecting by the time the second tap arrives. The request
 * waits here until the matching screen picks it up and clears it.
 */
object TabRefreshRequests {
    private val pending = MutableStateFlow<MainTab?>(null)

    fun request(tab: MainTab) {
        pending.value = tab
    }

    internal suspend fun collect(tab: MainTab, onRefresh: () -> Unit) {
        pending.filterNotNull().collect { requested ->
            if (requested == tab && pending.compareAndSet(requested, null)) onRefresh()
        }
    }
}

/** Runs [onRefresh] whenever the user double-taps [tab] in the bottom bar. */
@Composable
fun OnTabRefreshRequested(tab: MainTab, onRefresh: () -> Unit) {
    val currentOnRefresh by rememberUpdatedState(onRefresh)
    LaunchedEffect(tab) {
        TabRefreshRequests.collect(tab) { currentOnRefresh() }
    }
}
