package xyz.sattar.javid.proqueue.feature

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import xyz.sattar.javid.proqueue.core.location.IranCity
import xyz.sattar.javid.proqueue.core.network.ApiResponse
import xyz.sattar.javid.proqueue.feature.discovery.CategoryGroup
import xyz.sattar.javid.proqueue.feature.explore.ExploreViewModel
import xyz.sattar.javid.proqueue.feature.explore.SearchArea
import xyz.sattar.javid.proqueue.testsupport.FakeBusinessRepository
import xyz.sattar.javid.proqueue.testsupport.FakeCityCatalog
import xyz.sattar.javid.proqueue.testsupport.FakeClientPreferences
import xyz.sattar.javid.proqueue.testsupport.MainDispatcherSetup
import xyz.sattar.javid.proqueue.testsupport.businessDto
import xyz.sattar.javid.proqueue.testsupport.page
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class ExploreViewModelTest {
    private val main = MainDispatcherSetup()
    private val repo = FakeBusinessRepository()
    private val tehran = IranCity(301, "تهران", 35.7, 51.4, 25.0)

    @BeforeTest fun setUp() = main.setUp()
    @AfterTest fun tearDown() = main.tearDown()

    private fun vm(prefs: FakeClientPreferences = FakeClientPreferences(cities = listOf(301))) =
        ExploreViewModel(repo, prefs, FakeCityCatalog(listOf(tehran)), main.dispatcher)

    @Test
    fun savedCityIsRestoredAndFiltersTheFirstSearch() = runTest(main.dispatcher) {
        repo.listResponse = { page(businessDto(1), businessDto(2)) }
        val vm = vm()
        advanceUntilIdle()
        val s = vm.state.value
        assertEquals(SearchArea.City(301, "تهران", 35.7, 51.4, 25.0), s.area)
        assertEquals(listOf(1L, 2L), s.results.map { it.id })
        assertEquals(301, repo.searches.first().cityCode)
        assertFalse(s.needsCityPick)
        assertFalse(s.isLoading)
    }

    @Test
    fun noSavedCityAsksToPickAndSearchesAnywhere() = runTest(main.dispatcher) {
        val vm = vm(FakeClientPreferences())
        advanceUntilIdle()
        assertTrue(vm.state.value.needsCityPick)
        assertEquals(SearchArea.Anywhere, vm.state.value.area)
        assertNull(repo.searches.first().cityCode)
    }

    @Test
    fun selectingACityPersistsItAndRefilters() = runTest(main.dispatcher) {
        val prefs = FakeClientPreferences()
        val vm = vm(prefs)
        advanceUntilIdle()
        vm.selectCity(tehran.let { SearchArea.City(it.id, it.name, it.lat, it.lng, it.radiusKm) })
        advanceUntilIdle()
        assertEquals(listOf(301), prefs.cities.value)
        assertEquals(listOf(301), vm.state.value.recentCityCodes)
        assertFalse(vm.state.value.needsCityPick)
        assertEquals(301, repo.searches.last().cityCode)
    }

    @Test
    fun typingIsDebouncedAndOnlyTheLastQueryIsSearched() = runTest(main.dispatcher) {
        val vm = vm()
        advanceUntilIdle()
        val before = repo.searches.size
        vm.updateQuery("دند")
        advanceTimeBy(100)
        vm.updateQuery("دندانپزشک")
        advanceUntilIdle()
        val queries = repo.searches.drop(before).mapNotNull { it.search }.distinct()
        assertEquals(listOf("دندانپزشک"), queries)
    }

    @Test
    fun submitSearchRunsAtOnceAndIsRemembered() = runTest(main.dispatcher) {
        val prefs = FakeClientPreferences(cities = listOf(301))
        val vm = vm(prefs)
        advanceUntilIdle()
        vm.submitSearch("  ارتودنسی ")
        advanceUntilIdle()
        assertTrue(repo.searches.any { it.search == "ارتودنسی" })
        assertEquals(listOf("ارتودنسی"), prefs.recents.value)
        assertEquals(listOf("ارتودنسی"), vm.state.value.recentSearches)
    }

    @Test
    fun oneCharacterQueriesAreNotRemembered() = runTest(main.dispatcher) {
        val prefs = FakeClientPreferences(cities = listOf(301))
        val vm = vm(prefs)
        advanceUntilIdle()
        vm.submitSearch("د")
        advanceUntilIdle()
        assertTrue(prefs.recents.value.isEmpty())
    }

    @Test
    fun recentSearchesAreDedupedNewestFirstAndClearable() = runTest(main.dispatcher) {
        val prefs = FakeClientPreferences(cities = listOf(301))
        val vm = vm(prefs)
        advanceUntilIdle()
        vm.submitSearch("الف‌ب")
        vm.submitSearch("جیم")
        vm.submitSearch("الف‌ب")
        advanceUntilIdle()
        assertEquals(listOf("الف‌ب", "جیم"), vm.state.value.recentSearches)
        vm.clearRecentSearches()
        advanceUntilIdle()
        assertTrue(vm.state.value.recentSearches.isEmpty())
    }

    @Test
    fun onlyMedicalLikeCategoriesHaveAnInsuranceFilter() = runTest(main.dispatcher) {
        val vm = vm()
        vm.selectInsurance("tamin")
        vm.selectCategory(CategoryGroup.DENTAL)
        assertEquals("tamin", vm.state.value.insuranceSlug)

        vm.selectCategory(CategoryGroup.BEAUTY)
        assertNull(vm.state.value.insuranceSlug)
        assertFalse(vm.state.value.category.supportsInsurance)
        assertTrue(CategoryGroup.entries.filter { it.supportsInsurance }.containsAll(listOf(CategoryGroup.ALL, CategoryGroup.MEDICAL, CategoryGroup.DENTAL)))
    }

    @Test
    fun categoryAndOnlineFiltersReachTheRepository() = runTest(main.dispatcher) {
        val vm = vm()
        advanceUntilIdle()
        vm.selectCategory(CategoryGroup.DENTAL)
        advanceUntilIdle()
        assertEquals("DENTAL", repo.searches.last().category)
        vm.toggleOnlyOnline()
        advanceUntilIdle()
        assertTrue(repo.searches.last().onlyClaimed)
        vm.clearFilters()
        advanceUntilIdle()
        assertNull(repo.searches.last().category)
        assertFalse(vm.state.value.onlyOnline)
    }

    @Test
    fun failedSearchSurfacesTheMessage() = runTest(main.dispatcher) {
        repo.listResponse = { ApiResponse.Error("offline", 0) }
        val vm = vm()
        advanceUntilIdle()
        assertEquals("offline", vm.state.value.errorMessage)
        assertFalse(vm.state.value.isLoading)
    }

    @Test
    fun loadMoreAppendsWithoutDuplicates() = runTest(main.dispatcher) {
        repo.listResponse = { c ->
            if (c.page == 1) page(businessDto(1), businessDto(2), next = 2) else page(businessDto(2), businessDto(3))
        }
        val vm = vm()
        advanceUntilIdle()
        vm.loadMore()
        advanceUntilIdle()
        assertEquals(listOf(1L, 2L, 3L), vm.state.value.results.map { it.id })
    }

    @Test
    fun toggleSavedUpdatesSavedIds() = runTest(main.dispatcher) {
        val vm = vm()
        advanceUntilIdle()
        vm.toggleSaved(7)
        advanceUntilIdle()
        assertEquals(setOf(7L), vm.state.value.savedIds)
        vm.toggleSaved(7)
        advanceUntilIdle()
        assertTrue(vm.state.value.savedIds.isEmpty())
    }
}
