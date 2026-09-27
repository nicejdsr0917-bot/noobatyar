package xyz.sattar.javid.proqueue.data.remoteDataSource.appointment.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * One customer in the range report — see
 * backend/appointment/views/range_visitors_view.py.
 *
 * Not a [xyz.sattar.javid.proqueue.data.remoteDataSource.visitor] row: this is
 * a per-range aggregate (how many times *in this window*), so it carries only
 * what the report shows and is never cached as if it were the visitor record.
 */
@Serializable
data class RangeVisitorDto(
    @SerialName("visitor_id") val visitorId: Long,
    @SerialName("full_name") val fullName: String = "",
    @SerialName("phone_number") val phoneNumber: String = "",
    @SerialName("appointment_count") val appointmentCount: Int = 0,
    /** Epoch ms of the latest appointment in the window; null if the row had none. */
    @SerialName("last_visit") val lastVisit: Long? = null
)

/**
 * The range-visitors page plus the window the server actually measured.
 *
 * [dateFrom]/[dateTo] are echoed back rather than assumed from the request
 * because the server clamps anything wider than a month; [clamped] tells the
 * UI to correct its own picker instead of labelling the result with a range
 * that was never queried.
 */
@Serializable
data class RangeVisitorsPageDto(
    @SerialName("count") val count: Int = 0,
    @SerialName("total_pages") val totalPages: Int = 1,
    @SerialName("current_page") val currentPage: Int = 1,
    @SerialName("next") val next: Int? = null,
    @SerialName("previous") val previous: Int? = null,
    @SerialName("results") val results: List<RangeVisitorDto> = emptyList(),
    @SerialName("date_from") val dateFrom: Long = 0L,
    @SerialName("date_to") val dateTo: Long = 0L,
    @SerialName("clamped") val clamped: Boolean = false
)
