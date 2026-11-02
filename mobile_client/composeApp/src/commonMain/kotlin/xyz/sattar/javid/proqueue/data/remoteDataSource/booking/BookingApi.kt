package xyz.sattar.javid.proqueue.data.remoteDataSource.booking

import io.ktor.client.HttpClient
import io.ktor.client.plugins.onUpload
import io.ktor.client.request.forms.formData
import io.ktor.client.request.forms.submitFormWithBinaryData
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.Headers
import io.ktor.http.HttpHeaders
import io.ktor.http.contentType
import xyz.sattar.javid.proqueue.core.network.ApiResponse
import xyz.sattar.javid.proqueue.core.network.safeApiCall
import xyz.sattar.javid.proqueue.core.network.toApiResponse
import xyz.sattar.javid.proqueue.data.remoteDataSource.booking.model.AppointmentPageDto
import xyz.sattar.javid.proqueue.data.remoteDataSource.booking.model.AvailableSlotsDto
import xyz.sattar.javid.proqueue.data.remoteDataSource.booking.model.BookRequestDto
import xyz.sattar.javid.proqueue.data.remoteDataSource.booking.model.BookResponseDto
import xyz.sattar.javid.proqueue.data.remoteDataSource.booking.model.IdDto
import xyz.sattar.javid.proqueue.data.remoteDataSource.booking.model.OnlinePaymentDto
import xyz.sattar.javid.proqueue.data.remoteDataSource.booking.model.OtpSendRequestDto
import xyz.sattar.javid.proqueue.data.remoteDataSource.booking.model.OtpSendResponseDto
import xyz.sattar.javid.proqueue.data.remoteDataSource.booking.model.OtpVerifyRequestDto
import xyz.sattar.javid.proqueue.data.remoteDataSource.booking.model.OtpVerifyResponseDto
import xyz.sattar.javid.proqueue.data.remoteDataSource.booking.model.PayRequestDto
import xyz.sattar.javid.proqueue.data.remoteDataSource.booking.model.RegisterRequestDto
import xyz.sattar.javid.proqueue.data.remoteDataSource.booking.model.RegisterResponseDto

/**
 * Visitor sign-in and in-app booking: OTP, slots, create, pay, cancel and the
 * visitor's own appointments. An interface so the booking view-models can be
 * tested against a fake.
 */
interface BookingApi {
    suspend fun sendOtp(phone: String): ApiResponse<OtpSendResponseDto>
    suspend fun verifyOtp(phone: String, code: String): ApiResponse<OtpVerifyResponseDto>
    suspend fun register(phone: String, registerToken: String, name: String): ApiResponse<RegisterResponseDto>

    /** [date] is the local calendar day as `yyyy-MM-dd`. */
    suspend fun availableSlots(businessId: Long, date: String): ApiResponse<AvailableSlotsDto>
    suspend fun book(token: String, request: BookRequestDto): ApiResponse<BookResponseDto>

    suspend fun myAppointments(token: String): ApiResponse<AppointmentPageDto>
    suspend fun pay(token: String, appointmentId: Long, method: String, reference: String): ApiResponse<IdDto>
    /**
     * A card transfer with a photo of the receipt, sent as multipart. The
     * reference may be blank when the photo is there. [onProgress] gets the
     * sent fraction, 0..1.
     */
    suspend fun payWithReceipt(
        token: String,
        appointmentId: Long,
        reference: String,
        receipt: ByteArray,
        onProgress: (Float) -> Unit,
    ): ApiResponse<IdDto>
    suspend fun payOnline(token: String, appointmentId: Long): ApiResponse<OnlinePaymentDto>
    suspend fun cancel(token: String, appointmentId: Long): ApiResponse<IdDto>
}

class KtorBookingApi(private val httpClient: HttpClient) : BookingApi {

    private fun HttpRequestBuilder.visitor(token: String) = header(HttpHeaders.Authorization, "Visitor $token")

    override suspend fun sendOtp(phone: String): ApiResponse<OtpSendResponseDto> = safeApiCall {
        httpClient.post("client/auth/otp/send/") {
            contentType(ContentType.Application.Json)
            setBody(OtpSendRequestDto(phone))
        }.toApiResponse()
    }

    override suspend fun verifyOtp(phone: String, code: String): ApiResponse<OtpVerifyResponseDto> = safeApiCall {
        httpClient.post("client/auth/otp/verify/") {
            contentType(ContentType.Application.Json)
            setBody(OtpVerifyRequestDto(phone, code))
        }.toApiResponse()
    }

    override suspend fun register(phone: String, registerToken: String, name: String): ApiResponse<RegisterResponseDto> =
        safeApiCall {
            httpClient.post("client/auth/register/") {
                contentType(ContentType.Application.Json)
                setBody(RegisterRequestDto(phone, registerToken, name))
            }.toApiResponse()
        }

    override suspend fun availableSlots(businessId: Long, date: String): ApiResponse<AvailableSlotsDto> = safeApiCall {
        httpClient.get("client/appointments/$businessId/available-slots/") {
            parameter("date", date)
        }.toApiResponse()
    }

    override suspend fun book(token: String, request: BookRequestDto): ApiResponse<BookResponseDto> = safeApiCall {
        httpClient.post("client/appointments/") {
            visitor(token)
            contentType(ContentType.Application.Json)
            setBody(request)
        }.toApiResponse()
    }

    override suspend fun myAppointments(token: String): ApiResponse<AppointmentPageDto> = safeApiCall {
        httpClient.get("client/appointments/") {
            visitor(token)
            parameter("page_size", 50)
        }.toApiResponse()
    }

    override suspend fun pay(token: String, appointmentId: Long, method: String, reference: String): ApiResponse<IdDto> =
        safeApiCall {
            httpClient.post("client/appointments/$appointmentId/pay/") {
                visitor(token)
                contentType(ContentType.Application.Json)
                setBody(PayRequestDto(method, reference))
            }.toApiResponse()
        }

    override suspend fun payWithReceipt(
        token: String,
        appointmentId: Long,
        reference: String,
        receipt: ByteArray,
        onProgress: (Float) -> Unit,
    ): ApiResponse<IdDto> = safeApiCall {
        httpClient.submitFormWithBinaryData(
            url = "client/appointments/$appointmentId/pay/",
            formData = formData {
                append("method", "CARD")
                append("payment_reference", reference)
                append("payment_receipt", receipt, Headers.build {
                    append(HttpHeaders.ContentType, "image/jpeg")
                    append(HttpHeaders.ContentDisposition, "filename=\"receipt.jpg\"")
                })
            },
        ) {
            visitor(token)
            onUpload { sent, total -> if (total != null && total > 0) onProgress(sent.toFloat() / total) }
        }.toApiResponse()
    }

    override suspend fun payOnline(token: String, appointmentId: Long): ApiResponse<OnlinePaymentDto> = safeApiCall {
        httpClient.post("client/appointments/$appointmentId/pay/online/") {
            visitor(token)
        }.toApiResponse()
    }

    override suspend fun cancel(token: String, appointmentId: Long): ApiResponse<IdDto> = safeApiCall {
        httpClient.post("client/appointments/$appointmentId/cancel/") {
            visitor(token)
        }.toApiResponse()
    }
}
