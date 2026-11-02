package xyz.sattar.javid.proqueue.feature.profile

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
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import xyz.sattar.javid.proqueue.core.network.ApiResponse
import xyz.sattar.javid.proqueue.core.prefs.ClientPreferences
import xyz.sattar.javid.proqueue.data.remoteDataSource.booking.BookingApi
import xyz.sattar.javid.proqueue.data.remoteDataSource.profile.MeDto
import xyz.sattar.javid.proqueue.data.remoteDataSource.profile.ProfileApi

@Immutable
data class ProfileState(
    val signedIn: Boolean = false,
    val loading: Boolean = false,
    val me: MeDto? = null,
    /** First page of the visitor's appointments, for the count in the header. */
    val appointmentCount: Int? = null,
    val savedCount: Int = 0,
    val savingName: Boolean = false,
    val uploadingPhoto: Boolean = false,
)

sealed interface ProfileMessage {
    data class Success(val text: String) : ProfileMessage
    data class Error(val text: String) : ProfileMessage
}

/**
 * The profile tab, and the tab bar's avatar: who is signed in, their name and
 * photo, and the edits to those. Follows the visitor token, so signing in or
 * out anywhere in the app updates the tab icon too.
 */
class ProfileViewModel(
    private val api: ProfileApi,
    private val bookingApi: BookingApi,
    private val preferences: ClientPreferences,
    private val dispatcher: CoroutineDispatcher = Dispatchers.Main.immediate,
) : ViewModel() {

    private val _state = MutableStateFlow(ProfileState())
    val state: StateFlow<ProfileState> = _state.asStateFlow()

    private val _messages = Channel<ProfileMessage>(Channel.BUFFERED)
    val messages = _messages.receiveAsFlow()

    init {
        viewModelScope.launch(dispatcher) {
            preferences.visitorToken.distinctUntilChanged().collect { token ->
                if (token == null) _state.update { ProfileState(savedCount = it.savedCount) }
                else {
                    _state.update { it.copy(signedIn = true) }
                    load(token)
                }
            }
        }
        viewModelScope.launch(dispatcher) {
            preferences.savedBusinessIds.collect { ids -> _state.update { it.copy(savedCount = ids.size) } }
        }
    }

    /** Called when the tab is shown, so a rename made on the web shows up. */
    fun refresh() {
        viewModelScope.launch(dispatcher) { preferences.visitorToken.first()?.let { load(it) } }
    }

    private suspend fun load(token: String) {
        _state.update { it.copy(loading = it.me == null) }
        when (val res = api.me(token)) {
            is ApiResponse.Success -> _state.update { it.copy(loading = false, me = res.data) }
            is ApiResponse.Error -> _state.update { it.copy(loading = false) }
        }
        val count = (bookingApi.myAppointments(token) as? ApiResponse.Success)?.data?.results?.size
        if (count != null) _state.update { it.copy(appointmentCount = count) }
    }

    fun rename(name: String, onDone: () -> Unit) {
        val clean = name.trim().replace(Regex("\\s+"), " ")
        if (clean.length < 2) {
            viewModelScope.launch { _messages.send(ProfileMessage.Error("نام باید دست‌کم دو حرف باشد")) }
            return
        }
        if (clean == _state.value.me?.fullName) {
            onDone()
            return
        }
        withToken { token ->
            _state.update { it.copy(savingName = true) }
            when (val res = api.rename(token, clean)) {
                is ApiResponse.Success -> {
                    _state.update { it.copy(savingName = false, me = res.data) }
                    _messages.send(ProfileMessage.Success("نام شما ذخیره شد"))
                    onDone()
                }
                is ApiResponse.Error -> {
                    _state.update { it.copy(savingName = false) }
                    _messages.send(ProfileMessage.Error(res.message))
                }
            }
        }
    }

    fun setPhoto(jpeg: ByteArray) = withToken { token ->
        _state.update { it.copy(uploadingPhoto = true) }
        when (val res = api.uploadAvatar(token, jpeg)) {
            is ApiResponse.Success -> _state.update { it.copy(uploadingPhoto = false, me = res.data) }
            is ApiResponse.Error -> {
                _state.update { it.copy(uploadingPhoto = false) }
                _messages.send(ProfileMessage.Error(res.message))
            }
        }
    }

    fun removePhoto() = withToken { token ->
        _state.update { it.copy(uploadingPhoto = true) }
        when (val res = api.deleteAvatar(token)) {
            is ApiResponse.Success -> _state.update { it.copy(uploadingPhoto = false, me = res.data) }
            is ApiResponse.Error -> {
                _state.update { it.copy(uploadingPhoto = false) }
                _messages.send(ProfileMessage.Error(res.message))
            }
        }
    }

    fun signOut() {
        viewModelScope.launch(dispatcher) { preferences.setVisitorToken(null) }
    }

    private fun withToken(block: suspend (String) -> Unit) {
        viewModelScope.launch(dispatcher) {
            val token = preferences.visitorToken.first() ?: return@launch
            block(token)
        }
    }
}
