package xyz.sattar.javid.proqueue.feature.explore

import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.launch
import xyz.sattar.javid.proqueue.core.network.ApiResponse
import xyz.sattar.javid.proqueue.core.ui.BaseViewModel
import xyz.sattar.javid.proqueue.data.localDataSource.business.toDomain
import xyz.sattar.javid.proqueue.domain.BusinessRepository

class ExploreViewModel(
    private val businessRepository: BusinessRepository
) : BaseViewModel<ExploreState, ExplorePartialState, ExploreEvent, ExploreIntent>(
    initialState = ExploreState()
) {

    private var searchDebounceJob: Job? = null

    init {
        sendIntent(ExploreIntent.LoadInitialData)
    }

    override fun handleIntent(intent: ExploreIntent): Flow<ExplorePartialState> {
        return when (intent) {
            ExploreIntent.LoadInitialData -> flow {
                emit(ExplorePartialState.Loading(true))
                loadInsurancesInternal()
                fetchBusinesses(currentState = uiState.value, isRefresh = false)
            }
            ExploreIntent.Refresh -> flow {
                emit(ExplorePartialState.Refreshing(true))
                loadInsurancesInternal()
                fetchBusinesses(currentState = uiState.value, isRefresh = true)
            }
            is ExploreIntent.SelectLocation -> flow {
                emit(ExplorePartialState.LocationChanged(intent.preset))
                val newState = uiState.value.copy(selectedLocation = intent.preset)
                fetchBusinesses(currentState = newState, isRefresh = false)
            }
            is ExploreIntent.SelectCategory -> flow {
                emit(ExplorePartialState.CategoryChanged(intent.categoryKey))
                val newState = uiState.value.copy(selectedCategory = intent.categoryKey)
                fetchBusinesses(currentState = newState, isRefresh = false)
            }
            is ExploreIntent.SelectInsurance -> flow {
                emit(ExplorePartialState.InsuranceChanged(intent.insuranceSlug))
                val newState = uiState.value.copy(selectedInsuranceSlug = intent.insuranceSlug)
                fetchBusinesses(currentState = newState, isRefresh = false)
            }
            is ExploreIntent.UpdateSearchQuery -> channelFlow {
                send(ExplorePartialState.SearchQueryChanged(intent.query))
                searchDebounceJob?.cancel()
                searchDebounceJob = launch {
                    if (intent.query.isNotBlank()) {
                        delay(300)
                    }
                    val newState = uiState.value.copy(searchQuery = intent.query)
                    flow {
                        fetchBusinesses(currentState = newState, isRefresh = false)
                    }.collect { send(it) }
                }
            }
            is ExploreIntent.ToggleOnlyClaimed -> flow {
                emit(ExplorePartialState.OnlyClaimedChanged(intent.onlyClaimed))
                val newState = uiState.value.copy(onlyClaimed = intent.onlyClaimed)
                fetchBusinesses(currentState = newState, isRefresh = false)
            }
            ExploreIntent.ClearFilters -> flow {
                searchDebounceJob?.cancel()
                emit(ExplorePartialState.FiltersCleared)
                val newState = uiState.value.copy(
                    searchQuery = "",
                    selectedCategory = null,
                    selectedInsuranceSlug = null,
                    onlyClaimed = false
                )
                fetchBusinesses(currentState = newState, isRefresh = false)
            }
            is ExploreIntent.BusinessClicked -> flow {
                sendEvent(ExploreEvent.NavigateToDetail(intent.businessId))
            }
        }
    }

    private suspend fun kotlinx.coroutines.flow.FlowCollector<ExplorePartialState>.loadInsurancesInternal() {
        when (val res = businessRepository.getInsurances()) {
            is ApiResponse.Success -> {
                val list = res.data.map { it.toDomain() }
                if (list.isNotEmpty()) {
                    emit(ExplorePartialState.InsurancesLoaded(list))
                }
            }
            is ApiResponse.Error -> Unit
        }
    }

    private suspend fun kotlinx.coroutines.flow.FlowCollector<ExplorePartialState>.fetchBusinesses(
        currentState: ExploreState,
        isRefresh: Boolean
    ) {
        if (!isRefresh) {
            emit(ExplorePartialState.Loading(true))
        }
        try {
            val response = businessRepository.getClientBusinesses(
                page = 1,
                pageSize = 50,
                search = currentState.searchQuery.takeIf { it.isNotBlank() },
                category = currentState.selectedCategory,
                insuranceSlug = currentState.selectedInsuranceSlug,
                lat = currentState.selectedLocation.lat,
                lng = currentState.selectedLocation.lng,
                radiusKm = currentState.selectedLocation.radiusKm,
                onlyClaimed = currentState.onlyClaimed,
                sort = "recommended"
            )

            when (response) {
                is ApiResponse.Success -> {
                    val allBusinesses = response.data.results.map { it.toDomain() }
                    val vip = allBusinesses.filter { it.isClaimed }
                    emit(ExplorePartialState.BusinessesLoaded(allBusinesses, vip))
                    emit(ExplorePartialState.Error(null))
                }
                is ApiResponse.Error -> {
                    emit(ExplorePartialState.Error(response.message))
                }
            }
        } catch (e: Exception) {
            emit(ExplorePartialState.Error(e.message ?: "خطا در برقراری ارتباط با سرور"))
        } finally {
            emit(ExplorePartialState.Loading(false))
            emit(ExplorePartialState.Refreshing(false))
        }
    }

    override fun createErrorState(message: String): ExplorePartialState = ExplorePartialState.Error(message)

    override fun reduceState(
        currentState: ExploreState,
        partialState: ExplorePartialState
    ): ExploreState {
        return when (partialState) {
            is ExplorePartialState.Loading -> currentState.copy(isLoading = partialState.isLoading)
            is ExplorePartialState.Refreshing -> currentState.copy(isRefreshing = partialState.isRefreshing)
            is ExplorePartialState.BusinessesLoaded -> currentState.copy(
                businesses = partialState.businesses,
                vipBusinesses = partialState.vipBusinesses,
                isLoading = false,
                isRefreshing = false
            )
            is ExplorePartialState.InsurancesLoaded -> currentState.copy(insurances = partialState.insurances)
            is ExplorePartialState.LocationChanged -> currentState.copy(selectedLocation = partialState.location)
            is ExplorePartialState.CategoryChanged -> currentState.copy(selectedCategory = partialState.category)
            is ExplorePartialState.InsuranceChanged -> currentState.copy(selectedInsuranceSlug = partialState.insuranceSlug)
            is ExplorePartialState.SearchQueryChanged -> currentState.copy(searchQuery = partialState.query)
            is ExplorePartialState.OnlyClaimedChanged -> currentState.copy(onlyClaimed = partialState.onlyClaimed)
            ExplorePartialState.FiltersCleared -> currentState.copy(
                searchQuery = "",
                selectedCategory = null,
                selectedInsuranceSlug = null,
                onlyClaimed = false
            )
            is ExplorePartialState.Error -> currentState.copy(
                errorMessage = partialState.message,
                isLoading = false,
                isRefreshing = false
            )
        }
    }
}
