package xyz.sattar.javid.proqueue.core.location

import androidx.compose.runtime.Composable

/** A point on the map, in degrees. */
data class GeoPoint(val latitude: Double, val longitude: Double)

/**
 * Asks the device where it is — the "my location" button on the location
 * picker. Handles the permission prompt itself where the platform has one.
 */
interface CurrentLocationRequester {
    /** False where this build has no way to ask (iOS, for now): the picker
     *  hides the button instead of offering one that never answers. */
    val isAvailable: Boolean

    /** Calls [onResult] once — with the position, or null when permission was
     *  refused, location is switched off, or no fix arrived in time. */
    fun request(onResult: (GeoPoint?) -> Unit)
}

@Composable
expect fun rememberCurrentLocationRequester(): CurrentLocationRequester
