package xyz.sattar.javid.proqueue.core.prefs

/** The user's last known position and when it was taken (epoch ms). */
data class SavedLocation(val latitude: Double, val longitude: Double, val savedAtMillis: Long)

/** Stored as `lat,lng,millis`; null for anything unreadable. */
fun SavedLocation.encode(): String = "$latitude,$longitude,$savedAtMillis"

fun decodeSavedLocation(raw: String?): SavedLocation? {
    val parts = raw?.split(',') ?: return null
    if (parts.size != 3) return null
    val lat = parts[0].toDoubleOrNull() ?: return null
    val lng = parts[1].toDoubleOrNull() ?: return null
    val at = parts[2].toLongOrNull() ?: return null
    if (lat !in -90.0..90.0 || lng !in -180.0..180.0) return null
    return SavedLocation(lat, lng, at)
}
