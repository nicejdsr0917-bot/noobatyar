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

class SyncAppointmentsReplaceWindowTest {
    private val day = 86_400_000L
    private val day0 = 20_000 * day          // a UTC midnight
    private val iso = "2026-10-29T10:00:00Z"

    private val local = FakeAppointmentLocalSource()
    private var page: PaginatedResponseDto<AppointmentDto> = page(listOf(dto(1), dto(5)))

    private fun dto(id: Long, status: String = "WAITING") = AppointmentDto(
        id = id,
        visitor = VisitorDto(id = 10, fullName = "V", phoneNumber = "0912", createdAt = iso),
        appointmentDate = day0 + 3_600_000L * id,
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
            appt(1, 1, day0 + 3_600_000L),       // in window, still on the server
            appt(2, 1, day0 + 7_200_000L),       // in window, gone from the server -> stale
            appt(3, 1, day0 - 3_600_000L),       // previous day -> untouched
            appt(4, 2, day0 + 3_600_000L),       // other business -> untouched
        ).forEach { local.rows[it.id] = it }
    }

    @Test
    fun replaceWindowDropsStaleRowsOfThatBusinessAndWindowOnly() = runTest {
        seed()
        assertEquals(true, repo.syncAppointments(businessId = 1, date = day0 + 5_000, replaceWindow = true))
        assertEquals(setOf(1L, 3L, 4L, 5L), local.rows.keys)
        assertEquals(day0 to day0 + day, local.lastWindow)   // UTC day, as the server buckets it
    }

    @Test
    fun defaultStaysUpsertOnly() = runTest {
        seed()
        repo.syncAppointments(businessId = 1, date = day0)
        assertEquals(setOf(1L, 2L, 3L, 4L, 5L), local.rows.keys)
        assertEquals(0, local.replaceCalls)
    }

    @Test
    fun filteredRequestIsNeverTreatedAsAWindow() = runTest {
        seed()
        repo.syncAppointments(businessId = 1, date = day0, status = "WAITING", replaceWindow = true)
        repo.syncAppointments(businessId = 1, visitorId = 10, date = day0, replaceWindow = true)
        assertEquals(0, local.replaceCalls)
        assertEquals(setOf(1L, 2L, 3L, 4L, 5L), local.rows.keys)
    }

    @Test
    fun incompletePageOrUnboundedRequestFallsBackToUpsert() = runTest {
        seed()
        page = page(listOf(dto(1)), next = "page=2")
        repo.syncAppointments(businessId = 1, date = day0, replaceWindow = true)
        page = page(listOf(dto(1)))
        repo.syncAppointments(businessId = 1, replaceWindow = true)   // no date bound at all
        assertEquals(0, local.replaceCalls)
        assertEquals(setOf(1L, 2L, 3L, 4L), local.rows.keys)
    }

    @Test
    fun dateRangeWindowCoversWholeUtcDays() = runTest {
        seed()
        repo.syncAppointments(businessId = 1, dateFrom = day0 + 1_000, dateTo = day0 + day + 1_000, replaceWindow = true)
        assertEquals(day0 to day0 + 2 * day, local.lastWindow)
        assertNull(local.rows[2])
    }
}
