package xyz.sattar.javid.proqueue.feature

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import xyz.sattar.javid.proqueue.core.network.ApiResponse
import xyz.sattar.javid.proqueue.data.remoteDataSource.showcase.model.OwnerReviewDto
import xyz.sattar.javid.proqueue.data.remoteDataSource.showcase.model.OwnerReviewsPageDto
import xyz.sattar.javid.proqueue.data.remoteDataSource.showcase.model.ReviewSummaryDto
import xyz.sattar.javid.proqueue.data.repository.showcase.ShowcaseRepositoryImpl
import xyz.sattar.javid.proqueue.feature.reviews.ReviewsIntent
import xyz.sattar.javid.proqueue.feature.reviews.ReviewsViewModel
import xyz.sattar.javid.proqueue.feature.reviews.formatAverage
import xyz.sattar.javid.proqueue.feature.reviews.histogramFraction
import xyz.sattar.javid.proqueue.feature.reviews.reviewDate
import xyz.sattar.javid.proqueue.testsupport.FakeBusinessStore
import xyz.sattar.javid.proqueue.testsupport.FakeShowcaseApi
import xyz.sattar.javid.proqueue.testsupport.MainDispatcherSetup
import xyz.sattar.javid.proqueue.testsupport.testBusiness
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class ReviewsViewModelTest {
    private val main = MainDispatcherSetup()

    @BeforeTest fun setUp() = main.setUp()
    @AfterTest fun tearDown() = main.tearDown()

    private val api = FakeShowcaseApi()
    private val summary = ReviewSummaryDto(avg = 4.5, count = 3, histogram = mapOf("5" to 2, "4" to 1))

    private fun review(id: Long, hidden: Boolean = false) =
        OwnerReviewDto(id, rating = 5, text = "r$id", createdAt = "2026-10-30T09:00:00Z", visitorName = "n$id", isHidden = hidden)

    private fun viewModel(store: FakeBusinessStore = FakeBusinessStore(testBusiness(4))) =
        ReviewsViewModel(ShowcaseRepositoryImpl(api), store)

    @Test
    fun loadsTheFirstPageWithSummaryAndHiddenFlag() = runTest {
        val asked = mutableListOf<Triple<Long, Int, Int>>()
        api.reviews = { business, page, size ->
            asked += Triple(business, page, size)
            ApiResponse.Success(OwnerReviewsPageDto(count = 2, results = listOf(review(1), review(2, hidden = true)), summary = summary))
        }
        val vm = viewModel()

        vm.sendIntent(ReviewsIntent.Load); advanceUntilIdle()

        val state = vm.uiState.value
        assertEquals(listOf(Triple(4L, 1, 20)), asked)
        assertEquals(listOf(1L, 2L), state.reviews.map { it.id })
        assertEquals(listOf(false, true), state.reviews.map { it.isHidden })
        assertEquals(4.5, state.summary?.avg)
        assertEquals(2, state.summary?.countFor(5))
        assertEquals(0, state.summary?.countFor(1))
        assertEquals(false, state.canLoadMore)
        assertEquals(false, state.isLoading)
    }

    @Test
    fun loadMoreAppendsTheNextPageAndStopsWhenThereIsNoNext() = runTest {
        api.reviews = { _, page, _ ->
            if (page == 1) ApiResponse.Success(OwnerReviewsPageDto(results = listOf(review(1), review(2)), next = "http://x/?page=2", summary = summary))
            else ApiResponse.Success(OwnerReviewsPageDto(results = listOf(review(2), review(3)), next = null, summary = summary))
        }
        val vm = viewModel()

        vm.sendIntent(ReviewsIntent.Load); advanceUntilIdle()
        assertTrue(vm.uiState.value.canLoadMore)
        vm.sendIntent(ReviewsIntent.LoadMore); advanceUntilIdle()

        val state = vm.uiState.value
        // Review 2 shifted across the page boundary and must not be listed twice.
        assertEquals(listOf(1L, 2L, 3L), state.reviews.map { it.id })
        assertEquals(2, state.currentPage)
        assertEquals(false, state.canLoadMore)
        vm.sendIntent(ReviewsIntent.LoadMore); advanceUntilIdle()
        assertEquals(2, state.currentPage)
    }

    @Test
    fun aFailedFirstPageIsABlockingErrorAndRetryRecovers() = runTest {
        api.reviews = { _, _, _ -> ApiResponse.Error("down", 500) }
        val vm = viewModel()

        vm.sendIntent(ReviewsIntent.Load); advanceUntilIdle()
        assertEquals("down", vm.uiState.value.errorMessage)
        assertTrue(vm.uiState.value.reviews.isEmpty())

        api.reviews = { _, _, _ -> ApiResponse.Success(OwnerReviewsPageDto(results = listOf(review(1)), summary = summary)) }
        vm.sendIntent(ReviewsIntent.Load); advanceUntilIdle()
        assertNull(vm.uiState.value.errorMessage)
        assertEquals(1, vm.uiState.value.reviews.size)
    }

    @Test
    fun aFailedFurtherPageKeepsTheRowsAndOnlyToasts() = runTest {
        api.reviews = { _, page, _ ->
            if (page == 1) ApiResponse.Success(OwnerReviewsPageDto(results = listOf(review(1)), next = "n", summary = summary))
            else ApiResponse.Error("flaky", 500)
        }
        val vm = viewModel()
        vm.sendIntent(ReviewsIntent.Load); advanceUntilIdle()

        vm.sendIntent(ReviewsIntent.LoadMore); advanceUntilIdle()

        val state = vm.uiState.value
        assertEquals(1, state.reviews.size)
        assertNull(state.errorMessage)
        assertEquals("flaky", state.message?.text)
        assertEquals(false, state.isPaginating)
    }

    @Test
    fun noSelectedBusinessIsABlockingError() = runTest {
        val vm = viewModel(FakeBusinessStore(null))

        vm.sendIntent(ReviewsIntent.Load); advanceUntilIdle()

        assertNotNull(vm.uiState.value.errorMessage)
    }

    @Test
    fun displayHelpers() {
        assertEquals("4.5", formatAverage(4.5))
        assertEquals("5.0", formatAverage(5.0))
        assertEquals("0.0", formatAverage(0.0))
        assertEquals("3.7", formatAverage(3.66))
        assertTrue(histogramFraction(0, 10) < 0.01f)
        assertEquals(0.5f, histogramFraction(5, 10))
        assertEquals(1f, histogramFraction(10, 10))
        assertEquals("", reviewDate("not a date"))
        assertTrue(reviewDate("2026-10-30T09:00:00Z").matches(Regex("""\d{4}/\d{2}/\d{2}""")))
    }
}
