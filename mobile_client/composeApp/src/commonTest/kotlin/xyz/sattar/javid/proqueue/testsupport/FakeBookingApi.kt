package xyz.sattar.javid.proqueue.testsupport

import xyz.sattar.javid.proqueue.core.network.ApiResponse
import xyz.sattar.javid.proqueue.data.remoteDataSource.booking.BookingApi
import xyz.sattar.javid.proqueue.data.remoteDataSource.booking.model.AppointmentPageDto
import xyz.sattar.javid.proqueue.data.remoteDataSource.booking.model.AvailableSlotsDto
import xyz.sattar.javid.proqueue.data.remoteDataSource.booking.model.BookRequestDto
import xyz.sattar.javid.proqueue.data.remoteDataSource.booking.model.BookResponseDto
import xyz.sattar.javid.proqueue.data.remoteDataSource.booking.model.IdDto
import xyz.sattar.javid.proqueue.data.remoteDataSource.booking.model.OnlinePaymentDto
import xyz.sattar.javid.proqueue.data.remoteDataSource.booking.model.OtpSendResponseDto
import xyz.sattar.javid.proqueue.data.remoteDataSource.booking.model.OtpVerifyResponseDto
import xyz.sattar.javid.proqueue.data.remoteDataSource.booking.model.RegisterResponseDto
import xyz.sattar.javid.proqueue.data.remoteDataSource.booking.model.TimeSlotDto

fun slot(time: String, available: Boolean = true) = TimeSlotDto(time = time, timestamp = time.hashCode().toLong(), available = available)

class PayCall(val token: String, val id: Long, val method: String, val reference: String, val receipt: ByteArray? = null)

class FakeBookingApi : BookingApi {
    val slotDates = mutableListOf<String>()
    val booked = mutableListOf<Pair<String, BookRequestDto>>()
    val payments = mutableListOf<PayCall>()
    val cancelled = mutableListOf<Long>()
    var otpSentTo: String? = null

    var slots: ApiResponse<AvailableSlotsDto> = ApiResponse.Success(AvailableSlotsDto(slots = listOf(slot("10:00"), slot("10:30"))))
    var verify: ApiResponse<OtpVerifyResponseDto> =
        ApiResponse.Success(OtpVerifyResponseDto(isRegistered = true, token = "tok"))
    var registerResponse: ApiResponse<RegisterResponseDto> = ApiResponse.Success(RegisterResponseDto(token = "new-tok"))
    var bookResponse: ApiResponse<BookResponseDto> = ApiResponse.Success(BookResponseDto(id = 55))
    var appointments: ApiResponse<AppointmentPageDto> = ApiResponse.Success(AppointmentPageDto())
    var online: ApiResponse<OnlinePaymentDto> = ApiResponse.Success(OnlinePaymentDto("https://gateway.zibal.ir/start/1"))

    override suspend fun sendOtp(phone: String): ApiResponse<OtpSendResponseDto> {
        otpSentTo = phone
        return ApiResponse.Success(OtpSendResponseDto())
    }

    override suspend fun verifyOtp(phone: String, code: String) = verify
    override suspend fun register(phone: String, registerToken: String, name: String) = registerResponse

    override suspend fun availableSlots(businessId: Long, date: String): ApiResponse<AvailableSlotsDto> {
        slotDates += date
        return slots
    }

    override suspend fun book(token: String, request: BookRequestDto): ApiResponse<BookResponseDto> {
        booked += token to request
        return bookResponse
    }

    override suspend fun myAppointments(token: String) = appointments

    override suspend fun pay(token: String, appointmentId: Long, method: String, reference: String): ApiResponse<IdDto> {
        payments += PayCall(token, appointmentId, method, reference)
        return ApiResponse.Success(IdDto(appointmentId))
    }

    /** What [payWithReceipt] answers; progress is reported in quarters before it. */
    var receiptResult: ApiResponse<IdDto>? = null
    val reportedProgress = mutableListOf<Float>()

    override suspend fun payWithReceipt(
        token: String,
        appointmentId: Long,
        reference: String,
        receipt: ByteArray,
        onProgress: (Float) -> Unit,
    ): ApiResponse<IdDto> {
        payments += PayCall(token, appointmentId, "CARD", reference, receipt)
        listOf(0.25f, 0.5f, 0.75f, 1f).forEach { reportedProgress += it; onProgress(it) }
        return receiptResult ?: ApiResponse.Success(IdDto(appointmentId))
    }

    override suspend fun payOnline(token: String, appointmentId: Long) = online

    override suspend fun cancel(token: String, appointmentId: Long): ApiResponse<IdDto> {
        cancelled += appointmentId
        return ApiResponse.Success(IdDto(appointmentId))
    }
}
