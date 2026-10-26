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

    /** A city centre picked by hand — for a denied permission, or browsing another city. */
    data class City(override val label: String, val latitude: Double, val longitude: Double) : SearchArea

    /** No location at all: whole directory, claimed businesses first. */
    data object Anywhere : SearchArea {
        override val label = "همه شهرها"
    }
}

val SearchArea.latitude: Double?
    get() = when (this) {
        is SearchArea.NearMe -> latitude
        is SearchArea.City -> latitude
        SearchArea.Anywhere -> null
    }

val SearchArea.longitude: Double?
    get() = when (this) {
        is SearchArea.NearMe -> longitude
        is SearchArea.City -> longitude
        SearchArea.Anywhere -> null
    }

val majorCities = listOf(
    SearchArea.City("تهران", 35.6997, 51.3380),
    SearchArea.City("مشهد", 36.2972, 59.6067),
    SearchArea.City("اصفهان", 32.6539, 51.6660),
    SearchArea.City("کرج", 35.8327, 50.9915),
    SearchArea.City("شیراز", 29.5918, 52.5837),
    SearchArea.City("تبریز", 38.0800, 46.2919),
    SearchArea.City("قم", 34.6416, 50.8746),
    SearchArea.City("اهواز", 31.3183, 48.6706),
    SearchArea.City("رشت", 37.2808, 49.5832),
    SearchArea.City("کرمانشاه", 34.3142, 47.0650),
)

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
) {
    val hasLocation: Boolean get() = area !is SearchArea.Anywhere
    val selectedInsurance: Insurance? get() = insurances.firstOrNull { it.slug == insuranceSlug }
    val hasActiveFilters: Boolean
        get() = query.isNotBlank() || category != CategoryGroup.ALL || insuranceSlug != null || onlyOnline
}
