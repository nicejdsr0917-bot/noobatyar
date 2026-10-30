package xyz.sattar.javid.proqueue.core.ui.components.map

import MapLibre.MLNNetworkConfiguration
import kotlinx.cinterop.ExperimentalForeignApi
import platform.Foundation.NSURLSessionConfiguration

@OptIn(ExperimentalForeignApi::class)
actual fun installMapAuth(apiKey: String, userAgent: String) {
    // MapLibre on iOS has no per-host hook; every map request carries the
    // header, and the only hosts in the style are Map.ir's.
    val headers = buildMap<Any?, Any?> {
        put("User-Agent", userAgent)
        if (apiKey.isNotBlank()) put("x-api-key", apiKey)
    }
    val config = NSURLSessionConfiguration.defaultSessionConfiguration
    config.HTTPAdditionalHeaders = headers
    MLNNetworkConfiguration.sharedManager.sessionConfiguration = config
}
