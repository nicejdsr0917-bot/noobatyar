package xyz.sattar.javid.proqueue.core.utils

import xyz.sattar.javid.proqueue.core.i18n.AppLocale

/**
 * Converts Persian and Arabic digits in a string to English digits.
 */
fun String.toEnglishDigits(): String {
    var result = this
    val persianDigits = arrayOf("۰", "۱", "۲", "۳", "۴", "۵", "۶", "۷", "۸", "۹") // i18n-keep: digit table
    val arabicDigits = arrayOf("٠", "١", "٢", "٣", "٤", "٥", "٦", "٧", "٨", "٩") // i18n-keep: digit table
    val englishDigits = arrayOf("0", "1", "2", "3", "4", "5", "6", "7", "8", "9")

    for (i in 0..9) {
        result = result.replace(persianDigits[i], englishDigits[i])
        result = result.replace(arabicDigits[i], englishDigits[i])
    }
    return result
}

/**
 * Digits for display in the current UI language: Persian digits in the
 * Persian UI, Latin digits in the English one (also undoing Persian digits
 * that came from text). For display only — never feed the result back into
 * anything that parses numbers.
 */
fun String.toPersianDigits(): String {
    if (AppLocale.isEnglish) return toEnglishDigits()
    val persianDigits = charArrayOf('۰', '۱', '۲', '۳', '۴', '۵', '۶', '۷', '۸', '۹')
    return map { ch -> if (ch in '0'..'9') persianDigits[ch - '0'] else ch }.joinToString("")
}
