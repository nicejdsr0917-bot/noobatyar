package xyz.sattar.javid.proqueue

import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalFontFamilyResolver
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import org.jetbrains.compose.resources.ExperimentalResourceApi
import org.jetbrains.compose.resources.preloadFont
import proqueue.composeapp.generated.resources.Res
import proqueue.composeapp.generated.resources.yakan_bold
import proqueue.composeapp.generated.resources.yakan_medium
import proqueue.composeapp.generated.resources.yekan_regular
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.window.ComposeViewport
import kotlin.js.ExperimentalWasmJsInterop
import kotlinx.browser.document
import org.koin.core.context.startKoin
import org.w3c.dom.HTMLElement
import xyz.sattar.javid.proqueue.core.i18n.AppLocale
import xyz.sattar.javid.proqueue.core.i18n.Str
import xyz.sattar.javid.proqueue.di.appModule
import xyz.sattar.javid.proqueue.di.webPlatformModule

// index.html's boot-timer: enforces the loading screen staying up for at
// least its own minimum duration regardless of how fast the wasm module
// actually loaded — see that file's inline <script> for why the timer lives
// there and not here (it has to start counting the instant the HTML parses,
// before this Kotlin has even downloaded).
@OptIn(ExperimentalWasmJsInterop::class)
@JsFun("() => { if (window.__nbHideLoading) window.__nbHideLoading(); }")
private external fun hideBootLoadingScreen()

@OptIn(ExperimentalComposeUiApi::class, ExperimentalResourceApi::class)
fun main() {
    startKoin {
        // No dbModule/roomModule here — wasmJs has no Room (see
        // docs/OWNER_WEB_PLAN.md section 5); webPlatformModule provides the
        // same *LocalSource bindings appModule expects, backed by in-memory
        // stores instead.
        modules(webPlatformModule, appModule)
    }
    // Mounted into its own #compose-root, not document.body: ComposeViewport
    // takes ownership of whatever element it's given and clears its content,
    // which would delete index.html's #compose-loading overlay the instant
    // this line runs — before the minimum-display timer got any say at all.
    ComposeViewport(document.getElementById("compose-root") as HTMLElement) {
        // The canvas has no system fonts to fall back to, so Persian text in
        // the English UI (Nunito has no Arabic script — customer and business
        // names come from the server in Persian) would draw as boxes. Load
        // Yekan up front and register it as a fallback before the first frame.
        val fallbackFonts = listOf(
            preloadFont(Res.font.yekan_regular, FontWeight.Normal),
            preloadFont(Res.font.yakan_medium, FontWeight.Medium),
            preloadFont(Res.font.yakan_bold, FontWeight.Bold),
        ).mapNotNull { it.value }
        val fontFamilyResolver = LocalFontFamilyResolver.current
        var fontsReady by remember { mutableStateOf(false) }
        LaunchedEffect(fallbackFonts.size) {
            if (fallbackFonts.size == 3) {
                fontFamilyResolver.preload(FontFamily(fallbackFonts))
                fontsReady = true
                hideBootLoadingScreen()
            }
        }
        if (!fontsReady) return@ComposeViewport

        // Keep <html lang dir> in step with the in-app language (index.html
        // ships fa/rtl), for screen readers and the browser's own UI.
        LaunchedEffect(AppLocale.language) {
            document.documentElement?.setAttribute("lang", AppLocale.language.code)
            document.documentElement?.setAttribute("dir", if (AppLocale.isRtl) "rtl" else "ltr")
            document.title = Str.web_panel_title
        }
        App()
    }
}
