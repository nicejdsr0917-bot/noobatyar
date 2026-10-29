package xyz.sattar.javid.proqueue.data

import kotlinx.coroutines.test.runTest
import xyz.sattar.javid.proqueue.core.network.ApiResponse
import xyz.sattar.javid.proqueue.core.network.PaginatedResponseDto
import xyz.sattar.javid.proqueue.data.remoteDataSource.appointment.model.AppointmentDto
import xyz.sattar.javid.proqueue.data.remoteDataSource.visitor.model.VisitorDto
import xyz.sattar.javid.proqueue.data.repository.appointment.AppointmentRepositoryImpl
import xyz.sattar.javid.proqueue.domain.model.appointment.Appointment
import xyz.sattar.javid.proqueue.domain.model.appointment.AppointmentOrdering
import xyz.sattar.javid.proqueue.testsupport.FakeAppointmentLocalSource
import xyz.sattar.javid.proqueue.testsupport.FakeBusinessLocalSource
import xyz.sattar.javid.proqueue.testsupport.FakeVisitorLocalSource
import xyz.sattar.javid.proqueue.testsupport.StubAppointmentApi
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * The backend (appointment_query_view._utc_day_start) takes the UTC calendar
 * date D of the epoch-ms it is sent and returns [D 00:00, D+1 00:00) in
 * Asia/Tehran (UTC+3:30, no DST): [D-1 20:30Z, D 20:30Z).
 */
class SyncAppointmentsReplaceWindowTest {
    private val hour = 3_600_000L
    private val day = 24 * hour
    private val tehran = 3 * hour + hour / 2
    private val iso = "2026-10-29T10:00:00Z"

    private val dayD = 20_000 * day                 // UTC midnight of date D
    private val winFrom = dayD - tehran             // D 00:00 Tehran
    private val winTo = winFrom + day               // D+1 00:00 Tehran

    private val local = FakeAppointmentLocalSource()
    private var page: PaginatedResponseDto<AppointmentDto> = page(listOf(dto(1, winFrom + hour), dto(5, winFrom + 2 * hour)))

    private fun dto(id: Long, at: Long, status: String = "WAITING") = AppointmentDto(
        id = id,
        visitor = VisitorDto(id = 10, fullName = "V", phoneNumber = "0912", createdAt = iso),
        appointmentDate = at,
        serviceDuration = 30, status = status, description = null,
        createdAt = iso, updatedAt = iso,
    )

    private fun page(rows: List<AppointmentDto>, next: String? = null) =
        PaginatedResponseDto(count = rows.size, currentPage = 1, next = next, results = rows)

    private fun appt(id: Long, business: Long, at: Long) =
        Appointment(id, business, 10, at, 30, "WAITING", null, createdAt = 0, updatedAt = 0)

    private val api = object : StubAppointmentApi() {
        override suspend fun queryAppointments(
            businessId: Long, visitorId: Long?, status: String?, date: Long?, dateFrom: Long?,
            dateTo: Long?, ordering: AppointmentOrdering?, page: Int, pageSize: Int,
        ): ApiResponse<PaginatedResponseDto<AppointmentDto>> = ApiResponse.Success(this@SyncAppointmentsReplaceWindowTest.page)
    }

    private val repo = AppointmentRepositoryImpl(local, FakeBusinessLocalSource(), FakeVisitorLocalSource(), api)

    private fun seed() {
        listOf(
            appt(1, 1, winFrom + hour),          // in server window, still returned
            appt(2, 1, winFrom + 5 * hour),      // in server window, gone from response -> deleted
            appt(3, 1, winFrom - hour),          // just before the window -> kept
            appt(4, 2, winFrom + hour),          // other business -> kept
            appt(6, 1, winTo + 2 * hour),        // next Tehran day 02:00 -> kept
            appt(7, 1, winTo - hour),            // last hour of the window, missing -> deleted
        ).forEach { local.rows[it.id] = it }
    }

    @Test
    fun windowIsTheTehranDayOfTheUtcDateSent() = runTest {
        seed()
        assertEquals(true, repo.syncAppointments(businessId = 1, date = dayD + 5_000, replaceWindow = true))
        assertEquals(winFrom to winTo, local.lastWindow)
        assertEquals(setOf(1L, 3L, 4L, 5L, 6L), local.rows.keys)
    }

    @Test
    fun tehranOneAmIsThePreviousUtcDate() = runTest {
        // Tehran 01:00 of the day after D = D+1 00:00 Tehran + 1h = UTC date D (20:30Z + 1h = 21:30Z on D).
        val sent = winTo + hour
        assertEquals(dayD, sent - sent.mod(day), "sanity: same UTC date D")
        seed()
        repo.syncAppointments(businessId = 1, date = sent, replaceWindow = true)
        assertEquals(winFrom to winTo, local.lastWindow)
        assertEquals(setOf(1L, 3L, 4L, 5L, 6L), local.rows.keys)   // next-day 02:00 survives
    }

    @Test
    fun tehranMidnightSentByTheDatePickerSelectsThatUtcDateNotTheNextTehranDay() = runTest {
        // Picker sends D 00:00 Tehran = (D-1) 20:30Z, UTC date D-1 -> server window is the Tehran day of D-1.
        val sent = winFrom
        seed()
        repo.syncAppointments(businessId = 1, date = sent, replaceWindow = true)
        assertEquals(winFrom - day to winFrom, local.lastWindow)
    }

    @Test
    fun defaultStaysUpsertOnly() = runTest {
        seed()
        repo.syncAppointments(businessId = 1, date = dayD)
        assertEquals(setOf(1L, 2L, 3L, 4L, 5L, 6L, 7L), local.rows.keys)
        assertEquals(0, local.replaceCalls)
    }

    @Test
    fun filteredRequestIsNeverTreatedAsAWindow() = runTest {
        seed()
        repo.syncAppointments(businessId = 1, date = dayD, status = "WAITING", replaceWindow = true)
        repo.syncAppointments(businessId = 1, visitorId = 10, date = dayD, replaceWindow = true)
        assertEquals(0, local.replaceCalls)
    }

    @Test
    fun incompletePageOrUnboundedRequestFallsBackToUpsert() = runTest {
        seed()
        page = page(listOf(dto(1, winFrom + hour)), next = "page=2")
        repo.syncAppointments(businessId = 1, date = dayD, replaceWindow = true)
        page = page(listOf(dto(1, winFrom + hour)))
        repo.syncAppointments(businessId = 1, replaceWindow = true)
        assertEquals(0, local.replaceCalls)
        assertEquals(setOf(1L, 2L, 3L, 4L, 6L, 7L), local.rows.keys)
    }

    @Test
    fun dateRangeWindowSpansFromStartDayToEndDayInTehran() = runTest {
        seed()
        repo.syncAppointments(businessId = 1, dateFrom = dayD + 1_000, dateTo = dayD + day + 1_000, replaceWindow = true)
        assertEquals(winFrom to winTo + day, local.lastWindow)
        assertNull(local.rows[2])
    }
}
