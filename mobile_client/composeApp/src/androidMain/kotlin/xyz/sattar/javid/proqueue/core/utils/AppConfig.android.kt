package xyz.sattar.javid.proqueue.core.utils

// AGP's own generated BuildConfig — set per product flavor in
// composeApp/build.gradle.kts (buildConfigField calls in defaultConfig and
// the "local" flavor), which genuinely varies by flavor. See AppConfig.kt
// for why this is not buildkonfig's BuildKonfig object.
actual object AppConfig {
    actual val baseUrl: String = xyz.sattar.javid.proqueue.BuildConfig.BASE_URL
    actual val bookingBaseUrl: String = xyz.sattar.javid.proqueue.BuildConfig.BOOKING_BASE_URL
    actual val isStoreBuild: Boolean = xyz.sattar.javid.proqueue.BuildConfig.STORE == "store"
}
