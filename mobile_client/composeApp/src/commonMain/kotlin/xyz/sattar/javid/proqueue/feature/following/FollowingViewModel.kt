package xyz.sattar.javid.proqueue.feature.following

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import xyz.sattar.javid.proqueue.core.network.ApiResponse
import xyz.sattar.javid.proqueue.core.prefs.ClientPreferences
import xyz.sattar.javid.proqueue.data.remoteDataSource.business.toDomain
import xyz.sattar.javid.proqueue.domain.BusinessRepository
import xyz.sattar.javid.proqueue.domain.model.business.Business

@Immutable
data class FollowingState(
    val isLoading: Boolean = true,
    val businesses: List<Business> = emptyList(),
    val error: String? = null,
)

/** The businesses the signed-in visitor follows; unfollowing drops the row at once. */
class FollowingViewModel(
    private val businessRepository: BusinessRepository,
    private val preferences: ClientPreferences,
    private val dispatcher: CoroutineDispatcher = Dispatchers.Main.immediate,
) : ViewModel() {
    private val _state = MutableStateFlow(FollowingState())
    val state: StateFlow<FollowingState> = _state.asStateFlow()

    init { load() }

    fun load() {
        viewModelScope.launch(dispatcher) {
            val token = preferences.visitorToken.first()
            if (token == null) {
                _state.update { FollowingState(isLoading = false) }
                return@launch
            }
            _state.update { it.copy(isLoading = it.businesses.isEmpty(), error = null) }
            when (val res = businessRepository.getFollowing(token)) {
                is ApiResponse.Success -> _state.update {
                    FollowingState(isLoading = false, businesses = res.data.results.map { dto -> dto.toDomain() })
                }
                is ApiResponse.Error -> _state.update { it.copy(isLoading = false, error = res.message) }
            }
        }
    }

    fun unfollow(id: Long) {
        val before = _state.value.businesses
        _state.update { s -> s.copy(businesses = s.businesses.filterNot { it.id == id }) }
        viewModelScope.launch(dispatcher) {
            val token = preferences.visitorToken.first() ?: return@launch
            if (businessRepository.setFollowing(token, id, false) is ApiResponse.Error) {
                _state.update { it.copy(businesses = before) }
            }
        }
    }
}
