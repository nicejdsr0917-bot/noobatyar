package xyz.sattar.javid.proqueue.core.location

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.core.location.LocationManagerCompat
import android.os.CancellationSignal
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.resume

private val LOCATION_PERMISSIONS = arrayOf(
    Manifest.permission.ACCESS_COARSE_LOCATION,
    Manifest.permission.ACCESS_FINE_LOCATION,
)

/** A cached fix younger than this is good enough to rank "near me" results. */
private const val MAX_CACHED_AGE_MS = 10 * 60 * 1000L
private const val FIX_TIMEOUT_MS = 8_000L

@Composable
actual fun rememberLocationRequester(onResult: (LocationResult) -> Unit): LocationRequester {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val currentOnResult by rememberUpdatedState(onResult)

    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { grants ->
        if (grants.values.any { it }) {
            scope.launch { currentOnResult(fetchLocation(context)) }
        } else {
            currentOnResult(LocationResult.PermissionDenied)
        }
    }

    return remember(context, launcher) {
        object : LocationRequester {
            override fun request() {
                if (hasLocationPermission(context)) {
                    scope.launch { currentOnResult(fetchLocation(context)) }
                } else {
                    launcher.launch(LOCATION_PERMISSIONS)
                }
            }

            override fun isPermissionGranted() = hasLocationPermission(context)

            override fun openSettings() {
                val intent = if (hasLocationPermission(context)) {
                    Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS)
                } else {
                    Intent(
                        Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                        Uri.fromParts("package", context.packageName, null)
                    )
                }
                context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            }
        }
    }
}

private fun hasLocationPermission(context: Context): Boolean =
    LOCATION_PERMISSIONS.any {
        ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
    }

/**
 * Plain [LocationManager] rather than Play Services' fused provider: a large
 * share of phones in Iran ship without (working) Google Play Services, and the
 * fused client simply never answers on those. Network provider first — it
 * answers in a second or two indoors, where a GPS cold start can take minutes.
 */
@SuppressLint("MissingPermission")
private suspend fun fetchLocation(context: Context): LocationResult {
    val manager = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
    if (!LocationManagerCompat.isLocationEnabled(manager)) return LocationResult.Disabled

    val enabled = listOf(LocationManager.NETWORK_PROVIDER, LocationManager.GPS_PROVIDER)
        .filter { runCatching { manager.isProviderEnabled(it) }.getOrDefault(false) }

    val cached = (enabled + LocationManager.PASSIVE_PROVIDER)
        .mapNotNull { runCatching { manager.getLastKnownLocation(it) }.getOrNull() }
        .maxByOrNull { it.time }
    if (cached != null && System.currentTimeMillis() - cached.time < MAX_CACHED_AGE_MS) {
        return cached.toResult()
    }

    for (provider in enabled) {
        val fresh = withTimeoutOrNull(FIX_TIMEOUT_MS) { currentLocation(context, manager, provider) }
        if (fresh != null) return fresh.toResult()
    }
    // A stale fix still beats nothing for "which clinics are around here".
    return cached?.toResult() ?: LocationResult.Unavailable
}

@SuppressLint("MissingPermission")
private suspend fun currentLocation(
    context: Context,
    manager: LocationManager,
    provider: String,
): Location? = suspendCancellableCoroutine { cont ->
    val signal = CancellationSignal()
    cont.invokeOnCancellation { signal.cancel() }
    LocationManagerCompat.getCurrentLocation(
        manager, provider, signal, ContextCompat.getMainExecutor(context)
    ) { location -> if (cont.isActive) cont.resume(location) }
}

private fun Location.toResult() = LocationResult.Success(latitude, longitude)
