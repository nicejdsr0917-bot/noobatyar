package xyz.sattar.javid.proqueue.feature.businessDetail

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import xyz.sattar.javid.proqueue.core.network.ApiResponse
import xyz.sattar.javid.proqueue.core.prefs.ClientPreferences
import xyz.sattar.javid.proqueue.data.remoteDataSource.business.toDomain
import xyz.sattar.javid.proqueue.domain.BusinessRepository
import xyz.sattar.javid.proqueue.domain.model.business.Business

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
) : ViewModel() {

    private val _state = MutableStateFlow(ClientBusinessDetailState())
    val state: StateFlow<ClientBusinessDetailState> = _state.asStateFlow()

    private val _messages = Channel<DetailMessage>(Channel.BUFFERED)
    val messages = _messages.receiveAsFlow()

    private var businessId: Long = 0

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
                    _state.update {
                        it.copy(
                            isLoading = false,
                            business = business,
                            bookingRequestsCount = business.onlineBookingRequestsCount,
                        )
                    }
                }
                is ApiResponse.Error -> _state.update { it.copy(isLoading = false, errorMessage = res.message) }
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
