package xyz.sattar.javid.proqueue.feature.discovery

import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import xyz.sattar.javid.proqueue.domain.model.business.Business
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

enum class OpenTone { OPEN, CLOSING_SOON, CLOSED }

data class OpenStatus(val tone: OpenTone, val headline: String, val detail: String)

/** Persian weekday names indexed like Business.closedWeekdays (Monday=0 … Sunday=6). */
val WEEKDAY_NAMES = listOf("دوشنبه", "سه‌شنبه", "چهارشنبه", "پنجشنبه", "جمعه", "شنبه", "یکشنبه")

/** Saturday-first order, the way an Iranian week is read. */
val IRANIAN_WEEK_ORDER = listOf(5, 6, 0, 1, 2, 3, 4)

private const val CLOSING_SOON_MINUTES = 60

fun formatClock(hour: Int, minute: Int): String =
    "${hour.toString().padStart(2, '0')}:${minute.toString().padStart(2, '0')}".toPersianDigits()

@OptIn(ExperimentalTime::class)
fun tehranNow(): LocalDateTime = Clock.System.now().toLocalDateTime(TimeZone.of("Asia/Tehran"))

/**
 * "Open now / closes at / opens tomorrow" for a business.
 *
 * Only for claimed businesses: an imported listing's hours are the importer's
 * 9–18 placeholder, and telling someone a clinic is open on the strength of a
 * default would send them to a locked door.
 */
fun Business.openStatus(now: LocalDateTime = tehranNow()): OpenStatus? {
    if (!isClaimed) return null
    val start = workStartHour * 60 + workStartMinute
    var end = workEndHour * 60 + workEndMinute
    if (end <= start) end += 24 * 60
    val today = now.dayOfWeek.ordinal
    val nowMin = now.hour * 60 + now.minute
    val closes = formatClock(workEndHour, workEndMinute)
    val opens = formatClock(workStartHour, workStartMinute)

    if (today !in closedWeekdays) {
        if (nowMin in start until end) {
            val left = end - nowMin
            return if (left <= CLOSING_SOON_MINUTES) {
                OpenStatus(OpenTone.CLOSING_SOON, "به‌زودی بسته می‌شود", "تا $closes")
            } else {
                OpenStatus(OpenTone.OPEN, "باز است", "تا $closes")
            }
        }
        if (nowMin < start) return OpenStatus(OpenTone.CLOSED, "بسته", "امروز از $opens")
    }
    // Next working day after today.
    for (offset in 1..7) {
        val day = (today + offset) % 7
        if (day !in closedWeekdays) {
            val whenText = if (offset == 1) "فردا" else WEEKDAY_NAMES[day]
            return OpenStatus(OpenTone.CLOSED, "بسته", "$whenText از $opens")
        }
    }
    return OpenStatus(OpenTone.CLOSED, "بسته", "")
}
