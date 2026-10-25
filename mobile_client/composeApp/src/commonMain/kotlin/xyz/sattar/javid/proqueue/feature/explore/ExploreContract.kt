package xyz.sattar.javid.proqueue.feature.explore

import androidx.compose.runtime.Immutable
import xyz.sattar.javid.proqueue.domain.model.business.Business
import xyz.sattar.javid.proqueue.domain.model.business.Insurance

data class LocationPreset(
    val name: String,
    val lat: Double?,
    val lng: Double?,
    val radiusKm: Double? = 10.0
)

val defaultLocationPresets = listOf(
    LocationPreset("تهران — میدان ونک / ولیعصر", 35.7575, 51.4100, 10.0),
    LocationPreset("تهران — سعادت‌آباد / شهرک غرب", 35.7790, 51.3755, 10.0),
    LocationPreset("تهران — صادقیه / غرب", 35.7208, 51.3340, 10.0),
    LocationPreset("تهران — پاسداران / شرق", 35.7660, 51.4600, 10.0),
    LocationPreset("همه شهرها (بدون محدودیت مکانی)", null, null, null)
)

data class CategoryFilterItem(
    val label: String,
    val key: String?
)

val defaultCategoryFilters = listOf(
    CategoryFilterItem("همه مراکز", null),
    CategoryFilterItem("پزشکان و کلینیک 🩺", "MEDICAL"),
    CategoryFilterItem("سالن‌های زیبایی ✂️", "BEAUTY"),
    CategoryFilterItem("دندان‌پزشکی 🦷", "DENTAL"),
    CategoryFilterItem("سلامت و آزمایشگاه 🧪", "HEALTH"),
    CategoryFilterItem("مشاوره و خدمات 💼", "PROFESSIONAL")
)

val fallbackInsurances = listOf(
    Insurance(1, "بیمه دانا", "dana", "SUPPLEMENTARY"),
    Insurance(2, "بیمه آسیا", "asia", "SUPPLEMENTARY"),
    Insurance(3, "بیمه البرز", "alborz", "SUPPLEMENTARY"),
    Insurance(4, "بیمه سامان", "saman", "SUPPLEMENTARY"),
    Insurance(5, "بیمه ملت", "mellat", "SUPPLEMENTARY"),
    Insurance(6, "بیمه آتیه‌سازان حافظ", "atiyeh", "SUPPLEMENTARY"),
    Insurance(7, "تامین اجتماعی", "tamin", "BASIC"),
    Insurance(8, "بیمه ایران", "iran", "SUPPLEMENTARY"),
    Insurance(9, "بیمه دی", "dey", "SUPPLEMENTARY")
)

@Immutable
data class ExploreState(
    val isLoading: Boolean = false,
    val isRefreshing: Boolean = false,
    val businesses: List<Business> = emptyList(),
    val vipBusinesses: List<Business> = emptyList(),
    val insurances: List<Insurance> = fallbackInsurances,
    val selectedLocation: LocationPreset = defaultLocationPresets.first(),
    val selectedCategory: String? = null,
    val selectedInsuranceSlug: String? = null,
    val searchQuery: String = "",
    val onlyClaimed: Boolean = false,
    val errorMessage: String? = null
)

sealed interface ExplorePartialState {
    data class Loading(val isLoading: Boolean) : ExplorePartialState
    data class Refreshing(val isRefreshing: Boolean) : ExplorePartialState
    data class BusinessesLoaded(
        val businesses: List<Business>,
        val vipBusinesses: List<Business>
    ) : ExplorePartialState
    data class InsurancesLoaded(val insurances: List<Insurance>) : ExplorePartialState
    data class LocationChanged(val location: LocationPreset) : ExplorePartialState
    data class CategoryChanged(val category: String?) : ExplorePartialState
    data class InsuranceChanged(val insuranceSlug: String?) : ExplorePartialState
    data class SearchQueryChanged(val query: String) : ExplorePartialState
    data class OnlyClaimedChanged(val onlyClaimed: Boolean) : ExplorePartialState
    data object FiltersCleared : ExplorePartialState
    data class Error(val message: String?) : ExplorePartialState
}

sealed interface ExploreIntent {
    data object LoadInitialData : ExploreIntent
    data object Refresh : ExploreIntent
    data class SelectLocation(val preset: LocationPreset) : ExploreIntent
    data class SelectCategory(val categoryKey: String?) : ExploreIntent
    data class SelectInsurance(val insuranceSlug: String?) : ExploreIntent
    data class UpdateSearchQuery(val query: String) : ExploreIntent
    data class ToggleOnlyClaimed(val onlyClaimed: Boolean) : ExploreIntent
    data object ClearFilters : ExploreIntent
    data class BusinessClicked(val businessId: Long) : ExploreIntent
}

sealed interface ExploreEvent {
    data class NavigateToDetail(val businessId: Long) : ExploreEvent
    data class ShowToast(val message: String) : ExploreEvent
}
