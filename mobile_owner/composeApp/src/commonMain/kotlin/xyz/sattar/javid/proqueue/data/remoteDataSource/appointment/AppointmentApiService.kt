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

class AppointmentApiService(private val httpClient: HttpClient) {

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
    ): ApiResponse<PaginatedResponseDto<AppointmentDto>> {
        return httpClient
                .get("appointment/query") {
                    contentType(ContentType.Application.Json)
                    parameter("business_id", businessId)
                    visitorId?.let { parameter("visitor_id", it) }
                    status?.let { parameter("status", it) }
                    date?.let { parameter("date", it) }
                    dateFrom?.let { parameter("date_from", it) }
                    dateTo?.let { parameter("date_to", it) }
                    parameter("ordering", ordering?.value)
                    parameter("page", page)
                    parameter("page_size", pageSize)
                }
                .toApiResponse()
    }

    suspend fun getAppointmentById(id: Long): ApiResponse<AppointmentDto> {
        return httpClient
                .get("appointment/$id/") { contentType(ContentType.Application.Json) }
                .toApiResponse()
    }

    suspend fun createAppointment(
            request:
                    xyz.sattar.javid.proqueue.data.remoteDataSource.appointment.model.request.CreateAppointmentRequestDto
    ): ApiResponse<AppointmentDto> {
        return httpClient
                .post("appointment/") {
                    contentType(ContentType.Application.Json)
                    setBody(request)
                }
                .toApiResponse()
    }

    suspend fun updateAppointment(
            id: Long,
            request:
                    xyz.sattar.javid.proqueue.data.remoteDataSource.appointment.model.request.UpdateAppointmentRequestDto
    ): ApiResponse<AppointmentDto> {
        return httpClient
                .patch("appointment/$id/") {
                    contentType(ContentType.Application.Json)
                    setBody(request)
                }
                .toApiResponse()
    }

    suspend fun deleteAppointment(id: Long): ApiResponse<Unit> {
        return httpClient.delete("appointment/$id/") {}.toApiResponse()
    }

    /**
     * [dateFrom]/[dateTo] are optional and, when omitted, the server keeps its
     * long-standing "today" window — so existing callers are unaffected. The
     * range report passes them to get the same numbers over its own window.
     */
    suspend fun getAppointmentStats(
            businessId: Long,
            dateFrom: Long? = null,
            dateTo: Long? = null
    ): ApiResponse<
            xyz.sattar.javid.proqueue.data.remoteDataSource.appointment.model.AppointmentStatsDto> {
        return httpClient
                .get("appointment/stats/") {
                    contentType(ContentType.Application.Json)
                    parameter("business_id", businessId)
                    dateFrom?.let { parameter("date_from", it) }
                    dateTo?.let { parameter("date_to", it) }
                }
                .toApiResponse()
    }

    suspend fun getRangeVisitors(
            businessId: Long,
            dateFrom: Long,
            dateTo: Long,
            ordering: String = "visits",
            page: Int = 1,
            pageSize: Int = 20
    ): ApiResponse<
            xyz.sattar.javid.proqueue.data.remoteDataSource.appointment.model.RangeVisitorsPageDto> {
        return httpClient
                .get("appointment/range-visitors/") {
                    contentType(ContentType.Application.Json)
                    parameter("business_id", businessId)
                    parameter("date_from", dateFrom)
                    parameter("date_to", dateTo)
                    parameter("ordering", ordering)
                    parameter("page", page)
                    parameter("page_size", pageSize)
                }
                .toApiResponse()
    }

    suspend fun getDailyCounts(
            businessId: Long,
            days: Int,
            daysAhead: Int = 0
    ): ApiResponse<List<xyz.sattar.javid.proqueue.data.remoteDataSource.business.model.DailyCountDto>> {
        return httpClient
                .get("appointment/daily-counts/") {
                    contentType(ContentType.Application.Json)
                    parameter("business_id", businessId)
                    parameter("days", days)
                    // Forward half of the window; 0 keeps the plain 7-day
                    // trend chart's original request shape.
                    parameter("days_ahead", daysAhead)
                }
                .toApiResponse()
    }
}
