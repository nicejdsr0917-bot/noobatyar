package xyz.sattar.javid.proqueue.feature.businessDetail

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
import xyz.sattar.javid.proqueue.core.location.LocationResult
import xyz.sattar.javid.proqueue.core.network.ApiResponse
import xyz.sattar.javid.proqueue.core.prefs.ClientPreferences
import xyz.sattar.javid.proqueue.core.prefs.SavedLocation
import xyz.sattar.javid.proqueue.core.ui.components.map.GeoPoint
import xyz.sattar.javid.proqueue.core.ui.components.map.distanceKmTo
import xyz.sattar.javid.proqueue.data.remoteDataSource.business.toDomain
import xyz.sattar.javid.proqueue.domain.BusinessRepository
import xyz.sattar.javid.proqueue.domain.model.business.Business
import kotlin.time.Clock

@Immutable
data class BusinessMapState(
    val business: Business? = null,
    val isLoading: Boolean = true,
    val errorMessage: String? = null,
    /** The user's position: the saved one at first, then the fresh fix. */
    val me: GeoPoint? = null,
    /** The fix on screen came from this visit, not from the saved one. */
    val fresh: Boolean = false,
    val locating: Boolean = false,
    val locationProblem: LocationResult? = null,
)

/** Straight-line (great-circle) km from the user to the business; null if either is unknown. */
fun BusinessMapState.distanceKm(): Double? {
    val b = business ?: return null
    val lat = b.latitude ?: return null
    val lng = b.longitude ?: return null
    return me?.distanceKmTo(GeoPoint(lat, lng))
}

/**
 * The business on a full map with the user beside it. Opening it asks for a
 * fix; every fix is saved, so the next screen that needs the user's position
 * has one before GPS answers.
 */
class BusinessMapViewModel(
    private val businessRepository: BusinessRepository,
    private val preferences: ClientPreferences,
    private val dispatcher: CoroutineDispatcher = Dispatchers.Main.immediate,
    private val now: () -> Long = { Clock.System.now().toEpochMilliseconds() },
) : ViewModel() {

    private val _state = MutableStateFlow(BusinessMapState())
    val state: StateFlow<BusinessMapState> = _state.asStateFlow()

    fun load(businessId: Long) {
        viewModelScope.launch(dispatcher) {
            val saved = preferences.lastLocation.first()
            _state.update { it.copy(isLoading = true, errorMessage = null, me = it.me ?: saved?.let { s -> GeoPoint(s.latitude, s.longitude) }) }
            when (val res = businessRepository.getClientBusinessDetail(businessId)) {
                is ApiResponse.Success -> _state.update { it.copy(isLoading = false, business = res.data.toDomain()) }
                is ApiResponse.Error -> _state.update { it.copy(isLoading = false, errorMessage = res.message) }
            }
        }
    }

    fun onLocating() = _state.update { it.copy(locating = true, locationProblem = null) }

    fun onLocationResult(result: LocationResult) {
        when (result) {
            is LocationResult.Success -> {
                _state.update {
                    it.copy(locating = false, locationProblem = null, fresh = true, me = GeoPoint(result.latitude, result.longitude))
                }
                viewModelScope.launch(dispatcher) {
                    preferences.saveLocation(SavedLocation(result.latitude, result.longitude, now()))
                }
            }
            else -> _state.update { it.copy(locating = false, locationProblem = result) }
        }
    }
}
