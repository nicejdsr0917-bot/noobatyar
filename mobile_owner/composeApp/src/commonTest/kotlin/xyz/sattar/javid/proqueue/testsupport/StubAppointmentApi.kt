package xyz.sattar.javid.proqueue.testsupport

import io.ktor.client.HttpClient
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.client.request.patch
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType
import xyz.sattar.javid.proqueue.core.network.ApiResponse
import xyz.sattar.javid.proqueue.core.network.PaginatedResponseDto
import xyz.sattar.javid.proqueue.core.network.toApiResponse
import xyz.sattar.javid.proqueue.data.remoteDataSource.appointment.model.AppointmentDto
import xyz.sattar.javid.proqueue.domain.model.appointment.AppointmentOrdering
import xyz.sattar.javid.proqueue.data.remoteDataSource.appointment.AppointmentApi

/** Every member throws; a test subclasses it and overrides what it exercises. */
open class StubAppointmentApi : AppointmentApi {
    override suspend fun queryAppointments(
            businessId: Long,
            visitorId: Long?,
            status: String?,
            date: Long?,
            dateFrom: Long?,
            dateTo: Long?,
            ordering: AppointmentOrdering?,
            page: Int,
            pageSize: Int
    ): ApiResponse<PaginatedResponseDto<AppointmentDto>> = TODO("not stubbed")

    override suspend fun getAppointmentById(id: Long): ApiResponse<AppointmentDto> = TODO("not stubbed")

    override suspend fun createAppointment(
            request:
                    xyz.sattar.javid.proqueue.data.remoteDataSource.appointment.model.request.CreateAppointmentRequestDto
    ): ApiResponse<AppointmentDto> = TODO("not stubbed")

    override suspend fun updateAppointment(
            id: Long,
            request:
                    xyz.sattar.javid.proqueue.data.remoteDataSource.appointment.model.request.UpdateAppointmentRequestDto
    ): ApiResponse<AppointmentDto> = TODO("not stubbed")

    override suspend fun deleteAppointment(id: Long): ApiResponse<Unit> = TODO("not stubbed")

    override suspend fun getAppointmentStats(
            businessId: Long,
            dateFrom: Long?,
            dateTo: Long?
    ): ApiResponse<xyz.sattar.javid.proqueue.data.remoteDataSource.appointment.model.AppointmentStatsDto> = TODO("not stubbed")

    override suspend fun getRangeVisitors(
            businessId: Long,
            dateFrom: Long,
            dateTo: Long,
            ordering: String,
            page: Int,
            pageSize: Int
    ): ApiResponse<xyz.sattar.javid.proqueue.data.remoteDataSource.appointment.model.RangeVisitorsPageDto> = TODO("not stubbed")

    override suspend fun getDailyCounts(
            businessId: Long,
            days: Int,
            daysAhead: Int
    ): ApiResponse<List<xyz.sattar.javid.proqueue.data.remoteDataSource.business.model.DailyCountDto>> = TODO("not stubbed")
}
