package xyz.sattar.javid.proqueue.core.utils

import xyz.sattar.javid.proqueue.core.i18n.Str
import xyz.sattar.javid.proqueue.core.prefs.PreferencesManager

fun buildReminderMessage(
    businessId: Long,
    businessTitle: String,
    businessAddress: String = "",
    visitorName: String,
    appointmentMillis: Long,
    reminderMinutes: String = PreferencesManager.getNotificationReminderMinutes().toString(),
    serviceDuration: Int?
): String {
    val template = PreferencesManager.getMessageTemplate(businessId)
        ?: "با سلام {visitor} عزیز 🌹؛ یادآوری نوبت شما در {business} برای ساعت {time}. مدت زمان خدمت به شما حدود {duration} است. لطفاً تا {minutes} دقیقه دیگر در محل حضور داشته باشید." // i18n-keep: customer SMS text

    val date = DateTimeUtils.formatDate(appointmentMillis)
    val time = DateTimeUtils.formatTime(appointmentMillis)
    val duration = serviceDuration ?: Str.not_specified

    return template
        .replace("{visitor}", visitorName)
        .replace("{business}", businessTitle)
        .replace("{address}", businessAddress)
        .replace("{date}", date)
        .replace("{time}", time)
        .replace("{minutes}", reminderMinutes)
        .replace("{duration}", duration.toString())

}
