package xyz.sattar.javid.proqueue.core.utils

import android.content.Intent
import android.net.Uri
import androidx.core.net.toUri

private fun launchUri(uri: String): Boolean {
    val context = xyz.sattar.javid.proqueue.ProQueueApp.appContext
    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(uri)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    return try {
        context.startActivity(intent)
        true
    } catch (e: android.content.ActivityNotFoundException) {
        false
    }
}

actual fun openSms(phone: String, message: String?) {
    val context = xyz.sattar.javid.proqueue.ProQueueApp.appContext
    val uri = Uri.parse("smsto:${formatPhoneNumberForAction(phone)}")
    val intent = Intent(Intent.ACTION_SENDTO, uri).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    if (!message.isNullOrBlank()) intent.putExtra("sms_body", message)
    context.startActivity(intent)
}

actual fun openWhatsApp(phone: String, message: String?) {
    val encoded = if (message.isNullOrBlank()) "" else "?text=" + Uri.encode(message)
    launchUri("https://wa.me/${formatPhoneNumberForAction(phone)}$encoded")
}

actual fun openTelegram(phone: String, message: String?) {
    val encoded = if (message.isNullOrBlank()) "" else "?url=&text=" + Uri.encode(message)
    launchUri("https://t.me/share/url$encoded")
}

actual fun openPhoneDial(phone: String) {
    val context = xyz.sattar.javid.proqueue.ProQueueApp.appContext
    val intent = Intent(Intent.ACTION_DIAL, "tel:${formatPhoneNumberForAction(phone)}".toUri()).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    context.startActivity(intent)
}

actual fun openUrl(url: String) {
    launchUri(url)
}

actual fun openInstagram(username: String) {
    launchUri("https://instagram.com/$username")
}

actual fun openTwitter(username: String) {
    launchUri("https://twitter.com/$username")
}

actual fun openMap(latitude: Double, longitude: Double, label: String) {
    // geo: is handled by every Iranian navigation app (Neshan, Balad) as well
    // as Google Maps; the web fallback covers a phone with none installed.
    val geo = "geo:$latitude,$longitude?q=$latitude,$longitude(${Uri.encode(label)})"
    if (!launchUri(geo)) {
        launchUri("https://www.google.com/maps/search/?api=1&query=$latitude,$longitude")
    }
}

