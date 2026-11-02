package xyz.sattar.javid.proqueue.core.location

import androidx.compose.runtime.Composable

sealed interface LocationResult {
    data class Success(val latitude: Double, val longitude: Double) : LocationResult
    /** The user refused the permission (or it is blocked by policy). */
    data object PermissionDenied : LocationResult
    /** Location services are switched off for the whole device. */
    data object Disabled : LocationResult
    /** Permission is fine but no fix arrived in time (indoors, airplane mode…). */
    data object Unavailable : LocationResult
}

interface LocationRequester {
    /** Asks for permission if needed, then delivers exactly one [LocationResult]. */
    fun request()

    /** True when a fix can be taken without showing the permission dialog. */
    fun isPermissionGranted(): Boolean

    /** Opens the OS screen where the user can turn location / the permission back on. */
    fun openSettings()
}

/**
 * One-shot "where am I" for the explore screen's "near me" search.
 *
 * Deliberately not a continuous stream: the list is ranked once per search, and
 * a single coarse fix is all that needs. [onResult] is always called on the
 * main thread.
 */
@Composable
expect fun rememberLocationRequester(onResult: (LocationResult) -> Unit): LocationRequester
