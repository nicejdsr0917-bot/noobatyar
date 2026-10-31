package xyz.sattar.javid.proqueue.core.location

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.LifecycleResumeEffect
import kotlinx.coroutines.launch
import org.koin.compose.koinInject
import xyz.sattar.javid.proqueue.core.prefs.ClientPreferences
import xyz.sattar.javid.proqueue.core.prefs.SavedLocation
import xyz.sattar.javid.proqueue.core.utils.openMap
import kotlin.time.Clock

private data class Destination(val latitude: Double, val longitude: Double, val label: String)

/**
 * The «مسیریابی» action. Directions need the user's position, so it is asked for
 * first: if location is off, the user is told to turn it on, and nothing opens
 * while it stays off. The moment a fix arrives (the same tap, or on coming back
 * from the settings screen) the position is saved, for the next booking's travel
 * estimate, and the map app opens at the destination.
 *
 * Call this where the button is and use the returned function as its click.
 */
@Composable
fun rememberDirections(): (latitude: Double, longitude: Double, label: String) -> Unit {
    val preferences = koinInject<ClientPreferences>()
    val scope = rememberCoroutineScope()
    var pending by remember { mutableStateOf<Destination?>(null) }
    var problem by remember { mutableStateOf<LocationResult?>(null) }
    // Set when the user goes to the settings screen, so coming back retries once.
    var awaitingSettings by remember { mutableStateOf(false) }

    val locator = rememberLocationRequester { result ->
        val target = pending ?: return@rememberLocationRequester
        when (result) {
            is LocationResult.Success -> {
                scope.launch {
                    preferences.saveLocation(SavedLocation(result.latitude, result.longitude, Clock.System.now().toEpochMilliseconds()))
                }
                pending = null
                problem = null
                openMap(target.latitude, target.longitude, target.label)
            }
            else -> problem = result
        }
    }

    // Keyed on nothing: a key that changed when the flag is set would run this at once,
    // before the settings screen has even opened.
    LifecycleResumeEffect(Unit) {
        if (awaitingSettings) {
            awaitingSettings = false
            if (pending != null) locator.request()
        }
        onPauseOrDispose { }
    }

    problem?.let { shown ->
        val goToSettings = shown != LocationResult.Unavailable
        AlertDialog(
            onDismissRequest = { problem = null; pending = null },
            title = {
                Text(
                    when (shown) {
                        LocationResult.Disabled -> "موقعیت خاموش است"
                        LocationResult.PermissionDenied -> "اجازه موقعیت لازم است"
                        else -> "موقعیت پیدا نشد"
                    }
                )
            },
            text = {
                Text(
                    when (shown) {
                        LocationResult.Disabled -> "برای مسیریابی، موقعیت گوشی را روشن کنید."
                        LocationResult.PermissionDenied -> "برای مسیریابی، دسترسی به موقعیت را بدهید."
                        else -> "کمی بعد دوباره تلاش کنید."
                    }
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    problem = null
                    if (goToSettings) {
                        awaitingSettings = true
                        locator.openSettings()
                    } else {
                        locator.request()
                    }
                }) { Text(if (goToSettings) (if (shown == LocationResult.Disabled) "روشن کردن" else "تنظیمات") else "تلاش دوباره") }
            },
            dismissButton = { TextButton(onClick = { problem = null; pending = null }) { Text("فعلا نه") } },
        )
    }

    return { latitude, longitude, label ->
        pending = Destination(latitude, longitude, label)
        locator.request()
    }
}
