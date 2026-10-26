package xyz.sattar.javid.proqueue

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import xyz.sattar.javid.proqueue.feature.welcome.WelcomeScreen
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.material3.SnackbarHostState
import androidx.compose.ui.Modifier
import kotlinx.coroutines.launch
import org.jetbrains.compose.ui.tooling.preview.Preview
import xyz.sattar.javid.proqueue.core.navigation.navHost.MainNavHost
import xyz.sattar.javid.proqueue.core.network.GlobalError
import xyz.sattar.javid.proqueue.core.network.GlobalErrorManager
import xyz.sattar.javid.proqueue.core.prefs.PreferencesManager
import xyz.sattar.javid.proqueue.core.state.ThemeStateHolder
import xyz.sattar.javid.proqueue.core.ui.components.ToastyHost
import xyz.sattar.javid.proqueue.core.ui.components.UiMessage
import xyz.sattar.javid.proqueue.core.ui.components.showToasty
import xyz.sattar.javid.proqueue.core.utils.AppInfo
import xyz.sattar.javid.proqueue.feature.version.VersionHandler
import xyz.sattar.javid.proqueue.ui.theme.AppTheme

/**
 * Consumer app root. No sign-in: browsing, saving and the hand-off to the web
 * booking page all work anonymously, which is the whole point of a public
 * directory app.
 */
@Composable
@Preview
fun App() {
    val themeMode by ThemeStateHolder.themeMode.collectAsState()
    LaunchedEffect(Unit) {
        PreferencesManager.themeMode.collect { ThemeStateHolder.setThemeMode(it) }
    }

    val scope = rememberCoroutineScope()
    val globalSnackbarHostState = remember { SnackbarHostState() }
    LaunchedEffect(Unit) {
        GlobalErrorManager.errorFlow.collect { error ->
            if (error is GlobalError.RateLimit) {
                scope.launch { globalSnackbarHostState.showToasty(UiMessage.warning(error.message)) }
            }
        }
    }

    AppTheme(themeMode = themeMode) {
        Box(modifier = Modifier.fillMaxSize()) {
            // No iOS store listing yet, so skip the update gate there.
            if (!AppInfo.isIOS) {
                VersionHandler()
            }
            var showWelcome by remember { mutableStateOf(!PreferencesManager.hasSeenWelcome()) }
            if (showWelcome) {
                WelcomeScreen(onDone = {
                    showWelcome = false
                    scope.launch { PreferencesManager.setWelcomeSeen() }
                })
            } else {
                MainNavHost()
            }
            ToastyHost(hostState = globalSnackbarHostState)
        }
    }
}
