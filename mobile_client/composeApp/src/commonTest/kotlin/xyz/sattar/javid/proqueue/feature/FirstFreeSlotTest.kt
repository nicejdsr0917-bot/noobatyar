package xyz.sattar.javid.proqueue.feature

import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import xyz.sattar.javid.proqueue.data.remoteDataSource.business.model.BusinessDto
import xyz.sattar.javid.proqueue.data.remoteDataSource.business.toDomain
import xyz.sattar.javid.proqueue.feature.discovery.formatFirstFreeSlot
import xyz.sattar.javid.proqueue.testsupport.businessDto
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class FirstFreeSlotTest {
    private val tehran = TimeZone.of("Asia/Tehran")

    private fun millis(y: Int, mo: Int, d: Int, h: Int, mi: Int) =
        LocalDateTime(y, mo, d, h, mi).toInstant(tehran).toEpochMilliseconds()

    // Friday 2026-10-30 at 08:00 Tehran.
    private val now = millis(2026, 10, 30, 8, 0)

    @Test
    fun todayIsNamedToday() {
        assertEquals("امروز ۱۰:۳۰", formatFirstFreeSlot(millis(2026, 10, 30, 10, 30), now, tehran))
    }

    @Test
    fun tomorrowIsNamedTomorrow() {
        assertEquals("فردا ۰۹:۰۰", formatFirstFreeSlot(millis(2026, 10, 31, 9, 0), now, tehran))
    }

    @Test
    fun laterDaysUseTheWeekdayName() {
        // 2026-11-02 is a Monday.
        assertEquals("دوشنبه ۱۱:۰۰", formatFirstFreeSlot(millis(2026, 11, 2, 11, 0), now, tehran))
    }

    @Test
    fun tomorrowIsCalendarBasedNotTwentyFourHours() {
        val lateNow = millis(2026, 10, 30, 23, 30)
        assertEquals("فردا ۰۰:۱۵", formatFirstFreeSlot(millis(2026, 10, 31, 0, 15), lateNow, tehran))
    }

    @Test
    fun nullOrStaleSlotsAreHidden() {
        assertNull(formatFirstFreeSlot(null, now, tehran))
        assertNull(formatFirstFreeSlot(millis(2026, 10, 29, 10, 0), now, tehran))
    }

    @Test
    fun timeIsShownInTheViewersZone() {
        val utc = TimeZone.UTC
        val slot = millis(2026, 10, 30, 10, 30) // 07:00 UTC
        assertEquals("امروز ۰۷:۰۰", formatFirstFreeSlot(slot, now, utc))
    }

    @Test
    fun mapperCarriesTheFieldAndOldServersDefaultToNull() {
        assertEquals(1234L, businessDto(1, firstFreeSlot = 1234L).toDomain().firstFreeSlot)
        assertNull(BusinessDto(id = 1, title = "x").toDomain().firstFreeSlot)
    }
}
