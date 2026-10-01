package xyz.sattar.javid.proqueue.core.location

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember

// No iOS release yet (iOS owners use the web panel), and CoreLocation needs a
// delegate plus an Info.plist usage string — not worth it until there is one.
// The picker hides its "my location" button; picking on the map still works.
@Composable
actual fun rememberCurrentLocationRequester(): CurrentLocationRequester = remember {
    object : CurrentLocationRequester {
        override val isAvailable = false
        override fun request(onResult: (GeoPoint?) -> Unit) = onResult(null)
    }
}
