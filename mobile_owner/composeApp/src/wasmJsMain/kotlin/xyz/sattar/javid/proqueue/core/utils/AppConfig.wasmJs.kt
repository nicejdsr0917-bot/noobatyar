package xyz.sattar.javid.proqueue.core.utils

import xyz.sattar.javid.proqueue.core.AppConfig as CoreAppConfig

/** Same values as core/AppConfig.wasmJs.kt — see there for how they're chosen. */
actual object AppConfig {
    actual val baseUrl: String = CoreAppConfig.BASE_URL
    actual val bookingBaseUrl: String = CoreAppConfig.BOOKING_BASE_URL
}
