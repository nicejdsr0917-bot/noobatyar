package xyz.sattar.javid.proqueue.core.utils

// iOS has no flavor concept, so it always uses buildkonfig's BuildKonfig
// object directly — which, since Android no longer reads it, only ever holds
// the production values declared in buildkonfig{}'s defaultConfigs. See
// AppConfig.kt for the full reasoning.
actual object AppConfig {
    actual val baseUrl: String = xyz.sattar.javid.proqueue.BuildKonfig.BASE_URL
    actual val bookingBaseUrl: String = xyz.sattar.javid.proqueue.BuildKonfig.BOOKING_BASE_URL
}
