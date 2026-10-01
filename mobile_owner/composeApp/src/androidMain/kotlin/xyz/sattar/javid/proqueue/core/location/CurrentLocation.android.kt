package xyz.sattar.javid.proqueue.core.location

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Build
import android.os.Looper
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat

/**
 * Plain [LocationManager] rather than Google's fused location client: the
 * Bazaar and Myket builds run on phones without Play services, where the
 * fused client simply never answers.
 */
@Composable
actual fun rememberCurrentLocationRequester(): CurrentLocationRequester {
    val context = LocalContext.current
    val pending = remember { arrayOfNulls<(GeoPoint?) -> Unit>(1) }
    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { grants ->
        val callback = pending[0] ?: return@rememberLauncherForActivityResult
        pending[0] = null
        if (grants.values.any { it }) fetchLocation(context, callback) else callback(null)
    }

    return remember(launcher) {
        object : CurrentLocationRequester {
            override val isAvailable = true

            override fun request(onResult: (GeoPoint?) -> Unit) {
                if (hasLocationPermission(context)) {
                    fetchLocation(context, onResult)
                } else {
                    pending[0] = onResult
                    launcher.launch(
                        arrayOf(
                            Manifest.permission.ACCESS_FINE_LOCATION,
                            Manifest.permission.ACCESS_COARSE_LOCATION,
                        )
                    )
                }
            }
        }
    }
}

private fun hasLocationPermission(context: Context): Boolean =
    listOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION).any {
        ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
    }

private fun Location.toGeoPoint() = GeoPoint(latitude, longitude)

/**
 * A recent cached fix is good enough for "put the map near me" and answers
 * instantly; otherwise one fresh fix, network before GPS since an owner is
 * usually indoors at their business, where GPS can take a minute or never come.
 */
@SuppressLint("MissingPermission")
private fun fetchLocation(context: Context, onResult: (GeoPoint?) -> Unit) {
    val manager = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager
        ?: return onResult(null)
    val providers = buildList {
        if (Build.VERSION.SDK_INT >= 31) add(LocationManager.FUSED_PROVIDER)
        add(LocationManager.NETWORK_PROVIDER)
        add(LocationManager.GPS_PROVIDER)
    }.filter { runCatching { manager.isProviderEnabled(it) }.getOrDefault(false) }

    val lastKnown = providers
        .mapNotNull { runCatching { manager.getLastKnownLocation(it) }.getOrNull() }
        .maxByOrNull { it.time }
    if (lastKnown != null && System.currentTimeMillis() - lastKnown.time < 2 * 60 * 1000) {
        return onResult(lastKnown.toGeoPoint())
    }
    val provider = providers.firstOrNull() ?: return onResult(lastKnown?.toGeoPoint())

    if (Build.VERSION.SDK_INT >= 30) {
        manager.getCurrentLocation(provider, null, ContextCompat.getMainExecutor(context)) { location ->
            onResult(location?.toGeoPoint() ?: lastKnown?.toGeoPoint())
        }
    } else {
        @Suppress("DEPRECATION")
        manager.requestSingleUpdate(provider, object : LocationListener {
            override fun onLocationChanged(location: Location) = onResult(location.toGeoPoint())
        }, Looper.getMainLooper())
    }
}
