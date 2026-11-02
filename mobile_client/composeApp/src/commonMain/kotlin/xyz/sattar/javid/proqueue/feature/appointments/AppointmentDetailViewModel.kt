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

@Immutable
data class AppointmentDetailState(
    val isLoading: Boolean = true,
    val appointment: ClientAppointmentDto? = null,
    val errorMessage: String? = null,
    val cancelling: Boolean = false,
)

/**
 * One appointment's slip, as the web's `/appointments/<id>` shows it. The
 * client API has no single-appointment endpoint, so it is picked out of the
 * visitor's own list, as the web does.
 */
class AppointmentDetailViewModel(
    private val bookingApi: BookingApi,
    private val preferences: ClientPreferences,
    private val dispatcher: CoroutineDispatcher = Dispatchers.Main.immediate,
) : ViewModel() {

    private val _state = MutableStateFlow(AppointmentDetailState())
    val state: StateFlow<AppointmentDetailState> = _state.asStateFlow()

    private val _messages = Channel<AppointmentsMessage>(Channel.BUFFERED)
    val messages = _messages.receiveAsFlow()

    fun load(id: Long) {
        viewModelScope.launch(dispatcher) {
            val token = preferences.visitorToken.first()
            if (token == null) {
                _state.update { AppointmentDetailState(isLoading = false, errorMessage = "برای دیدن نوبت وارد شوید") }
                return@launch
            }
            _state.update { it.copy(isLoading = true, errorMessage = null) }
            when (val res = bookingApi.myAppointments(token)) {
                is ApiResponse.Success -> {
                    val found = res.data.results.firstOrNull { it.id == id }
                    _state.update {
                        it.copy(
                            isLoading = false,
                            appointment = found,
                            errorMessage = if (found == null) "نوبت پیدا نشد" else null,
                        )
                    }
                }
                is ApiResponse.Error -> _state.update { it.copy(isLoading = false, errorMessage = res.message) }
            }
        }
    }

    fun cancel() {
        val apt = _state.value.appointment ?: return
        if (_state.value.cancelling) return
        viewModelScope.launch(dispatcher) {
            val token = preferences.visitorToken.first() ?: return@launch
            _state.update { it.copy(cancelling = true) }
            when (val res = bookingApi.cancel(token, apt.id)) {
                is ApiResponse.Success -> {
                    _messages.send(AppointmentsMessage.Info("نوبت لغو شد"))
                    _state.update { it.copy(cancelling = false) }
                    load(apt.id)
                }
                is ApiResponse.Error -> {
                    _state.update { it.copy(cancelling = false) }
                    _messages.send(AppointmentsMessage.Error(res.message))
                }
            }
        }
    }
}
