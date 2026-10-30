package xyz.sattar.javid.proqueue.feature.saved

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import xyz.sattar.javid.proqueue.core.network.ApiResponse
import xyz.sattar.javid.proqueue.core.prefs.ClientPreferences
import xyz.sattar.javid.proqueue.data.remoteDataSource.business.toDomain
import xyz.sattar.javid.proqueue.domain.BusinessRepository
import xyz.sattar.javid.proqueue.domain.model.business.Business

@Immutable
data class SavedState(
    val isLoading: Boolean = true,
    val businesses: List<Business> = emptyList(),
)

class SavedViewModel(
    private val businessRepository: BusinessRepository,
    private val preferences: ClientPreferences,
    private val dispatcher: CoroutineDispatcher = Dispatchers.Main.immediate,
) : ViewModel() {
    private val _state = MutableStateFlow(SavedState())
    val state: StateFlow<SavedState> = _state.asStateFlow()

    // Previously loaded entries, so un-saving one doesn't re-fetch the rest.
    private val cache = mutableMapOf<Long, Business>()

    init {
        viewModelScope.launch(dispatcher) {
            preferences.savedBusinessIds.collectLatest { ids ->
                val missing = ids.filter { it !in cache }
                if (missing.isNotEmpty()) {
                    _state.update { it.copy(isLoading = cache.isEmpty()) }
                    missing.map { id ->
                        async { (businessRepository.getClientBusinessDetail(id) as? ApiResponse.Success)?.data?.toDomain() }
                    }.awaitAll().filterNotNull().forEach { cache[it.id] = it }
                }
                _state.update { SavedState(isLoading = false, businesses = ids.mapNotNull { cache[it] }) }
            }
        }
    }

    fun unsave(id: Long) {
        viewModelScope.launch(dispatcher) { preferences.toggleSavedBusiness(id) }
    }
}
