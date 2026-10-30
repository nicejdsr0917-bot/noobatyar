package xyz.sattar.javid.proqueue.feature

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import xyz.sattar.javid.proqueue.core.network.ApiResponse
import xyz.sattar.javid.proqueue.data.remoteDataSource.showcase.model.BannerDto
import xyz.sattar.javid.proqueue.data.remoteDataSource.showcase.model.BannersDto
import xyz.sattar.javid.proqueue.data.repository.showcase.ShowcaseRepositoryImpl
import xyz.sattar.javid.proqueue.feature.banner.BannerIntent
import xyz.sattar.javid.proqueue.feature.banner.BannerViewModel
import xyz.sattar.javid.proqueue.testsupport.FakeShowcaseApi
import xyz.sattar.javid.proqueue.testsupport.MainDispatcherSetup
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** The home carousel: shown only when there is something to show, never an error. */
@OptIn(ExperimentalCoroutinesApi::class)
class BannerViewModelTest {
    private val main = MainDispatcherSetup()

    @BeforeTest fun setUp() = main.setUp()
    @AfterTest fun tearDown() = main.tearDown()

    private val api = FakeShowcaseApi()
    private fun viewModel() = BannerViewModel(ShowcaseRepositoryImpl(api))

    @Test
    fun showsBannersInServerOrderWithTheSharedLink() = runTest {
        api.banners = {
            ApiResponse.Success(
                BannersDto(
                    banners = listOf(BannerDto(2, "https://x/b.png", 1), BannerDto(1, "https://x/a.png", 0)),
                    linkUrl = " https://noobatyar.ir/plans "
                )
            )
        }
        val vm = viewModel()

        vm.sendIntent(BannerIntent.Load(7)); advanceUntilIdle()

        val state = vm.uiState.value
        assertTrue(state.visible)
        assertEquals(listOf(1L, 2L), state.banners.map { it.id })
        assertEquals("https://noobatyar.ir/plans", state.linkUrl)
        assertEquals(listOf(7L), api.bannerCalls)
    }

    @Test
    fun anEmptyListHidesTheCarousel() = runTest {
        api.banners = { ApiResponse.Success(BannersDto(banners = emptyList(), linkUrl = "https://x")) }
        val vm = viewModel()

        vm.sendIntent(BannerIntent.Load(7)); advanceUntilIdle()

        assertFalse(vm.uiState.value.visible)
    }

    @Test
    fun aFailedCallHidesTheCarouselInsteadOfShowingAnError() = runTest {
        api.banners = { ApiResponse.Error("boom", 500) }
        val vm = viewModel()

        vm.sendIntent(BannerIntent.Load(7)); advanceUntilIdle()

        assertFalse(vm.uiState.value.visible)
        assertEquals("", vm.uiState.value.linkUrl)
    }

    @Test
    fun aFailureAfterASuccessClearsTheOldBanners() = runTest {
        val vm = viewModel()
        api.banners = { ApiResponse.Success(BannersDto(listOf(BannerDto(1, "https://x/a.png")), "")) }
        vm.sendIntent(BannerIntent.Load(7)); advanceUntilIdle()
        assertTrue(vm.uiState.value.visible)

        api.banners = { ApiResponse.Error("offline", 0) }
        vm.sendIntent(BannerIntent.Load(7)); advanceUntilIdle()

        assertFalse(vm.uiState.value.visible)
    }

    @Test
    fun switchingBusinessDropsTheOldBannersAndLoadsTheNewOnes() = runTest {
        api.banners = { id ->
            ApiResponse.Success(BannersDto(listOf(BannerDto(id, "https://x/$id.png")), "link-$id"))
        }
        val vm = viewModel()
        vm.sendIntent(BannerIntent.Load(1)); advanceUntilIdle()
        vm.sendIntent(BannerIntent.Load(2)); advanceUntilIdle()

        assertEquals(2L, vm.uiState.value.businessId)
        assertEquals(listOf(2L), vm.uiState.value.banners.map { it.id })
        assertEquals("link-2", vm.uiState.value.linkUrl)
    }

    @Test
    fun bannersWithoutAnImageAreDropped() = runTest {
        api.banners = { ApiResponse.Success(BannersDto(listOf(BannerDto(1, ""), BannerDto(2, "https://x/b.png")), "")) }
        val vm = viewModel()

        vm.sendIntent(BannerIntent.Load(7)); advanceUntilIdle()

        assertEquals(listOf(2L), vm.uiState.value.banners.map { it.id })
    }
}
