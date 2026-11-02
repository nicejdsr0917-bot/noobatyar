package xyz.sattar.javid.proqueue.feature.appointments

import xyz.sattar.javid.proqueue.data.remoteDataSource.booking.model.ClientAppointmentDto
import xyz.sattar.javid.proqueue.feature.discovery.toPersianDigits

/** The web's three tabs on «نوبت‌های من». */
enum class AppointmentTab(val label: String) {
    UPCOMING("آینده"),
    PAST("گذشته"),
    CANCELLED("لغو شده"),
}

fun tabOf(status: String): AppointmentTab = when (status) {
    "COMPLETED" -> AppointmentTab.PAST
    "CANCELLED", "NO_SHOW" -> AppointmentTab.CANCELLED
    else -> AppointmentTab.UPCOMING
}

/** The countdown chip: only on appointments that are on and still ahead. */
fun showsCountdown(status: String): Boolean = status == "WAITING" || status == "CONFIRMED"

/** «۲ روز و ۳ ساعت مانده», «۴ ساعت و ۱۰ دقیقه مانده», or the time has come. */
fun timeLeftLabel(nowMillis: Long, atMillis: Long): String {
    val minutes = (atMillis - nowMillis) / 60_000
    if (minutes <= 0) return "زمان نوبت فرا رسیده"
    val hours = minutes / 60
    val days = hours / 24
    return if (days > 0) {
        "${days.toInt().toPersianDigits()} روز و ${(hours % 24).toInt().toPersianDigits()} ساعت مانده"
    } else {
        "${hours.toInt().toPersianDigits()} ساعت و ${(minutes % 60).toInt().toPersianDigits()} دقیقه مانده"
    }
}

/** Cancellable while the status allows it and the time has not passed (the web's rule). */
fun ClientAppointmentDto.canCancelAt(nowMillis: Long): Boolean =
    status in CANCELLABLE_STATUSES && appointmentDate > nowMillis

/** How the deposit was settled, for the slip. */
fun depositMethodLabel(method: String?): String? = when (method) {
    "CARD" -> "کارت به کارت"
    "GATEWAY" -> "درگاه آنلاین"
    else -> null
}
