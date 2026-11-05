package xyz.sattar.javid.proqueue.feature.businessDetail

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
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import xyz.sattar.javid.proqueue.core.network.ApiResponse
import xyz.sattar.javid.proqueue.core.prefs.ClientPreferences
import xyz.sattar.javid.proqueue.data.remoteDataSource.business.toDomain
import xyz.sattar.javid.proqueue.domain.BusinessRepository
import xyz.sattar.javid.proqueue.domain.model.business.Business
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.todayIn
import kotlin.time.Clock
import xyz.sattar.javid.proqueue.data.remoteDataSource.booking.BookingApi
import xyz.sattar.javid.proqueue.data.remoteDataSource.booking.model.TimeSlotDto
import xyz.sattar.javid.proqueue.feature.booking.BookingDay
import xyz.sattar.javid.proqueue.feature.booking.bookingDays
import xyz.sattar.javid.proqueue.feature.discovery.canBookOnline

@Immutable
data class ClientBusinessDetailState(
    val isLoading: Boolean = true,
    val business: Business? = null,
    val errorMessage: String? = null,
    val isSaved: Boolean = false,
    val bookingRequestsCount: Int = 0,
    val hasRequestedBooking: Boolean = false,
    val isRequestingBooking: Boolean = false,
    val isReporting: Boolean = false,
    /** Whether a visitor is signed in; a guest may be shown less than the owner allows. */
    val isSignedIn: Boolean = false,
    /** Quick pick on the page itself: the booking flow's days and the picked day's free times. */
    val days: List<BookingDay> = emptyList(),
    val selectedDay: Int = 0,
    val slots: List<TimeSlotDto> = emptyList(),
    val slotsLoading: Boolean = false,
    val selectedSlot: TimeSlotDto? = null,
    /** Pull-to-refresh in progress; the page stays on screen meanwhile. */
    val isRefreshing: Boolean = false,
    val followersCount: Int = 0,
    val isFollowing: Boolean = false,
    val followBusy: Boolean = false,
)

sealed interface DetailMessage {
    data class Success(val text: String) : DetailMessage
    data class Error(val text: String) : DetailMessage
}

/** Report reasons offered to clients — a subset of ContentReport.REASON_CHOICES. */
val reportReasons = listOf(
    "MISLEADING" to "اطلاعات نادرست یا قدیمی (آدرس، تلفن، بیمه)",
    "IMPERSONATION" to "مرکز مال من است و بدون اجازه ثبت شده",
    "INAPPROPRIATE" to "محتوای نامناسب",
    "OTHER" to "سایر",
)

class ClientBusinessDetailViewModel(
    private val businessRepository: BusinessRepository,
    private val preferences: ClientPreferences,
    private val dispatcher: CoroutineDispatcher = Dispatchers.Main.immediate,
    /** Null leaves the quick pick off (tests that only cover the page). */
    private val bookingApi: BookingApi? = null,
    private val today: () -> LocalDate = { Clock.System.todayIn(TimeZone.currentSystemDefault()) },
) : ViewModel() {

    private val _state = MutableStateFlow(ClientBusinessDetailState())
    val state: StateFlow<ClientBusinessDetailState> = _state.asStateFlow()

    private val _messages = Channel<DetailMessage>(Channel.BUFFERED)
    val messages = _messages.receiveAsFlow()

    private var businessId: Long = 0
    private var slotsJob: Job? = null

    fun load(id: Long) {
        businessId = id
        viewModelScope.launch(dispatcher) {
            preferences.savedBusinessIds.collect { ids -> _state.update { it.copy(isSaved = id in ids) } }
        }
        viewModelScope.launch(dispatcher) {
            // Signing in (or out) changes what the server will show of this
            // business, so read it again; the first value is just the current state.
            var first = true
            preferences.visitorToken.map { it != null }.distinctUntilChanged().collect { signedIn ->
                _state.update { it.copy(isSignedIn = signedIn) }
                if (!first) reload()
                first = false
            }
        }
        reload()
    }

    fun reload() {
        viewModelScope.launch(dispatcher) {
            _state.update { it.copy(isLoading = true, errorMessage = null) }
            when (val res = businessRepository.getClientBusinessDetail(businessId)) {
                is ApiResponse.Success -> {
                    val business = res.data.toDomain()
                    val firstLoad = _state.value.days.isEmpty()
                    _state.update {
                        it.copy(
                            isLoading = false,
                            business = business,
                            bookingRequestsCount = business.onlineBookingRequestsCount,
                            followersCount = business.followersCount,
                            isFollowing = business.isFollowing ?: false,
                        )
                    }
                    if (firstLoad && business.canBookOnline && bookingApi != null) {
                        val days = bookingDays(business, today())
                        val first = days.indexOfFirst { !it.closed }.coerceAtLeast(0)
                        _state.update { it.copy(days = days, selectedDay = first) }
                        loadSlots()
                    }
                }
                is ApiResponse.Error -> _state.update { it.copy(isLoading = false, errorMessage = res.message) }
            }
        }
    }

    /** Pull-to-refresh: the business and the picked day's times, without the loading shimmer. */
    fun refresh() {
        if (_state.value.isRefreshing || businessId == 0L) return
        viewModelScope.launch(dispatcher) {
            _state.update { it.copy(isRefreshing = true) }
            when (val res = businessRepository.getClientBusinessDetail(businessId)) {
                is ApiResponse.Success -> {
                    val business = res.data.toDomain()
                    _state.update {
                        it.copy(
                            business = business,
                            bookingRequestsCount = business.onlineBookingRequestsCount,
                            followersCount = business.followersCount,
                            isFollowing = business.isFollowing ?: it.isFollowing,
                        )
                    }
                }
                is ApiResponse.Error -> _messages.send(DetailMessage.Error(res.message))
            }
            loadSlots()
            slotsJob?.join()
            _state.update { it.copy(isRefreshing = false) }
        }
    }

    fun selectDay(index: Int) {
        if (index !in _state.value.days.indices || index == _state.value.selectedDay) return
        _state.update { it.copy(selectedDay = index, selectedSlot = null) }
        loadSlots()
    }

    fun selectSlot(slot: TimeSlotDto) {
        if (!slot.available) return
        _state.update { it.copy(selectedSlot = if (it.selectedSlot == slot) null else slot) }
    }

    private fun loadSlots() {
        val api = bookingApi ?: return
        val day = _state.value.days.getOrNull(_state.value.selectedDay) ?: return
        slotsJob?.cancel()
        if (day.closed) {
            _state.update { it.copy(slots = emptyList(), slotsLoading = false) }
            return
        }
        slotsJob = viewModelScope.launch(dispatcher) {
            _state.update { it.copy(slotsLoading = true, slots = emptyList()) }
            // A failed fetch just leaves the row empty; the booking flow still works.
            val slots = (api.availableSlots(businessId, day.date.toString()) as? ApiResponse.Success)?.data?.slots.orEmpty()
            _state.update { it.copy(slotsLoading = false, slots = slots) }
        }
    }

    /**
     * Follow or unfollow. Shown at once and put back if the server refuses.
     * A guest is asked to sign in instead ([onNeedsSignIn]).
     */
    fun toggleFollow(onNeedsSignIn: () -> Unit) {
        val current = _state.value
        if (current.followBusy) return
        viewModelScope.launch(dispatcher) {
            val token = preferences.visitorToken.first()
            if (token == null) {
                _messages.send(DetailMessage.Error("برای دنبال کردن، وارد حساب شوید"))
                onNeedsSignIn()
                return@launch
            }
            val follow = !current.isFollowing
            _state.update {
                it.copy(followBusy = true, isFollowing = follow, followersCount = (it.followersCount + if (follow) 1 else -1).coerceAtLeast(0))
            }
            when (val res = businessRepository.setFollowing(token, businessId, follow)) {
                is ApiResponse.Success -> _state.update {
                    it.copy(followBusy = false, isFollowing = res.data.isFollowing, followersCount = res.data.followersCount)
                }
                is ApiResponse.Error -> {
                    _state.update { it.copy(followBusy = false, isFollowing = current.isFollowing, followersCount = current.followersCount) }
                    _messages.send(DetailMessage.Error(res.message))
                }
            }
        }
    }

    fun toggleSaved() {
        viewModelScope.launch(dispatcher) { preferences.toggleSavedBusiness(businessId) }
    }

    fun requestOnlineBooking() {
        if (_state.value.isRequestingBooking || _state.value.hasRequestedBooking) return
        viewModelScope.launch(dispatcher) {
            _state.update { it.copy(isRequestingBooking = true) }
            when (val res = businessRepository.requestOnlineBooking(businessId)) {
                is ApiResponse.Success -> {
                    _state.update {
                        it.copy(
                            isRequestingBooking = false,
                            hasRequestedBooking = true,
                            bookingRequestsCount = res.data.onlineBookingRequestsCount,
                        )
                    }
                    _messages.send(
                        DetailMessage.Success(
                            if (res.data.alreadyRequested) "درخواست شما قبلاً ثبت شده بود"
                            else "درخواست ثبت شد؛ به مرکز اطلاع می‌دهیم"
                        )
                    )
                }
                is ApiResponse.Error -> {
                    _state.update { it.copy(isRequestingBooking = false) }
                    _messages.send(DetailMessage.Error(res.message))
                }
            }
        }
    }

    fun report(reason: String, detail: String, onDone: () -> Unit) {
        viewModelScope.launch(dispatcher) {
            _state.update { it.copy(isReporting = true) }
            when (val res = businessRepository.reportBusiness(businessId, reason, detail)) {
                is ApiResponse.Success -> {
                    _messages.send(DetailMessage.Success("گزارش ثبت شد؛ بررسی می‌کنیم"))
                    onDone()
                }
                is ApiResponse.Error -> _messages.send(DetailMessage.Error(res.message))
            }
            _state.update { it.copy(isReporting = false) }
        }
    }
}
