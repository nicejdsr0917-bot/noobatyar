package xyz.sattar.javid.proqueue.feature.more

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import xyz.sattar.javid.proqueue.core.network.ApiResponse
import xyz.sattar.javid.proqueue.core.prefs.ClientPreferences
import xyz.sattar.javid.proqueue.data.remoteDataSource.business.toDomain
import xyz.sattar.javid.proqueue.domain.BusinessRepository
import xyz.sattar.javid.proqueue.domain.model.business.Insurance

@Immutable
data class MyInsuranceState(
    val insurances: List<Insurance> = emptyList(),
    val mySlug: String? = null,
) {
    val mine: Insurance? get() = insurances.firstOrNull { it.slug == mySlug }
}

/** «بیمه من»: the insurance the user holds, picked once from the catalog and kept on the phone. */
class MyInsuranceViewModel(
    private val businessRepository: BusinessRepository,
    private val preferences: ClientPreferences,
    private val dispatcher: CoroutineDispatcher = Dispatchers.Main.immediate,
) : ViewModel() {

    private val _state = MutableStateFlow(MyInsuranceState())
    val state: StateFlow<MyInsuranceState> = _state.asStateFlow()

    init {
        viewModelScope.launch(dispatcher) {
            preferences.myInsuranceSlug.collect { slug -> _state.update { it.copy(mySlug = slug) } }
        }
        viewModelScope.launch(dispatcher) {
            when (val res = businessRepository.getInsurances()) {
                is ApiResponse.Success -> _state.update { s -> s.copy(insurances = res.data.map { it.toDomain() }) }
                is ApiResponse.Error -> Unit
            }
        }
    }

    /** [slug] null removes it. */
    fun select(slug: String?) {
        viewModelScope.launch(dispatcher) { preferences.setMyInsuranceSlug(slug) }
    }
}
