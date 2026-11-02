package xyz.sattar.javid.proqueue.feature.booking

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.isoDayNumber
import kotlinx.datetime.plus
import kotlinx.datetime.todayIn
import xyz.sattar.javid.proqueue.core.network.ApiResponse
import xyz.sattar.javid.proqueue.core.prefs.ClientPreferences
import xyz.sattar.javid.proqueue.data.remoteDataSource.booking.BookingApi
import xyz.sattar.javid.proqueue.data.remoteDataSource.booking.model.BOOKING_SOURCE_APP
import xyz.sattar.javid.proqueue.data.remoteDataSource.booking.model.BookRequestDto
import xyz.sattar.javid.proqueue.data.remoteDataSource.booking.model.TimeSlotDto
import xyz.sattar.javid.proqueue.data.remoteDataSource.business.toDomain
import xyz.sattar.javid.proqueue.domain.BusinessRepository
import xyz.sattar.javid.proqueue.domain.model.business.Business
import kotlin.math.roundToInt
import kotlin.time.Clock
import xyz.sattar.javid.proqueue.core.location.DriveEstimate
import xyz.sattar.javid.proqueue.core.location.driveEstimate
import xyz.sattar.javid.proqueue.core.ui.components.map.GeoPoint
import xyz.sattar.javid.proqueue.core.ui.components.map.distanceKmTo

/** How many days ahead the day strip offers. */
const val BOOKING_DAYS_AHEAD = 7

enum class BookingStep { SERVICES, SLOT, PHONE, CODE, NAME, CONFIRM, PAYMENT, DONE }

/** The days the strip offers, from [start]; the business page shows the same ones. */
fun bookingDays(business: Business, start: LocalDate): List<BookingDay> {
    val zone = TimeZone.currentSystemDefault()
    return (0 until BOOKING_DAYS_AHEAD).map { offset ->
        val date = start.plus(DatePeriod(days = offset))
        // closed_weekdays uses Python's date.weekday(): Monday = 0.
        val pyWeekday = date.dayOfWeek.isoDayNumber - 1
        BookingDay(date, date.atStartOfDayIn(zone).toEpochMilliseconds(), pyWeekday in business.closedWeekdays)
    }
}

@Immutable
data class BookingDay(val date: LocalDate, val startMillis: Long, val closed: Boolean)

@Immutable
data class BookingState(
    val isLoading: Boolean = true,
    val loadError: String? = null,
    val business: Business? = null,
    val step: BookingStep = BookingStep.SLOT,
    val selectedServices: List<String> = emptyList(),
    val days: List<BookingDay> = emptyList(),
    val selectedDay: Int = 0,
    val slots: List<TimeSlotDto> = emptyList(),
    val slotsLoading: Boolean = false,
    val dayClosed: Boolean = false,
    val selectedSlot: TimeSlotDto? = null,
    val phone: String = "",
    val code: String = "",
    val name: String = "",
    val description: String = "",
    val busy: Boolean = false,
    val appointmentId: Long? = null,
    val paymentMethods: List<String> = emptyList(),
    val cardNumber: String = "",
    val cardOwnerName: String = "",
    /** The deposit went to the online gateway; its result shows in «نوبت‌های من». */
    val paidOnline: Boolean = false,
    /** Sent fraction of the receipt photo while it uploads; null otherwise. */
    val uploadProgress: Float? = null,
    /** When the held appointment is, if it came from «نوبت‌های من» rather than this flow. */
    val heldAt: Long? = null,
    /** A card or link payment was recorded and now waits for the owner to verify it. */
    val proofSent: Boolean = false,
    /** The hold was given up; the screen leaves. */
    val released: Boolean = false,
)

sealed interface BookingMessage {
    data class Error(val text: String) : BookingMessage
    data class OpenUrl(val url: String) : BookingMessage
}

/**
 * The in-app booking flow: service → day and time → OTP sign-in (first time
 * only) → confirm → deposit. Mirrors the web's book + checkout pages over the
 * same endpoints, but sends `source=APP` and never leaves the app except for
 * the bank gateway itself.
 */
class BookingViewModel(
    private val businessRepository: BusinessRepository,
    private val bookingApi: BookingApi,
    private val preferences: ClientPreferences,
    private val dispatcher: CoroutineDispatcher = Dispatchers.Main.immediate,
    private val today: () -> LocalDate = { Clock.System.todayIn(TimeZone.currentSystemDefault()) },
) : ViewModel() {

    private val _state = MutableStateFlow(BookingState())
    val state: StateFlow<BookingState> = _state.asStateFlow()

    private val _messages = Channel<BookingMessage>(Channel.BUFFERED)
    val messages = _messages.receiveAsFlow()

    private var registerToken: String? = null
    private var slotsJob: Job? = null
    /** A slot picked on the business page, applied once its day's slots arrive. */
    private var pendingSlotAt: Long = 0

    /**
     * [resumeAppointmentId] reopens the payment step of a hold made earlier
     * («تکمیل پرداخت» in the appointments list), like the web's checkout link.
     * [preDate] (ISO date) and [preSlotAt] carry a time already picked on the
     * business page; the strip and grid open on it.
     */
    fun load(businessId: Long, resumeAppointmentId: Long = 0, preDate: String = "", preSlotAt: Long = 0) {
        pendingSlotAt = preSlotAt
        viewModelScope.launch(dispatcher) {
            _state.update { it.copy(isLoading = true, loadError = null) }
            when (val res = businessRepository.getClientBusinessDetail(businessId)) {
                is ApiResponse.Success -> {
                    val business = res.data.toDomain()
                    val days = buildDays(business)
                    val picked = days.indexOfFirst { it.date.toString() == preDate }
                    val first = if (picked >= 0) picked else days.indexOfFirst { !it.closed }.coerceAtLeast(0)
                    _state.update {
                        it.copy(
                            isLoading = false,
                            business = business,
                            days = days,
                            selectedDay = first,
                            step = if (business.offersServiceStep()) BookingStep.SERVICES else BookingStep.SLOT,
                            paymentMethods = business.acceptedPaymentMethods
                                ?: listOfNotNull(business.paymentMethod),
                        )
                    }
                    val token = preferences.visitorToken.first()
                    if (resumeAppointmentId > 0 && token != null) {
                        _state.update { it.copy(appointmentId = resumeAppointmentId) }
                        openPayment(token, resumeAppointmentId)
                    } else {
                        loadSlots()
                    }
                }
                is ApiResponse.Error -> _state.update { it.copy(isLoading = false, loadError = res.message) }
            }
        }
    }

    private fun buildDays(business: Business): List<BookingDay> = bookingDays(business, today())

    // ── Services ──

    fun toggleService(service: String) = _state.update {
        val picked = if (service in it.selectedServices) it.selectedServices - service else it.selectedServices + service
        it.copy(selectedServices = picked)
    }

    /** Adds a service the visitor typed (when the business allows it), picked. */
    fun addCustomService(raw: String): Boolean {
        val name = customServiceName(raw) ?: return false
        if (_state.value.business?.allowClientAddService != true) return false
        _state.update { if (name in it.selectedServices) it else it.copy(selectedServices = it.selectedServices + name) }
        return true
    }

    fun continueFromServices() = _state.update { it.copy(step = BookingStep.SLOT) }

    // ── Day and time ──

    fun selectDay(index: Int) {
        if (index !in _state.value.days.indices || index == _state.value.selectedDay) return
        _state.update { it.copy(selectedDay = index, selectedSlot = null) }
        loadSlots()
    }

    private fun loadSlots() {
        val s = _state.value
        val business = s.business ?: return
        val day = s.days.getOrNull(s.selectedDay) ?: return
        slotsJob?.cancel()
        slotsJob = viewModelScope.launch(dispatcher) {
            _state.update { it.copy(slotsLoading = true, slots = emptyList(), dayClosed = day.closed) }
            when (val res = bookingApi.availableSlots(business.id, day.date.toString())) {
                is ApiResponse.Success -> {
                    val pre = res.data.slots.firstOrNull { it.available && it.timestamp == pendingSlotAt }
                    pendingSlotAt = 0
                    _state.update {
                        it.copy(
                            slotsLoading = false,
                            slots = res.data.slots,
                            dayClosed = day.closed || res.data.closed,
                            selectedSlot = pre ?: it.selectedSlot,
                        )
                    }
                }
                is ApiResponse.Error -> {
                    _state.update { it.copy(slotsLoading = false) }
                    _messages.send(BookingMessage.Error(res.message))
                }
            }
        }
    }

    fun selectSlot(slot: TimeSlotDto) {
        if (!slot.available) return
        _state.update { it.copy(selectedSlot = slot) }
    }

    fun continueFromSlot() {
        if (_state.value.selectedSlot == null) return
        viewModelScope.launch(dispatcher) {
            val signedIn = preferences.visitorToken.first() != null
            _state.update { it.copy(step = if (signedIn) BookingStep.CONFIRM else BookingStep.PHONE) }
        }
    }

    // ── Sign-in (first booking only) ──

    fun setPhone(value: String) = _state.update { it.copy(phone = value.normalizeDigits().filter(Char::isDigit).take(11)) }
    fun setCode(value: String) = _state.update { it.copy(code = value.normalizeDigits().filter(Char::isDigit).take(6)) }
    fun setName(value: String) = _state.update { it.copy(name = value.take(60)) }
    fun setDescription(value: String) = _state.update { it.copy(description = value.take(500)) }

    val isPhoneValid: Boolean get() = _state.value.phone.matches(Regex("09\\d{9}"))

    fun sendOtp() {
        if (!isPhoneValid) return
        busy {
            when (val res = bookingApi.sendOtp(_state.value.phone)) {
                is ApiResponse.Success -> _state.update { it.copy(step = BookingStep.CODE, code = "") }
                is ApiResponse.Error -> _messages.send(BookingMessage.Error(res.message))
            }
        }
    }

    fun verifyOtp() {
        val s = _state.value
        if (s.code.length < 4) return
        busy {
            when (val res = bookingApi.verifyOtp(s.phone, s.code)) {
                is ApiResponse.Success -> {
                    val token = res.data.token
                    if (res.data.isRegistered && token != null) {
                        signedIn(token)
                    } else {
                        registerToken = res.data.registerToken
                        _state.update { it.copy(step = BookingStep.NAME) }
                    }
                }
                is ApiResponse.Error -> _messages.send(BookingMessage.Error(res.message))
            }
        }
    }

    fun register() {
        val s = _state.value
        val token = registerToken ?: return
        if (s.name.isBlank()) return
        busy {
            when (val res = bookingApi.register(s.phone, token, s.name.trim())) {
                is ApiResponse.Success -> signedIn(res.data.token)
                is ApiResponse.Error -> _messages.send(BookingMessage.Error(res.message))
            }
        }
    }

    private suspend fun signedIn(token: String) {
        preferences.setVisitorToken(token)
        _state.update { it.copy(step = BookingStep.CONFIRM) }
    }

    // ── Confirm and pay ──

    fun confirm() {
        val s = _state.value
        val business = s.business ?: return
        val slot = s.selectedSlot ?: return
        busy {
            val token = preferences.visitorToken.first() ?: run {
                _state.update { it.copy(step = BookingStep.PHONE) }
                return@busy
            }
            val travel = travelFrom(business)
            val request = BookRequestDto(
                businessId = business.id,
                appointmentDate = slot.timestamp,
                source = BOOKING_SOURCE_APP,
                serviceDuration = business.defaultServiceDuration.takeIf { it > 0 },
                description = s.description.trim().ifBlank { null },
                selectedServices = s.selectedServices.ifEmpty { null },
                travelDistanceM = travel?.first,
                driveMinutesMin = travel?.second?.minMinutes,
                driveMinutesMax = travel?.second?.maxMinutes,
            )
            when (val res = bookingApi.book(token, request)) {
                is ApiResponse.Success -> {
                    _state.update { it.copy(appointmentId = res.data.id) }
                    if (res.data.requiresPayment) openPayment(token, res.data.id)
                    else _state.update { it.copy(step = BookingStep.DONE) }
                }
                is ApiResponse.Error -> {
                    if (res.code == 401) {
                        // The stored session is dead: sign in again, then come back here.
                        preferences.setVisitorToken(null)
                        _state.update { it.copy(step = BookingStep.PHONE) }
                    } else if (res.code == 409) {
                        // Someone took the slot meanwhile: back to the times, refreshed.
                        _state.update { it.copy(step = BookingStep.SLOT, selectedSlot = null) }
                        loadSlots()
                    }
                    _messages.send(BookingMessage.Error(res.message))
                }
            }
        }
    }

    /**
     * Straight-line metres and a drive estimate from the visitor's last saved
     * position, for the owner to see; null when there is no position yet.
     */
    private suspend fun travelFrom(business: Business): Pair<Int, DriveEstimate>? {
        val me = preferences.lastLocation.first() ?: return null
        val lat = business.latitude ?: return null
        val lng = business.longitude ?: return null
        val km = GeoPoint(me.latitude, me.longitude).distanceKmTo(GeoPoint(lat, lng))
        return (km * 1000).roundToInt() to driveEstimate(km)
    }

    /**
     * The card number is only served on the visitor's own held appointment,
     * and its business payload is fresher than the one loaded at the start of
     * the flow (the public detail is cached), so payment options come from it.
     */
    private suspend fun openPayment(token: String, appointmentId: Long) {
        val own = (bookingApi.myAppointments(token) as? ApiResponse.Success)
            ?.data?.results?.firstOrNull { it.id == appointmentId }
        if (own != null && own.status != "LOCKED") {
            // Paid, cancelled or expired meanwhile (a resumed hold): nothing to pay.
            _messages.send(BookingMessage.Error("نوبت منتظر پرداخت نیست"))
            _state.update { it.copy(released = true) }
            return
        }
        val methods = own?.business?.let { it.acceptedPaymentMethods ?: listOfNotNull(it.paymentMethod) }
        _state.update {
            it.copy(
                step = BookingStep.PAYMENT,
                business = own?.business?.toDomain() ?: it.business,
                heldAt = if (it.selectedSlot == null) own?.appointmentDate else null,
                cardNumber = own?.cardNumber.orEmpty(),
                cardOwnerName = own?.cardOwnerName.orEmpty(),
                paymentMethods = methods ?: it.paymentMethods,
            )
        }
    }

    fun payOnline() = withAppointment { token, id ->
        when (val res = bookingApi.payOnline(token, id)) {
            is ApiResponse.Success -> {
                _state.update { it.copy(step = BookingStep.DONE, paidOnline = true) }
                _messages.send(BookingMessage.OpenUrl(res.data.paymentUrl))
            }
            is ApiResponse.Error -> _messages.send(BookingMessage.Error(res.message))
        }
    }

    /** A card transfer, proven by a tracking number, a receipt photo, or both. */
    fun payByCard(reference: String, receipt: ByteArray? = null) {
        if (!canSubmitCardPayment(reference, receipt != null)) return
        if (receipt == null) return submitPayment("CARD", reference.trim())
        withAppointment { token, id ->
            _state.update { it.copy(uploadProgress = 0f) }
            val res = try {
                bookingApi.payWithReceipt(token, id, reference.trim(), receipt) { sent ->
                    _state.update { it.copy(uploadProgress = sent) }
                }
            } finally {
                _state.update { it.copy(uploadProgress = null) }
            }
            when (res) {
                is ApiResponse.Success -> _state.update { it.copy(step = BookingStep.DONE, proofSent = true) }
                is ApiResponse.Error -> _messages.send(BookingMessage.Error(res.message))
            }
        }
    }

    /** Paid on the owner's own payment page: the tracking number is the proof. */
    fun payByLink(reference: String) {
        if (reference.isBlank()) return
        submitPayment("ONLINE", reference.trim())
    }

    /**
     * Gives the held slot back, as closing the web checkout does. Leaves even
     * when the call fails: a stale hold ages out on the server by itself.
     */
    fun releaseHold() {
        val id = _state.value.appointmentId
        busy {
            val token = preferences.visitorToken.first()
            if (id != null && token != null) bookingApi.cancel(token, id)
            _state.update { it.copy(released = true) }
        }
    }

    fun payCash() = submitPayment("CASH", "")

    private fun submitPayment(method: String, reference: String) = withAppointment { token, id ->
        when (val res = bookingApi.pay(token, id, method, reference)) {
            is ApiResponse.Success -> _state.update { it.copy(step = BookingStep.DONE, proofSent = method != "CASH") }
            is ApiResponse.Error -> _messages.send(BookingMessage.Error(res.message))
        }
    }

    private fun withAppointment(block: suspend (token: String, id: Long) -> Unit) {
        val id = _state.value.appointmentId ?: return
        busy {
            val token = preferences.visitorToken.first() ?: return@busy
            block(token, id)
        }
    }

    /** Steps back one screen; false when the flow is at its start (the caller leaves). */
    fun back(): Boolean {
        val s = _state.value
        val previous = when (s.step) {
            BookingStep.SERVICES -> null
            BookingStep.SLOT -> if (s.business?.offersServiceStep() == true) BookingStep.SERVICES else null
            BookingStep.PHONE, BookingStep.CONFIRM -> BookingStep.SLOT
            BookingStep.CODE -> BookingStep.PHONE
            BookingStep.NAME -> BookingStep.CODE
            // Booked: the appointment exists, so there is nothing to go back to.
            BookingStep.PAYMENT, BookingStep.DONE -> null
        } ?: return false
        _state.update { it.copy(step = previous) }
        return true
    }

    private fun busy(block: suspend () -> Unit) {
        if (_state.value.busy) return
        viewModelScope.launch(dispatcher) {
            _state.update { it.copy(busy = true) }
            try { block() } finally { _state.update { it.copy(busy = false) } }
        }
    }
}

/** Persian and Arabic-Indic digits to ASCII, so a pasted code or phone works. */
internal fun String.normalizeDigits(): String = map { c ->
    when (c) {
        in '۰'..'۹' -> '0' + (c - '۰')
        in '٠'..'٩' -> '0' + (c - '٠')
        else -> c
    }
}.joinToString("")
