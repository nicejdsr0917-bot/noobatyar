package xyz.sattar.javid.proqueue.core.utils

/**
 * Hardcoded to the local Django dev server for now. Android reads AGP's
 * per-flavor BuildConfig and iOS reads BuildKonfig (see AppConfig.kt), but
 * wasmJs runs in a real browser: the Android "local" flavor's `10.0.2.2` is
 * the emulator's loopback alias and resolves to nothing there, so this target
 * cannot share either of those values. `127.0.0.1` is what a browser on this
 * machine reaches the dev server at — see docs/ENVIRONMENTS.md.
 *
 * Not yet wired to `window.__NOOBATYAR_CONFIG__` for a real deployed panel
 * build (docs/OWNER_WEB_PLAN.md) — follow-up work once there is an actual
 * panel domain to point at; local dev only needs this.
 */
actual object AppConfig {
    actual val baseUrl: String = "http://127.0.0.1:8000"
    actual val bookingBaseUrl: String = "http://127.0.0.1:3000"
}
