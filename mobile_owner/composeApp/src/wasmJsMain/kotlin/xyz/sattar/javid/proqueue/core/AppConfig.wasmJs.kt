package xyz.sattar.javid.proqueue.core

/**
 * Picked by the page's own host rather than baked in at build time: one
 * bundle then serves both `localhost` (talking to the Django dev server, see
 * docs/ENVIRONMENTS.md) and the deployed panel on panel.noobatyar.ir, which
 * talks to production. This target can't share Android/iOS's BuildKonfig
 * value — `-Pbuildkonfig.flavor` is one value for every non-Android target.
 */
actual object AppConfig {
    actual val BASE_URL: String = if (isLocalHost()) "http://127.0.0.1:8000" else "https://api.noobatyar.ir"
    actual val BOOKING_BASE_URL: String = if (isLocalHost()) "http://127.0.0.1:3000" else "https://app.noobatyar.ir"
}

@OptIn(kotlin.js.ExperimentalWasmJsInterop::class)
@JsFun("() => ['localhost', '127.0.0.1', '[::1]'].includes(window.location.hostname)")
internal external fun isLocalHost(): Boolean
