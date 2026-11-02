package xyz.sattar.javid.proqueue.data.remoteDataSource.business.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class RequestBookingResponseDto(
    @SerialName("id") val id: Long,
    @SerialName("online_booking_requests_count") val onlineBookingRequestsCount: Int,
    @SerialName("already_requested") val alreadyRequested: Boolean = false
)

@Serializable
data class ContentReportRequestDto(
    @SerialName("reason") val reason: String,
    @SerialName("detail") val detail: String = ""
)
