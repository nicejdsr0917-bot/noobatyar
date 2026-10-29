package xyz.sattar.javid.proqueue.feature.explore

import androidx.compose.runtime.Immutable
import xyz.sattar.javid.proqueue.domain.model.business.Business
import xyz.sattar.javid.proqueue.domain.model.business.Insurance
import xyz.sattar.javid.proqueue.feature.discovery.CategoryGroup

/** Where results are ranked from. */
sealed interface SearchArea {
    val label: String

    /** The phone's own GPS fix. */
    data class NearMe(val latitude: Double, val longitude: Double) : SearchArea {
        override val label = "نزدیک من"
    }

    /** A city from Divar's list ([CityPickerSheet]). Results are the businesses the
     *  owner put in it ([code]); the centre only places the map. */
    data class City(
        val code: Int,
        override val label: String,
        val latitude: Double,
        val longitude: Double,
        val radiusKm: Double,
    ) : SearchArea

    /** A point the user chose on the map ("search this area", a long press, or the centre pin). */
    data class Picked(val latitude: Double, val longitude: Double) : SearchArea {
        override val label = "محدوده انتخابی روی نقشه"
    }

    /** No location at all: whole directory, claimed businesses first. */
    data object Anywhere : SearchArea {
        override val label = "همه شهرها"
    }
}

val SearchArea.latitude: Double?
    get() = when (this) {
        is SearchArea.NearMe -> latitude
        is SearchArea.City -> latitude
        is SearchArea.Picked -> latitude
        SearchArea.Anywhere -> null
    }

val SearchArea.longitude: Double?
    get() = when (this) {
        is SearchArea.NearMe -> longitude
        is SearchArea.City -> longitude
        is SearchArea.Picked -> longitude
        SearchArea.Anywhere -> null
    }


val radiusOptionsKm = listOf(3.0, 10.0, 25.0, 50.0)

enum class LocationStatus { Idle, Locating, Granted, Denied, Disabled, Unavailable }

enum class SortMode(val apiValue: String, val label: String) {
    RECOMMENDED("recommended", "پیشنهادی"),
    DISTANCE("distance", "نزدیک‌ترین"),
}

@Immutable
data class ExploreState(
    val area: SearchArea = SearchArea.Anywhere,
    val radiusKm: Double = 10.0,
    val locationStatus: LocationStatus = LocationStatus.Idle,
    val query: String = "",
    val category: CategoryGroup = CategoryGroup.ALL,
    val insuranceSlug: String? = null,
    val onlyOnline: Boolean = false,
    val sort: SortMode = SortMode.RECOMMENDED,

    val insurances: List<Insurance> = emptyList(),
    val featured: List<Business> = emptyList(),
    val results: List<Business> = emptyList(),
    val totalCount: Int = 0,
    val nextPage: Int? = null,

    val isLoading: Boolean = true,
    val isLoadingMore: Boolean = false,
    val isRefreshing: Boolean = false,
    val errorMessage: String? = null,
    val savedIds: Set<Long> = emptySet(),
    val recentSearches: List<String> = emptyList(),
    /** Newest first, Divar ids or ALL_IRAN; empty until the first pick. */
    val recentCityCodes: List<Int> = emptyList(),
    /** First launch: no city picked yet, so the picker opens by itself. */
    val needsCityPick: Boolean = false,
) {
    val hasLocation: Boolean get() = area !is SearchArea.Anywhere
    val selectedInsurance: Insurance? get() = insurances.firstOrNull { it.slug == insuranceSlug }
    val hasActiveFilters: Boolean
        get() = query.isNotBlank() || category != CategoryGroup.ALL || insuranceSlug != null || onlyOnline
}
