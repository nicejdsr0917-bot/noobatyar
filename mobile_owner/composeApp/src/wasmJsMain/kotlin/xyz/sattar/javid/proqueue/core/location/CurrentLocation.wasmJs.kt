package xyz.sattar.javid.proqueue.core.location

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember

// The browser asks for permission itself on the first call. Works on https
// and on localhost only — the Geolocation API is unavailable elsewhere, and
// then the error callback answers instead.
@OptIn(kotlin.js.ExperimentalWasmJsInterop::class)
@JsFun(
    """(onOk, onErr) => {
        if (!navigator.geolocation) { onErr(); return; }
        navigator.geolocation.getCurrentPosition(
            (p) => onOk(p.coords.latitude, p.coords.longitude),
            () => onErr(),
            { enableHighAccuracy: true, timeout: 15000, maximumAge: 120000 }
        );
    }"""
)
private external fun jsCurrentPosition(onOk: (Double, Double) -> Unit, onErr: () -> Unit)

@OptIn(kotlin.js.ExperimentalWasmJsInterop::class)
@JsFun("() => !!navigator.geolocation")
private external fun jsHasGeolocation(): Boolean

@Composable
actual fun rememberCurrentLocationRequester(): CurrentLocationRequester = remember {
    object : CurrentLocationRequester {
        override val isAvailable = jsHasGeolocation()

        override fun request(onResult: (GeoPoint?) -> Unit) {
            jsCurrentPosition(
                onOk = { lat, lng -> onResult(GeoPoint(lat, lng)) },
                onErr = { onResult(null) },
            )
        }
    }
}
