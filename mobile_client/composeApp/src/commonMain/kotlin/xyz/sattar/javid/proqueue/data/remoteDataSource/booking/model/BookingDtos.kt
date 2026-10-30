package xyz.sattar.javid.proqueue.data.remoteDataSource.booking.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import xyz.sattar.javid.proqueue.data.remoteDataSource.business.model.BusinessDto

// Shapes of the visitor surface (`client/auth/...`, `client/appointments/...`),
// the same endpoints the web client books through.

@Serializable
data class OtpSendRequestDto(val phone: String)

@Serializable
data class OtpSendResponseDto(@SerialName("expires_in") val expiresIn: Int = 120)

@Serializable
data class OtpVerifyRequestDto(val phone: String, val code: String)

@Serializable
data class VisitorDto(
    val id: Long,
    @SerialName("full_name") val fullName: String = "",
    @SerialName("phone_number") val phoneNumber: String = "",
)

/** Registered visitors get [token]; new ones get [registerToken] to finish with a name. */
@Serializable
data class OtpVerifyResponseDto(
    @SerialName("is_registered") val isRegistered: Boolean,
    val token: String? = null,
    val visitor: VisitorDto? = null,
    @SerialName("register_token") val registerToken: String? = null,
)

@Serializable
data class RegisterRequestDto(
    val phone: String,
    @SerialName("register_token") val registerToken: String,
    val name: String,
)

@Serializable
data class RegisterResponseDto(val token: String, val visitor: VisitorDto? = null)

@Serializable
data class TimeSlotDto(
    val time: String,
    val timestamp: Long,
    val available: Boolean,
    val status: String = "AVAILABLE",
)

@Serializable
data class AvailableSlotsDto(
    val slots: List<TimeSlotDto> = emptyList(),
    @SerialName("duration_minutes") val durationMinutes: Int = 0,
    val date: String = "",
    /** The business's weekly day off — tells "closed" apart from "fully booked". */
    val closed: Boolean = false,
)

const val BOOKING_SOURCE_APP = "APP"

@Serializable
data class BookRequestDto(
    @SerialName("business_id") val businessId: Long,
    @SerialName("appointment_date") val appointmentDate: Long,
    // Explicit, and deliberately without a default: the client Json does not
    // encode defaults, and the owner's per-channel stats must not depend on
    // the server-side fallback.
    val source: String,
    @SerialName("service_duration") val serviceDuration: Int? = null,
    val description: String? = null,
    @SerialName("selected_services") val selectedServices: List<String>? = null,
)

@Serializable
data class BookResponseDto(
    val id: Long,
    @SerialName("requires_payment") val requiresPayment: Boolean = false,
)

@Serializable
data class ClientAppointmentDto(
    val id: Long,
    val business: BusinessDto,
    @SerialName("appointment_date") val appointmentDate: Long,
    val status: String,
    @SerialName("queue_position") val queuePosition: Int? = null,
    /** Card-to-card destination; only on the visitor's own LOCKED appointment. */
    @SerialName("card_number") val cardNumber: String? = null,
    @SerialName("card_owner_name") val cardOwnerName: String? = null,
)

@Serializable
data class AppointmentPageDto(val results: List<ClientAppointmentDto> = emptyList())

@Serializable
data class PayRequestDto(
    val method: String,
    @SerialName("payment_reference") val paymentReference: String,
)

@Serializable
data class OnlinePaymentDto(
    @SerialName("payment_url") val paymentUrl: String,
    @SerialName("track_id") val trackId: String = "",
)

@Serializable
data class IdDto(val id: Long)
