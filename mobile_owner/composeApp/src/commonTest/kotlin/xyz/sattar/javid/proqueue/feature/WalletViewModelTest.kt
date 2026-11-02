package xyz.sattar.javid.proqueue.feature

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import xyz.sattar.javid.proqueue.core.network.ApiResponse
import xyz.sattar.javid.proqueue.data.remoteDataSource.user.model.EntitlementKeys
import xyz.sattar.javid.proqueue.data.remoteDataSource.user.model.WalletBalancesDto
import xyz.sattar.javid.proqueue.data.remoteDataSource.user.model.WalletHistoryDto
import xyz.sattar.javid.proqueue.data.remoteDataSource.user.model.WalletLedgerEntryDto
import xyz.sattar.javid.proqueue.data.remoteDataSource.user.model.WalletMetric
import xyz.sattar.javid.proqueue.data.remoteDataSource.user.model.WalletConvertDto
import xyz.sattar.javid.proqueue.data.remoteDataSource.user.model.WalletConvertKind
import xyz.sattar.javid.proqueue.domain.usecase.user.ConvertWalletUseCase
import xyz.sattar.javid.proqueue.domain.usecase.user.GetWalletHistoryUseCase
import xyz.sattar.javid.proqueue.feature.wallet.WalletIntent
import xyz.sattar.javid.proqueue.feature.wallet.WalletViewModel
import xyz.sattar.javid.proqueue.testsupport.MainDispatcherSetup
import xyz.sattar.javid.proqueue.testsupport.StubUserRepository
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class WalletViewModelTest {
    private val main = MainDispatcherSetup()

    @BeforeTest fun setUp() = main.setUp()
    @AfterTest fun tearDown() = main.tearDown()

    private class FakeRepo(
        val convertResult: ApiResponse<WalletConvertDto> = ApiResponse.Error("not stubbed", 0),
        val history: (page: Int, size: Int) -> ApiResponse<WalletHistoryDto>
    ) : StubUserRepository() {
        val asked = mutableListOf<Pair<Int, Int>>()
        val converted = mutableListOf<Pair<String, Int>>()
        override suspend fun getWalletHistory(page: Int, pageSize: Int): ApiResponse<WalletHistoryDto> {
            asked += page to pageSize
            return history(page, pageSize)
        }
        override suspend fun convertWallet(kind: String, count: Int): ApiResponse<WalletConvertDto> {
            converted += kind to count
            return convertResult
        }
    }

    private fun entry(id: Long, delta: Int = -1, metric: String = WalletMetric.APPOINTMENTS) =
        WalletLedgerEntryDto(id = id, metric = metric, delta = delta, balanceAfter = 10, reason = "booking")

    private val balances = WalletBalancesDto(appointmentWallet = 9, smsWallet = 50, appointmentQuota = 30, smsQuota = 100)

    private fun viewModel(repo: FakeRepo) = WalletViewModel(GetWalletHistoryUseCase(repo), ConvertWalletUseCase(repo))

    @Test
    fun loadsTheFirstPageWithBalances() = runTest {
        val repo = FakeRepo { _, _ ->
            ApiResponse.Success(WalletHistoryDto(count = 2, results = listOf(entry(2), entry(1)), balances = balances))
        }
        val vm = viewModel(repo)

        vm.sendIntent(WalletIntent.Load); advanceUntilIdle()

        val state = vm.uiState.value
        assertEquals(listOf(1 to 20), repo.asked)
        assertEquals(listOf(2L, 1L), state.entries.map { it.id })
        assertEquals(9, state.balances?.appointmentWallet)
        assertEquals(50, state.balances?.smsWallet)
        assertFalse(state.canLoadMore)
        assertFalse(state.isLoading)
        assertFalse(state.appointmentCreditIdle)
    }

    @Test
    fun loadMoreAppendsWithoutDuplicatesAndStopsAtTheEnd() = runTest {
        val repo = FakeRepo { page, _ ->
            if (page == 1) ApiResponse.Success(WalletHistoryDto(results = listOf(entry(3), entry(2)), next = "x?page=2", balances = balances))
            else ApiResponse.Success(WalletHistoryDto(results = listOf(entry(2), entry(1)), next = null, balances = balances))
        }
        val vm = viewModel(repo)

        vm.sendIntent(WalletIntent.Load); advanceUntilIdle()
        assertTrue(vm.uiState.value.canLoadMore)
        vm.sendIntent(WalletIntent.LoadMore); advanceUntilIdle()
        vm.sendIntent(WalletIntent.LoadMore); advanceUntilIdle()

        assertEquals(listOf(3L, 2L, 1L), vm.uiState.value.entries.map { it.id })
        assertFalse(vm.uiState.value.canLoadMore)
        // The third request never went out: there was no next page.
        assertEquals(listOf(1, 2), repo.asked.map { it.first })
    }

    @Test
    fun aFailedFirstPageIsMarkedAndToasted() = runTest {
        val vm = viewModel(FakeRepo { _, _ -> ApiResponse.Error("boom", 500) })

        vm.sendIntent(WalletIntent.Load); advanceUntilIdle()

        val state = vm.uiState.value
        assertTrue(state.loadFailed)
        assertEquals("boom", assertNotNull(state.message).text)
        assertTrue(state.entries.isEmpty())
    }

    @Test
    fun appointmentCreditIsIdleOnAnUnlimitedPlan() = runTest {
        val unlimited = balances.copy(appointmentQuota = EntitlementKeys.UNLIMITED)
        val vm = viewModel(FakeRepo { _, _ -> ApiResponse.Success(WalletHistoryDto(balances = unlimited)) })

        vm.sendIntent(WalletIntent.Load); advanceUntilIdle()

        assertTrue(vm.uiState.value.appointmentCreditIdle)
    }

    @Test
    fun tomanBalanceComesWithTheHistory() = runTest {
        val vm = viewModel(FakeRepo { _, _ ->
            ApiResponse.Success(WalletHistoryDto(balances = balances.copy(tomanWallet = 10_000, smsUnitPrice = 200)))
        })

        vm.sendIntent(WalletIntent.Load); advanceUntilIdle()

        assertEquals(10_000, vm.uiState.value.balances?.tomanWallet)
        assertEquals(200, vm.uiState.value.balances?.smsUnitPrice)
    }

    @Test
    fun aRefusedConversionKeepsTheDialogOpenAndShowsWhy() = runTest {
        val repo = FakeRepo(convertResult = ApiResponse.Error("موجودی کیف‌پول کافی نیست", 400)) { _, _ ->
            ApiResponse.Success(WalletHistoryDto(balances = balances))
        }
        val vm = viewModel(repo)
        vm.sendIntent(WalletIntent.Load); advanceUntilIdle()

        vm.sendIntent(WalletIntent.Convert(WalletConvertKind.SMS, 6)); advanceUntilIdle()

        assertEquals(listOf(WalletConvertKind.SMS to 6), repo.converted)
        val state = vm.uiState.value
        assertEquals(0, state.convertedCount)
        assertFalse(state.isConverting)
        assertEquals("موجودی کیف‌پول کافی نیست", assertNotNull(state.message).text)
        // Nothing changed server-side, so the history is not fetched again.
        assertEquals(1, repo.asked.size)
    }
}
