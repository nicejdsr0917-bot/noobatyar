package xyz.sattar.javid.proqueue.core.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavController

/**
 * Makes the browser's back button (and a phone's back gesture on the web
 * panel) go back one screen in [navController] instead of leaving the site.
 * A no-op on Android/iOS, where the system back already reaches the
 * NavController.
 *
 * Call it inside each NavHost composable, after the NavHost itself.
 */
@Composable
expect fun BrowserBackBinding(navController: NavController)
