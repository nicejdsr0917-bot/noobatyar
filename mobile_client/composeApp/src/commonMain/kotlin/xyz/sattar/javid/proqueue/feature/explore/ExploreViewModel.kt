package xyz.sattar.javid.proqueue.feature.explore

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import xyz.sattar.javid.proqueue.core.location.CityCatalog
import xyz.sattar.javid.proqueue.core.location.LocationResult
import xyz.sattar.javid.proqueue.core.network.ApiResponse
import xyz.sattar.javid.proqueue.core.prefs.ALL_IRAN
import xyz.sattar.javid.proqueue.core.prefs.ClientPreferences
import xyz.sattar.javid.proqueue.data.remoteDataSource.business.toDomain
import xyz.sattar.javid.proqueue.domain.BusinessRepository
import xyz.sattar.javid.proqueue.feature.discovery.CategoryGroup

private const val PAGE_SIZE = 20
private const val FEATURED_SIZE = 10
private const val SEARCH_DEBOUNCE_MS = 350L

/**
 * Not built on BaseViewModel on purpose: that base merges every intent's flow
 * concurrently (flatMapMerge), so a slow response for "دندان" could land after
 * the one for "دندانپزشک" and overwrite it. Here every filter change cancels the
 * in-flight search ([searchJob]) so only the latest query can reach the screen.
 */
class ExploreViewModel(
    private val businessRepository: BusinessRepository,
    private val preferences: ClientPreferences,
    private val cities: CityCatalog,
    private val dispatcher: CoroutineDispatcher = Dispatchers.Main.immediate,
) : ViewModel() {

    private val _state = MutableStateFlow(ExploreState())
    val state: StateFlow<ExploreState> = _state.asStateFlow()

    private var searchJob: Job? = null
    private var loadMoreJob: Job? = null

    init {
        viewModelScope.launch(dispatcher) {
            preferences.savedBusinessIds.collect { ids -> _state.update { it.copy(savedIds = ids) } }
        }
        viewModelScope.launch(dispatcher) {
            preferences.recentSearches.collect { r -> _state.update { it.copy(recentSearches = r) } }
        }
        viewModelScope.launch(dispatcher) {
            preferences.myInsuranceSlug.collect { slug ->
                val before = _state.value.insuranceSlug
                _state.update { it.copy(myInsuranceSlug = slug).withMyInsurance() }
                if (_state.value.insuranceSlug != before) search()
            }
        }
        viewModelScope.launch(dispatcher) {
            when (val res = businessRepository.getInsurances()) {
                is ApiResponse.Success -> _state.update { s -> s.copy(insurances = res.data.map { it.toDomain() }) }
                is ApiResponse.Error -> Unit
            }
        }
        // City first, like Divar: the last pick is where the app reopens; with
        // none yet, the screen asks before showing the whole country.
        viewModelScope.launch(dispatcher) {
            val codes = preferences.recentCityCodes.first()
            val area = codes.firstOrNull()?.takeIf { it != ALL_IRAN }
                ?.let { cities.city(it)?.first?.toArea() }
                ?: SearchArea.Anywhere
            _state.update { it.copy(area = area, recentCityCodes = codes, needsCityPick = codes.isEmpty()) }
            search()
        }
        viewModelScope.launch(dispatcher) {
            preferences.recentCityCodes.collect { c -> _state.update { it.copy(recentCityCodes = c) } }
        }
    }

    /** A pick from [CityPickerSheet]: a city, or [SearchArea.Anywhere] for "کل ایران". */
    fun selectCity(area: SearchArea) {
        viewModelScope.launch(dispatcher) {
            preferences.selectCity((area as? SearchArea.City)?.code ?: ALL_IRAN)
        }
        _state.update { it.copy(needsCityPick = false) }
        selectArea(area)
    }

    // ── Location ─────────────────────────────────────────────────────────────

    fun onLocating() = _state.update { it.copy(locationStatus = LocationStatus.Locating) }

    fun onLocationResult(result: LocationResult) {
        when (result) {
            is LocationResult.Success -> {
                _state.update {
                    it.copy(
                        area = SearchArea.NearMe(result.latitude, result.longitude),
                        locationStatus = LocationStatus.Granted,
                        sort = SortMode.DISTANCE,
                    )
                }
                search()
            }
            LocationResult.PermissionDenied -> _state.update { it.copy(locationStatus = LocationStatus.Denied) }
            LocationResult.Disabled -> _state.update { it.copy(locationStatus = LocationStatus.Disabled) }
            LocationResult.Unavailable -> _state.update { it.copy(locationStatus = LocationStatus.Unavailable) }
        }
    }

    fun selectArea(area: SearchArea) {
        _state.update {
            it.copy(
                area = area,
                sort = when (area) {
                    SearchArea.Anywhere, is SearchArea.City -> SortMode.RECOMMENDED
                    // A spot picked on the map is a "what's around here" question.
                    is SearchArea.Picked -> SortMode.DISTANCE
                    else -> it.sort
                },
            )
        }
        search()
    }

    /** "Search this area" / a picked point: the centre and a radius that covers what's on screen. */
    fun searchAround(latitude: Double, longitude: Double, radiusKm: Double) {
        _state.update {
            it.copy(
                area = SearchArea.Picked(latitude, longitude),
                radiusKm = radiusKm,
                sort = SortMode.DISTANCE,
            )
        }
        search()
    }

    fun selectRadius(km: Double) {
        _state.update { it.copy(radiusKm = km) }
        search()
    }

    // ── Filters ──────────────────────────────────────────────────────────────

    fun updateQuery(query: String) {
        _state.update { it.copy(query = query) }
        search(debounce = true)
    }

    /** A deliberate search (keyboard "search", a suggestion tap): runs at once and is remembered. */
    fun submitSearch(query: String) {
        _state.update { it.copy(query = query) }
        search()
        rememberQuery()
    }

    /** Remembers the current query once the user acts on its results. */
    fun rememberQuery() {
        val q = _state.value.query.trim()
        if (q.length >= 2) viewModelScope.launch(dispatcher) { preferences.addRecentSearch(q) }
    }

    fun clearRecentSearches() {
        viewModelScope.launch(dispatcher) { preferences.clearRecentSearches() }
    }

    fun selectCategory(group: CategoryGroup) {
        if (group == _state.value.category) return
        // A filter the new category does not have must not stay on, unseen.
        _state.update {
            it.copy(category = group, insuranceSlug = it.insuranceSlug.takeIf { group.supportsInsurance }).withMyInsurance()
        }
        search()
    }

    fun selectInsurance(slug: String?) {
        _state.update { it.copy(insuranceSlug = slug, insuranceOptedOut = slug == null) }
        search()
    }

    /** «ذخیره به‌عنوان بیمه من»: the insurance picked in the filter becomes the user's own. */
    fun saveMyInsurance(slug: String?) {
        viewModelScope.launch(dispatcher) { preferences.setMyInsuranceSlug(slug) }
    }

    fun toggleOnlyOnline() {
        _state.update { it.copy(onlyOnline = !it.onlyOnline) }
        search()
    }

    fun selectSort(sort: SortMode) {
        if (sort == _state.value.sort) return
        _state.update { it.copy(sort = sort) }
        search()
    }

    fun clearFilters() {
        _state.update {
            it.copy(query = "", category = CategoryGroup.ALL, insuranceSlug = null, insuranceOptedOut = false, onlyOnline = false)
        }
        search()
    }

    fun refresh() = search(isRefresh = true)

    fun toggleSaved(id: Long) {
        viewModelScope.launch(dispatcher) { preferences.toggleSavedBusiness(id) }
    }

    // ── Loading ──────────────────────────────────────────────────────────────

    private fun search(debounce: Boolean = false, isRefresh: Boolean = false) {
        searchJob?.cancel()
        loadMoreJob?.cancel()
        searchJob = viewModelScope.launch(dispatcher) {
            if (debounce) delay(SEARCH_DEBOUNCE_MS)
            _state.update {
                it.copy(
                    isLoading = !isRefresh,
                    isRefreshing = isRefresh,
                    isLoadingMore = false,
                    errorMessage = null,
                )
            }
            val s = _state.value

            // Featured strip: claimed + bookable businesses for the same area and
            // category, independent of the text query so it doesn't vanish while typing.
            val featuredDeferred = async {
                if (s.onlyOnline) null else fetch(s, page = 1, pageSize = FEATURED_SIZE, onlyClaimed = true, ignoreQuery = true)
            }
            val results = fetch(s, page = 1, pageSize = PAGE_SIZE, onlyClaimed = s.onlyOnline)
            val featured = featuredDeferred.await()

            _state.update { current ->
                when (results) {
                    is ApiResponse.Success -> current.copy(
                        results = results.data.results.map { it.toDomain() },
                        totalCount = results.data.count,
                        nextPage = results.data.next,
                        featured = (featured as? ApiResponse.Success)?.data?.results
                            ?.map { it.toDomain() }
                            ?.filter { it.isClaimed && it.bookingEnabled }
                            .orEmpty(),
                        isLoading = false,
                        isRefreshing = false,
                    )
                    is ApiResponse.Error -> current.copy(
                        errorMessage = results.message,
                        isLoading = false,
                        isRefreshing = false,
                    )
                }
            }
        }
    }

    fun loadMore() {
        val s = _state.value
        val page = s.nextPage ?: return
        if (s.isLoading || s.isLoadingMore || loadMoreJob?.isActive == true) return
        loadMoreJob = viewModelScope.launch(dispatcher) {
            _state.update { it.copy(isLoadingMore = true) }
            when (val res = fetch(s, page = page, pageSize = PAGE_SIZE, onlyClaimed = s.onlyOnline)) {
                is ApiResponse.Success -> _state.update { current ->
                    val seen = current.results.mapTo(HashSet()) { it.id }
                    current.copy(
                        results = current.results + res.data.results.map { it.toDomain() }.filter { it.id !in seen },
                        nextPage = res.data.next,
                        isLoadingMore = false,
                    )
                }
                is ApiResponse.Error -> _state.update { it.copy(isLoadingMore = false) }
            }
        }
    }

    private suspend fun fetch(
        s: ExploreState,
        page: Int,
        pageSize: Int,
        onlyClaimed: Boolean,
        ignoreQuery: Boolean = false,
    ) = businessRepository.getClientBusinesses(
        page = page,
        pageSize = pageSize,
        search = if (ignoreQuery) null else s.query.trim().takeIf { it.isNotEmpty() },
        category = s.category.key,
        insuranceSlug = s.insuranceSlug,
        // A city is matched by code, not by distance from its centre.
        lat = s.area.takeIf { it !is SearchArea.City }?.latitude,
        lng = s.area.takeIf { it !is SearchArea.City }?.longitude,
        radiusKm = if (s.hasLocation && s.area !is SearchArea.City) s.radiusKm else null,
        cityCode = (s.area as? SearchArea.City)?.code,
        onlyClaimed = onlyClaimed,
        sort = s.sort.apiValue,
    )
}
