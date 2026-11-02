package xyz.sattar.javid.proqueue.data

import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.plus
import xyz.sattar.javid.proqueue.data.repository.appointment.AppointmentRepositoryImpl
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * The server-day window uses a fixed UTC+3:30 so the web build needs no tz
 * database. JVM-only (it has the database) to prove that offset still equals
 * the real Asia/Tehran zone the server uses, across a whole year.
 */
class ServerZoneMatchesTehranTest {
    @Test
    fun fixedOffsetMatchesAsiaTehranThroughAYear() {
        val tehran = TimeZone.of("Asia/Tehran")
        var d = LocalDate(2026, 1, 1)
        repeat(366) {
            val noonUtc = d.atStartOfDayIn(TimeZone.UTC).toEpochMilliseconds() + 12 * 3_600_000L
            val expected = d.atStartOfDayIn(tehran).toEpochMilliseconds() to
                d.plus(1, DateTimeUnit.DAY).atStartOfDayIn(tehran).toEpochMilliseconds()
            assertEquals(expected, AppointmentRepositoryImpl.serverDayWindow(noonUtc), "on $d")
            d = d.plus(1, DateTimeUnit.DAY)
        }
    }
}
