package xyz.sattar.javid.proqueue.core.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.navigation.NavController
import androidx.navigation.NavGraph
import kotlin.random.Random

/**
 * Mirrors the NavController's back-stack depth in the browser history, one
 * entry per level, so browser back pops one screen.
 *
 * Deliberately not navigation's own `bindToBrowserNavigation()`: that one
 * assumes a single NavController for the whole page, while this app swaps
 * three (auth / business list / main, see App.kt). After a login, history
 * still holds the auth controller's routes, and replaying one of those into
 * the main controller throws. It also rewrites the address bar to
 * fully-qualified route class names and replays them on reload, before the
 * session or the selected business is restored.
 *
 * Here every entry is tagged with this binding's token and its depth:
 *  - our token, shallower than the stack  → pop to that depth;
 *  - our token, deeper (the forward button) → there's no route to restore,
 *    so step back again;
 *  - anything else (an earlier controller, an earlier page load, or the
 *    entry before the app) → keep going back, so back from the first screen
 *    still leaves the site like it always did.
 * The app's own back arrows pop the stack directly, and the browser history
 * is rewound to match. The URL never changes.
 */
@Composable
actual fun BrowserBackBinding(navController: NavController) {
    val token = remember(navController) { "nb${Random.nextInt(0, Int.MAX_VALUE)}" }

    // Depth the browser history currently points at. Updated before any pop
    // the popstate handler makes, so the collector below doesn't mistake that
    // pop for an in-app one and rewind history a second time.
    val synced = remember(navController) { IntArray(1) }

    fun depth() = navController.currentBackStack.value.count { it.destination !is NavGraph }

    DisposableEffect(navController) {
        val handle = jsOnPopState { state ->
            val marker = parseMarker(state)
            if (marker == null || marker.first != token) {
                jsHistoryGo(-1)
                return@jsOnPopState
            }
            val target = marker.second
            val current = depth()
            when {
                target < current -> {
                    synced[0] = target
                    repeat(current - target) { navController.popBackStack() }
                }
                target > current -> jsHistoryGo(current - target)
            }
        }
        onDispose { jsOffPopState(handle) }
    }

    LaunchedEffect(navController) {
        navController.currentBackStack.collect {
            val d = depth()
            if (d == 0) return@collect
            val last = synced[0]
            when {
                // First emission for this controller: tag the entry the page
                // is on now (it may belong to a previous controller).
                last == 0 -> jsReplaceState(marker(token, d))
                d > last -> jsPushState(marker(token, d))
                // Popped in the app (its own back arrow): rewind the browser
                // too, or the next browser back would land on a dead entry.
                d < last -> jsHistoryGo(d - last)
                else -> jsReplaceState(marker(token, d))
            }
            synced[0] = d
        }
    }
}

private fun marker(token: String, depth: Int) = "$token|$depth"

private fun parseMarker(state: String): Pair<String, Int>? {
    val parts = state.split('|')
    if (parts.size != 2) return null
    val depth = parts[1].toIntOrNull() ?: return null
    return parts[0] to depth
}

// The history entries keep the current URL: pushState/replaceState without a
// url argument, so the address bar never changes.
@OptIn(kotlin.js.ExperimentalWasmJsInterop::class)
@JsFun("(s) => window.history.pushState(s, '')")
private external fun jsPushState(state: String)

@OptIn(kotlin.js.ExperimentalWasmJsInterop::class)
@JsFun("(s) => window.history.replaceState(s, '')")
private external fun jsReplaceState(state: String)

@OptIn(kotlin.js.ExperimentalWasmJsInterop::class)
@JsFun("(n) => window.history.go(n)")
private external fun jsHistoryGo(delta: Int)

@OptIn(kotlin.js.ExperimentalWasmJsInterop::class)
@JsFun(
    """(cb) => {
        const h = (e) => cb(typeof e.state === 'string' ? e.state : '');
        window.addEventListener('popstate', h);
        return h;
    }"""
)
private external fun jsOnPopState(callback: (String) -> Unit): JsAny

@OptIn(kotlin.js.ExperimentalWasmJsInterop::class)
@JsFun("(h) => window.removeEventListener('popstate', h)")
private external fun jsOffPopState(handle: JsAny)
