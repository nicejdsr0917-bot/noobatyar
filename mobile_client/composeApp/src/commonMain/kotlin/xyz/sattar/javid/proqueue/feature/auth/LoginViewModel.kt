package xyz.sattar.javid.proqueue.feature.auth

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import xyz.sattar.javid.proqueue.core.network.ApiResponse
import xyz.sattar.javid.proqueue.core.prefs.ClientPreferences
import xyz.sattar.javid.proqueue.data.remoteDataSource.booking.BookingApi
import xyz.sattar.javid.proqueue.feature.booking.normalizeDigits

/** The web login's three steps: phone, the SMS code, and the name of a first-time visitor. */
enum class LoginStep { PHONE, CODE, NAME }

const val OTP_LENGTH = 6
const val RESEND_AFTER_SECONDS = 120
private const val NAME_MAX = 50

@Immutable
data class LoginState(
    val step: LoginStep = LoginStep.PHONE,
    val phone: String = "",
    val code: String = "",
    val name: String = "",
    val busy: Boolean = false,
    /** Seconds until the code may be sent again; 0 when it may. */
    val resendIn: Int = 0,
    val signedIn: Boolean = false,
)

/**
 * Visitor sign-in, the same flow as the web's /auth/login: a phone number, the
 * code texted to it, and — only the first time — a name. The session token is
 * stored the way the booking flow stores it, so every screen picks it up.
 */
class LoginViewModel(
    private val api: BookingApi,
    private val preferences: ClientPreferences,
    private val dispatcher: CoroutineDispatcher = Dispatchers.Main.immediate,
) : ViewModel() {

    private val _state = MutableStateFlow(LoginState())
    val state: StateFlow<LoginState> = _state.asStateFlow()

    private val _errors = Channel<String>(Channel.BUFFERED)
    val errors = _errors.receiveAsFlow()

    private var registerToken: String? = null
    private var countdown: Job? = null

    val isPhoneValid: Boolean get() = _state.value.phone.matches(Regex("09\\d{9}"))

    fun setPhone(value: String) =
        _state.update { it.copy(phone = value.normalizeDigits().filter(Char::isDigit).take(11)) }

    fun setName(value: String) = _state.update { it.copy(name = value.take(NAME_MAX)) }

    /** Verifies by itself once the last digit is typed, as the web's code boxes do. */
    fun setCode(value: String) {
        val code = value.normalizeDigits().filter(Char::isDigit).take(OTP_LENGTH)
        _state.update { it.copy(code = code) }
        if (code.length == OTP_LENGTH && !_state.value.busy) verifyCode()
    }

    fun sendCode() {
        if (!isPhoneValid || _state.value.busy) return
        busy {
            when (val res = api.sendOtp(_state.value.phone)) {
                is ApiResponse.Success -> {
                    _state.update { it.copy(step = LoginStep.CODE, code = "") }
                    startCountdown()
                }
                is ApiResponse.Error -> _errors.send(res.message)
            }
        }
    }

    fun resend() {
        if (_state.value.resendIn > 0) return
        sendCode()
    }

    fun verifyCode() {
        val s = _state.value
        if (s.code.length < OTP_LENGTH || s.busy) return
        busy {
            when (val res = api.verifyOtp(s.phone, s.code)) {
                is ApiResponse.Success -> {
                    val token = res.data.token
                    if (res.data.isRegistered && token != null) {
                        signedIn(token)
                    } else {
                        registerToken = res.data.registerToken
                        _state.update { it.copy(step = LoginStep.NAME) }
                    }
                }
                is ApiResponse.Error -> {
                    _state.update { it.copy(code = "") }
                    _errors.send(res.message)
                }
            }
        }
    }

    fun register() {
        val s = _state.value
        val token = registerToken ?: return
        if (s.name.isBlank() || s.busy) return
        busy {
            when (val res = api.register(s.phone, token, s.name.trim())) {
                is ApiResponse.Success -> signedIn(res.data.token)
                is ApiResponse.Error -> _errors.send(res.message)
            }
        }
    }

    /** One step back; false when already on the first, so the caller leaves the screen. */
    fun back(): Boolean {
        if (_state.value.busy) return true
        return when (_state.value.step) {
            LoginStep.PHONE -> false
            LoginStep.CODE -> true.also { _state.update { it.copy(step = LoginStep.PHONE, code = "") } }
            LoginStep.NAME -> true.also { _state.update { it.copy(step = LoginStep.CODE, code = "") } }
        }
    }

    private suspend fun signedIn(token: String) {
        preferences.setVisitorToken(token)
        countdown?.cancel()
        _state.update { it.copy(signedIn = true) }
    }

    private fun startCountdown() {
        countdown?.cancel()
        countdown = viewModelScope.launch(dispatcher) {
            for (left in RESEND_AFTER_SECONDS downTo 0) {
                _state.update { it.copy(resendIn = left) }
                if (left > 0) delay(1_000)
            }
        }
    }

    private fun busy(block: suspend () -> Unit) {
        viewModelScope.launch(dispatcher) {
            _state.update { it.copy(busy = true) }
            try {
                block()
            } finally {
                _state.update { it.copy(busy = false) }
            }
        }
    }
}
