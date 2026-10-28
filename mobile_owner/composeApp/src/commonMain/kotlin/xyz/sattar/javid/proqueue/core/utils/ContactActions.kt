package xyz.sattar.javid.proqueue.core.utils

expect fun openSms(phone: String, message: String? = null)
expect fun openWhatsApp(phone: String, message: String? = null)
expect fun openTelegram(phone: String, message: String? = null)
expect fun openPhoneDial(phone: String)
expect fun openUrl(url: String)
expect fun openInstagram(username: String)
expect fun openTwitter(username: String)

fun formatPhoneNumberForAction(phone: String): String {
    // 1. Convert Persian/Arabic digits to English digits
    var formatted = phone
        .replace("۰", "0") // i18n-keep: digit table
        .replace("۱", "1") // i18n-keep: digit table
        .replace("۲", "2") // i18n-keep: digit table
        .replace("۳", "3") // i18n-keep: digit table
        .replace("۴", "4") // i18n-keep: digit table
        .replace("۵", "5") // i18n-keep: digit table
        .replace("۶", "6") // i18n-keep: digit table
        .replace("۷", "7") // i18n-keep: digit table
        .replace("۸", "8") // i18n-keep: digit table
        .replace("۹", "9") // i18n-keep: digit table

    // 2. If starts with 09, replace with +989
    if (formatted.startsWith("09")) {
        formatted = "+98" + formatted.substring(1)
    }

    return formatted
}
