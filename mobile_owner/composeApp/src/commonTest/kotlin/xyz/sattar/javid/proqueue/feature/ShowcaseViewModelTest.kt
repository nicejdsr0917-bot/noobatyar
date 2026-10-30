package xyz.sattar.javid.proqueue.feature

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import xyz.sattar.javid.proqueue.core.ui.components.ToastyType
import xyz.sattar.javid.proqueue.data.repository.showcase.ShowcaseRepositoryImpl
import xyz.sattar.javid.proqueue.domain.model.showcase.SHOWCASE_MAX_ITEMS
import xyz.sattar.javid.proqueue.domain.model.showcase.ShowcaseFields
import xyz.sattar.javid.proqueue.domain.model.showcase.ShowcaseKind
import xyz.sattar.javid.proqueue.domain.model.showcase.ShowcaseStatus
import xyz.sattar.javid.proqueue.domain.model.showcase.statusEnum
import xyz.sattar.javid.proqueue.feature.showcase.ShowcaseIntent
import xyz.sattar.javid.proqueue.feature.showcase.ShowcaseViewModel
import xyz.sattar.javid.proqueue.testsupport.FakeBusinessStore
import xyz.sattar.javid.proqueue.testsupport.FakeShowcaseApi
import xyz.sattar.javid.proqueue.testsupport.MainDispatcherSetup
import xyz.sattar.javid.proqueue.testsupport.showcaseItem
import xyz.sattar.javid.proqueue.testsupport.testBusiness
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class ShowcaseViewModelTest {
    private val main = MainDispatcherSetup()

    @BeforeTest fun setUp() = main.setUp()
    @AfterTest fun tearDown() = main.tearDown()

    private val api = FakeShowcaseApi()
    private val image = byteArrayOf(1, 2, 3)

    private fun viewModel(store: FakeBusinessStore = FakeBusinessStore(testBusiness(9))) =
        ShowcaseViewModel(ShowcaseRepositoryImpl(api), store)

    private val portfolio get() = api.items.getValue(ShowcaseKind.PORTFOLIO)

    @Test
    fun loadsTheOpenTabInOrderAndLazilyTheOtherOne() = runTest {
        portfolio += listOf(showcaseItem(2, order = 1), showcaseItem(1, order = 0))
        api.items.getValue(ShowcaseKind.CERTIFICATES) += showcaseItem(7, title = "دیپلم")
        val vm = viewModel()

        vm.sendIntent(ShowcaseIntent.Load); advanceUntilIdle()
        assertEquals(listOf(1L, 2L), vm.uiState.value.portfolio.map { it.id })
        assertTrue(vm.uiState.value.certificates.isEmpty())

        vm.sendIntent(ShowcaseIntent.SelectKind(ShowcaseKind.CERTIFICATES)); advanceUntilIdle()
        assertEquals(ShowcaseKind.CERTIFICATES, vm.uiState.value.selectedKind)
        assertEquals(listOf(7L), vm.uiState.value.items.map { it.id })
    }

    @Test
    fun statusMappingCoversAllThreeStatesAndDefaultsToPending() {
        assertEquals(ShowcaseStatus.PENDING, showcaseItem(1, status = "PENDING").statusEnum)
        assertEquals(ShowcaseStatus.APPROVED, showcaseItem(1, status = "APPROVED").statusEnum)
        assertEquals(ShowcaseStatus.REJECTED, showcaseItem(1, status = "rejected").statusEnum)
        assertEquals(ShowcaseStatus.PENDING, showcaseItem(1, status = "SOMETHING_NEW").statusEnum)
        assertEquals(1f, ShowcaseKind.PORTFOLIO.aspectRatio)
        assertEquals(0.8f, ShowcaseKind.CERTIFICATES.aspectRatio)
        assertEquals(1350, ShowcaseKind.CERTIFICATES.outputHeight)
    }

    @Test
    fun addingAPhotoAppendsItAsPendingAndToastsSuccess() = runTest {
        val vm = viewModel()
        vm.sendIntent(ShowcaseIntent.Load); advanceUntilIdle()

        vm.sendIntent(ShowcaseIntent.Add(ShowcaseKind.PORTFOLIO, image, ShowcaseFields(caption = "رنگ و مش"))); advanceUntilIdle()

        val state = vm.uiState.value
        assertEquals(1, state.portfolio.size)
        assertEquals(ShowcaseStatus.PENDING, state.portfolio.single().statusEnum)
        assertEquals("رنگ و مش", state.portfolio.single().caption)
        assertEquals(Triple(ShowcaseKind.PORTFOLIO, 9L, ShowcaseFields(caption = "رنگ و مش")), api.created.single())
        assertEquals(ToastyType.Success, state.message?.type)
        assertFalse(state.isBusy)
    }

    @Test
    fun aCertificateNeedsATitleAndASaneYearBeforeAnythingIsUploaded() = runTest {
        val vm = viewModel()
        vm.sendIntent(ShowcaseIntent.Load); advanceUntilIdle()

        vm.sendIntent(ShowcaseIntent.Add(ShowcaseKind.CERTIFICATES, image, ShowcaseFields(title = "  "))); advanceUntilIdle()
        assertEquals(ToastyType.Error, vm.uiState.value.message?.type)
        vm.sendIntent(ShowcaseIntent.ClearMessage); advanceUntilIdle()
        vm.sendIntent(ShowcaseIntent.Add(ShowcaseKind.CERTIFICATES, image, ShowcaseFields(title = "دیپلم", year = 1200))); advanceUntilIdle()

        assertNotNull(vm.uiState.value.message)
        assertTrue(api.created.isEmpty())

        vm.sendIntent(ShowcaseIntent.Add(ShowcaseKind.CERTIFICATES, image, ShowcaseFields(title = "دیپلم", issuer = "نظام پزشکی", year = 1402))); advanceUntilIdle()
        assertEquals(1, api.created.size)
        assertEquals(1402, vm.uiState.value.certificates.single().year)
    }

    @Test
    fun theThirtyItemLimitIsEnforcedWithoutCallingTheServer() = runTest {
        repeat(SHOWCASE_MAX_ITEMS) { portfolio += showcaseItem(it + 1L, order = it) }
        val vm = viewModel()
        vm.sendIntent(ShowcaseIntent.Load); advanceUntilIdle()

        vm.sendIntent(ShowcaseIntent.Add(ShowcaseKind.PORTFOLIO, image, ShowcaseFields())); advanceUntilIdle()

        assertTrue(api.created.isEmpty())
        assertEquals(SHOWCASE_MAX_ITEMS, vm.uiState.value.portfolio.size)
        assertEquals(ToastyType.Error, vm.uiState.value.message?.type)
    }

    @Test
    fun aServerRefusalOnUploadKeepsTheListAndShowsTheServerMessage() = runTest {
        val vm = viewModel()
        vm.sendIntent(ShowcaseIntent.Load); advanceUntilIdle()
        api.failNext = "تصویر خیلی کوچک است"

        vm.sendIntent(ShowcaseIntent.Add(ShowcaseKind.PORTFOLIO, image, ShowcaseFields())); advanceUntilIdle()

        assertTrue(vm.uiState.value.portfolio.isEmpty())
        assertEquals("تصویر خیلی کوچک است", vm.uiState.value.message?.text)
        assertFalse(vm.uiState.value.isBusy)
    }

    @Test
    fun editingRequeuesTheItemAndClearsTheRejection() = runTest {
        portfolio += showcaseItem(1, status = "REJECTED", caption = "old").copy(rejectReason = "blurry")
        val vm = viewModel()
        vm.sendIntent(ShowcaseIntent.Load); advanceUntilIdle()
        assertEquals(ShowcaseStatus.REJECTED, vm.uiState.value.portfolio.single().statusEnum)

        vm.sendIntent(ShowcaseIntent.Edit(ShowcaseKind.PORTFOLIO, 1, ShowcaseFields(caption = "new"))); advanceUntilIdle()

        val item = vm.uiState.value.portfolio.single()
        assertEquals("new", item.caption)
        assertEquals(ShowcaseStatus.PENDING, item.statusEnum)
        assertEquals("", item.rejectReason)
    }

    @Test
    fun deleteRemovesOnlyThatItem() = runTest {
        portfolio += listOf(showcaseItem(1), showcaseItem(2, order = 1))
        val vm = viewModel()
        vm.sendIntent(ShowcaseIntent.Load); advanceUntilIdle()

        vm.sendIntent(ShowcaseIntent.Delete(ShowcaseKind.PORTFOLIO, 1)); advanceUntilIdle()

        assertEquals(listOf(2L), vm.uiState.value.portfolio.map { it.id })
        assertEquals(listOf(2L), portfolio.map { it.id })
    }

    @Test
    fun aFailedDeleteLeavesTheItemInPlace() = runTest {
        portfolio += showcaseItem(1)
        val vm = viewModel()
        vm.sendIntent(ShowcaseIntent.Load); advanceUntilIdle()
        api.failNext = "nope"

        vm.sendIntent(ShowcaseIntent.Delete(ShowcaseKind.PORTFOLIO, 1)); advanceUntilIdle()

        assertEquals(1, vm.uiState.value.portfolio.size)
        assertEquals("nope", vm.uiState.value.message?.text)
    }

    @Test
    fun movingAnItemSendsTheFullNewOrderAndAppliesIt() = runTest {
        portfolio += listOf(showcaseItem(1), showcaseItem(2, order = 1), showcaseItem(3, order = 2))
        val vm = viewModel()
        vm.sendIntent(ShowcaseIntent.Load); advanceUntilIdle()

        vm.sendIntent(ShowcaseIntent.Move(ShowcaseKind.PORTFOLIO, 3, -1)); advanceUntilIdle()

        assertEquals(listOf(listOf(1L, 3L, 2L)), api.reorders)
        assertEquals(listOf(1L, 3L, 2L), vm.uiState.value.portfolio.map { it.id })
        assertEquals(listOf(0, 1, 2), vm.uiState.value.portfolio.map { it.order })
    }

    @Test
    fun movingPastTheEndsDoesNothing() = runTest {
        portfolio += listOf(showcaseItem(1), showcaseItem(2, order = 1))
        val vm = viewModel()
        vm.sendIntent(ShowcaseIntent.Load); advanceUntilIdle()

        vm.sendIntent(ShowcaseIntent.Move(ShowcaseKind.PORTFOLIO, 1, -1)); advanceUntilIdle()
        vm.sendIntent(ShowcaseIntent.Move(ShowcaseKind.PORTFOLIO, 2, +1)); advanceUntilIdle()

        assertTrue(api.reorders.isEmpty())
    }

    @Test
    fun aRefusedReorderSnapsTheListBack() = runTest {
        portfolio += listOf(showcaseItem(1), showcaseItem(2, order = 1))
        val vm = viewModel()
        vm.sendIntent(ShowcaseIntent.Load); advanceUntilIdle()
        api.failNext = "must list every item"

        vm.sendIntent(ShowcaseIntent.Move(ShowcaseKind.PORTFOLIO, 2, -1)); advanceUntilIdle()

        assertEquals(listOf(1L, 2L), vm.uiState.value.portfolio.map { it.id })
        assertEquals("must list every item", vm.uiState.value.message?.text)
        assertFalse(vm.uiState.value.isBusy)
    }

    @Test
    fun noSelectedBusinessIsABlockingError() = runTest {
        val vm = viewModel(FakeBusinessStore(null))

        vm.sendIntent(ShowcaseIntent.Load); advanceUntilIdle()

        assertNotNull(vm.uiState.value.errorMessage)
        assertNull(vm.uiState.value.message)
    }
}
