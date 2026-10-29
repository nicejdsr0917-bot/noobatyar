package xyz.sattar.javid.proqueue.data.remoteDataSource.appointment

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

/**
 * Contract the repositories depend on; [AppointmentApiService] is the Ktor
 * implementation, tests substitute a fake.
 */
interface AppointmentApi {
    suspend fun queryAppointments(
            businessId: Long,
            visitorId: Long? = null,
            status: String? = null,
            date: Long? = null,
            dateFrom: Long? = null,
            dateTo: Long? = null,
            ordering: AppointmentOrdering? = AppointmentOrdering.DATE_DESC,
            page: Int = 1,
            pageSize: Int = 20
    ): ApiResponse<PaginatedResponseDto<AppointmentDto>>

    suspend fun getAppointmentById(id: Long): ApiResponse<AppointmentDto>

    suspend fun createAppointment(
            request:
                    xyz.sattar.javid.proqueue.data.remoteDataSource.appointment.model.request.CreateAppointmentRequestDto
    ): ApiResponse<AppointmentDto>

    suspend fun updateAppointment(
            id: Long,
            request:
                    xyz.sattar.javid.proqueue.data.remoteDataSource.appointment.model.request.UpdateAppointmentRequestDto
    ): ApiResponse<AppointmentDto>

    suspend fun deleteAppointment(id: Long): ApiResponse<Unit>

    suspend fun getAppointmentStats(
            businessId: Long,
            dateFrom: Long? = null,
            dateTo: Long? = null
    ): ApiResponse<
            xyz.sattar.javid.proqueue.data.remoteDataSource.appointment.model.AppointmentStatsDto>

    suspend fun getRangeVisitors(
            businessId: Long,
            dateFrom: Long,
            dateTo: Long,
            ordering: String = "visits",
            page: Int = 1,
            pageSize: Int = 20
    ): ApiResponse<
            xyz.sattar.javid.proqueue.data.remoteDataSource.appointment.model.RangeVisitorsPageDto>

    suspend fun getDailyCounts(
            businessId: Long,
            days: Int,
            daysAhead: Int = 0
    ): ApiResponse<List<xyz.sattar.javid.proqueue.data.remoteDataSource.business.model.DailyCountDto>>
}
