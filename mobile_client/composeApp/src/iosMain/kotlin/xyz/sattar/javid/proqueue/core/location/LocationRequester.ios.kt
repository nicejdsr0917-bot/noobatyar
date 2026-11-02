package xyz.sattar.javid.proqueue.core.location

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.useContents
import platform.CoreLocation.CLLocation
import platform.CoreLocation.CLLocationManager
import platform.CoreLocation.CLLocationManagerDelegateProtocol
import platform.CoreLocation.kCLAuthorizationStatusAuthorizedAlways
import platform.CoreLocation.kCLAuthorizationStatusAuthorizedWhenInUse
import platform.CoreLocation.kCLAuthorizationStatusDenied
import platform.CoreLocation.kCLAuthorizationStatusNotDetermined
import platform.CoreLocation.kCLAuthorizationStatusRestricted
import platform.CoreLocation.kCLLocationAccuracyHundredMeters
import platform.Foundation.NSError
import platform.darwin.NSObject
import xyz.sattar.javid.proqueue.core.utils.openUrl

@Composable
actual fun rememberLocationRequester(onResult: (LocationResult) -> Unit): LocationRequester {
    val currentOnResult by rememberUpdatedState(onResult)
    return remember { IosLocationRequester { currentOnResult(it) } }
}

/**
 * CLLocationManager keeps only a weak reference to its delegate, so this
 * object holds both strongly for as long as the composable remembers it.
 * Requires NSLocationWhenInUseUsageDescription in iosApp's Info.plist.
 */
private class IosLocationRequester(
    private val onResult: (LocationResult) -> Unit,
) : LocationRequester {

    private val manager = CLLocationManager()
    private var pending = false

    private val delegate = object : NSObject(), CLLocationManagerDelegateProtocol {
        override fun locationManagerDidChangeAuthorization(manager: CLLocationManager) {
            if (!pending) return
            when (manager.authorizationStatus) {
                kCLAuthorizationStatusAuthorizedWhenInUse,
                kCLAuthorizationStatusAuthorizedAlways -> manager.requestLocation()
                kCLAuthorizationStatusDenied,
                kCLAuthorizationStatusRestricted -> finish(LocationResult.PermissionDenied)
                else -> Unit
            }
        }

        @OptIn(ExperimentalForeignApi::class)
        override fun locationManager(manager: CLLocationManager, didUpdateLocations: List<*>) {
            val location = didUpdateLocations.lastOrNull() as? CLLocation ?: return
            location.coordinate.useContents {
                finish(LocationResult.Success(latitude, longitude))
            }
        }

        override fun locationManager(manager: CLLocationManager, didFailWithError: NSError) {
            finish(LocationResult.Unavailable)
        }
    }

    init {
        manager.delegate = delegate
        manager.desiredAccuracy = kCLLocationAccuracyHundredMeters
    }

    private fun finish(result: LocationResult) {
        if (!pending) return
        pending = false
        onResult(result)
    }

    override fun request() {
        if (!CLLocationManager.locationServicesEnabled()) {
            onResult(LocationResult.Disabled)
            return
        }
        pending = true
        when (manager.authorizationStatus) {
            kCLAuthorizationStatusNotDetermined -> manager.requestWhenInUseAuthorization()
            kCLAuthorizationStatusDenied,
            kCLAuthorizationStatusRestricted -> finish(LocationResult.PermissionDenied)
            else -> manager.requestLocation()
        }
    }

    override fun isPermissionGranted(): Boolean =
        manager.authorizationStatus == kCLAuthorizationStatusAuthorizedWhenInUse ||
            manager.authorizationStatus == kCLAuthorizationStatusAuthorizedAlways

    override fun openSettings() {
        openUrl("app-settings:")
    }
}
