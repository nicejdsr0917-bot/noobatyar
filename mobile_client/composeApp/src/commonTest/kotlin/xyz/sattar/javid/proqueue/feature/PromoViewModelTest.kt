package xyz.sattar.javid.proqueue.feature

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import xyz.sattar.javid.proqueue.core.network.ApiResponse
import xyz.sattar.javid.proqueue.data.remoteDataSource.promo.PromoApi
import xyz.sattar.javid.proqueue.data.remoteDataSource.promo.PromoDto
import xyz.sattar.javid.proqueue.data.remoteDataSource.promo.PromoListDto
import xyz.sattar.javid.proqueue.data.remoteDataSource.promo.PromoPlacement
import xyz.sattar.javid.proqueue.feature.promo.PromoViewModel
import xyz.sattar.javid.proqueue.testsupport.MainDispatcherSetup
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

@OptIn(ExperimentalCoroutinesApi::class)
class PromoViewModelTest {
    private val main = MainDispatcherSetup()

    private class FakePromoApi(var response: ApiResponse<PromoListDto>) : PromoApi {
        val asked = mutableListOf<PromoPlacement>()
        override suspend fun promos(placement: PromoPlacement): ApiResponse<PromoListDto> {
            asked += placement
            return response
        }
    }

    @BeforeTest fun setUp() { main.setUp(); PromoViewModel.resetDismissed() }
    @AfterTest fun tearDown() = main.tearDown()

    private val two = ApiResponse.Success(PromoListDto(listOf(PromoDto(1, "اول"), PromoDto(2, "دوم"))))

    @Test
    fun showsTheFirstAndClosingMovesToTheNext() = runTest(main.dispatcher) {
        val api = FakePromoApi(two)
        val vm = PromoViewModel(api, main.dispatcher)
        vm.load(PromoPlacement.EXPLORE)
        assertEquals(1L, vm.current.value?.id)
        vm.dismiss()
        assertEquals(2L, vm.current.value?.id)
        vm.dismiss()
        assertNull(vm.current.value)
    }

    @Test
    fun aClosedPromoStaysClosedOnAnotherScreenThisSession() = runTest(main.dispatcher) {
        PromoViewModel(FakePromoApi(two), main.dispatcher).apply { load(PromoPlacement.EXPLORE); dismiss() }
        val other = PromoViewModel(FakePromoApi(two), main.dispatcher)
        other.load(PromoPlacement.BUSINESS_MAP)
        assertEquals(2L, other.current.value?.id)
    }

    @Test
    fun aFailedLoadShowsNothingAndLoadsOnce() = runTest(main.dispatcher) {
        val api = FakePromoApi(ApiResponse.Error("down", 500))
        val vm = PromoViewModel(api, main.dispatcher)
        vm.load(PromoPlacement.EXPLORE)
        vm.load(PromoPlacement.EXPLORE)
        assertNull(vm.current.value)
        assertEquals(1, api.asked.size)
    }
}
