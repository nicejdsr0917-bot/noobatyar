package xyz.sattar.javid.proqueue.feature.appointments

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import xyz.sattar.javid.proqueue.core.network.ApiResponse
import xyz.sattar.javid.proqueue.core.prefs.ClientPreferences
import xyz.sattar.javid.proqueue.data.remoteDataSource.booking.BookingApi
import xyz.sattar.javid.proqueue.data.remoteDataSource.booking.model.ClientAppointmentDto

/** Mirrors backend `CLIENT_CANCELLABLE_STATUSES`. */
val CANCELLABLE_STATUSES = setOf("LOCKED", "PENDING_APPROVAL", "PENDING_VERIFICATION", "WAITING", "CONFIRMED")

fun statusLabel(status: String): String = when (status) {
    "LOCKED" -> "در انتظار پرداخت بیعانه"
    "PENDING_VERIFICATION" -> "در انتظار تأیید واریز"
    "PENDING_APPROVAL" -> "در انتظار تأیید مرکز"
    "CONFIRMED" -> "تأیید شده"
    "WAITING" -> "در صف"
    "IN_PROGRESS" -> "در حال انجام"
    "COMPLETED" -> "انجام شده"
    "NO_SHOW" -> "غیبت"
    "CANCELLED" -> "لغو شده"
    else -> status
}

@Immutable
data class MyAppointmentsState(
    val isSignedIn: Boolean = false,
    val isLoading: Boolean = false,
    val appointments: List<ClientAppointmentDto> = emptyList(),
    val errorMessage: String? = null,
    /** Appointment whose cancel/pay call is in flight. */
    val busyId: Long? = null,
)

sealed interface AppointmentsMessage {
    data class Info(val text: String) : AppointmentsMessage
    data class Error(val text: String) : AppointmentsMessage
    data class OpenUrl(val url: String) : AppointmentsMessage
}

class MyAppointmentsViewModel(
    private val bookingApi: BookingApi,
    private val preferences: ClientPreferences,
    private val dispatcher: CoroutineDispatcher = Dispatchers.Main.immediate,
) : ViewModel() {

    private val _state = MutableStateFlow(MyAppointmentsState())
    val state: StateFlow<MyAppointmentsState> = _state.asStateFlow()

    private val _messages = Channel<AppointmentsMessage>(Channel.BUFFERED)
    val messages = _messages.receiveAsFlow()

    fun refresh() {
        viewModelScope.launch(dispatcher) {
            val token = preferences.visitorToken.first()
            if (token == null) {
                _state.update { MyAppointmentsState(isSignedIn = false) }
                return@launch
            }
            _state.update { it.copy(isSignedIn = true, isLoading = true, errorMessage = null) }
            when (val res = bookingApi.myAppointments(token)) {
                is ApiResponse.Success -> _state.update { it.copy(isLoading = false, appointments = res.data.results) }
                is ApiResponse.Error -> {
                    if (res.code == 401) {
                        // The session expired server-side: show the signed-out state.
                        preferences.setVisitorToken(null)
                        _state.update { MyAppointmentsState(isSignedIn = false) }
                    } else {
                        _state.update { it.copy(isLoading = false, errorMessage = res.message) }
                    }
                }
            }
        }
    }

    fun cancel(id: Long) = act(id) { token ->
        when (val res = bookingApi.cancel(token, id)) {
            is ApiResponse.Success -> {
                _messages.send(AppointmentsMessage.Info("نوبت لغو شد"))
                refresh()
            }
            is ApiResponse.Error -> _messages.send(AppointmentsMessage.Error(res.message))
        }
    }

    fun payOnline(id: Long) = act(id) { token ->
        when (val res = bookingApi.payOnline(token, id)) {
            is ApiResponse.Success -> _messages.send(AppointmentsMessage.OpenUrl(res.data.paymentUrl))
            is ApiResponse.Error -> _messages.send(AppointmentsMessage.Error(res.message))
        }
    }

    fun signOut() {
        viewModelScope.launch(dispatcher) {
            preferences.setVisitorToken(null)
            _state.update { MyAppointmentsState(isSignedIn = false) }
        }
    }

    private fun act(id: Long, block: suspend (token: String) -> Unit) {
        if (_state.value.busyId != null) return
        viewModelScope.launch(dispatcher) {
            val token = preferences.visitorToken.first() ?: return@launch
            _state.update { it.copy(busyId = id) }
            try { block(token) } finally { _state.update { it.copy(busyId = null) } }
        }
    }
}
