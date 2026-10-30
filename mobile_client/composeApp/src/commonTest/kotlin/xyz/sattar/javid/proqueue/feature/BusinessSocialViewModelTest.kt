package xyz.sattar.javid.proqueue.feature

import app.cash.turbine.test
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import xyz.sattar.javid.proqueue.core.network.ApiResponse
import xyz.sattar.javid.proqueue.data.remoteDataSource.social.model.CertificateDto
import xyz.sattar.javid.proqueue.data.remoteDataSource.social.model.MyReviewDto
import xyz.sattar.javid.proqueue.data.remoteDataSource.social.model.PortfolioItemDto
import xyz.sattar.javid.proqueue.data.remoteDataSource.social.model.ShowcaseDto
import xyz.sattar.javid.proqueue.feature.businessDetail.BusinessSocialViewModel
import xyz.sattar.javid.proqueue.feature.businessDetail.DetailMessage
import xyz.sattar.javid.proqueue.feature.businessDetail.formatRatingAverage
import xyz.sattar.javid.proqueue.testsupport.FakeBusinessSocialApi
import xyz.sattar.javid.proqueue.testsupport.FakeClientPreferences
import xyz.sattar.javid.proqueue.testsupport.MainDispatcherSetup
import xyz.sattar.javid.proqueue.testsupport.review
import xyz.sattar.javid.proqueue.testsupport.reviewPage
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class BusinessSocialViewModelTest {
    private val main = MainDispatcherSetup()
    private val api = FakeBusinessSocialApi()
    private val prefs = FakeClientPreferences()

    @BeforeTest fun setUp() = main.setUp()
    @AfterTest fun tearDown() = main.tearDown()

    private fun vm() = BusinessSocialViewModel(api, prefs, main.dispatcher)

    // ── showcase ──

    @Test
    fun showcaseItemsLoad() = runTest(main.dispatcher) {
        api.showcase = ApiResponse.Success(
            ShowcaseDto(
                portfolio = listOf(PortfolioItemDto(1, "a.jpg")),
                certificates = listOf(CertificateDto(2, "c.jpg", title = "گواهی", year = 1402)),
            )
        )
        val vm = vm()
        vm.load(3)
        advanceUntilIdle()
        assertEquals(1, vm.state.value.portfolio.size)
        assertEquals("گواهی", vm.state.value.certificates.single().title)
    }

    @Test
    fun failedShowcaseLeavesSectionsEmpty() = runTest(main.dispatcher) {
        api.showcase = ApiResponse.Error("boom", 500)
        val vm = vm()
        vm.load(3)
        advanceUntilIdle()
        assertTrue(vm.state.value.portfolio.isEmpty())
        assertTrue(vm.state.value.certificates.isEmpty())
    }

    // ── reviews ──

    @Test
    fun summaryAndFirstPageLoad() = runTest(main.dispatcher) {
        api.pages[1] = reviewPage(review(1), review(2), next = 2)
        val vm = vm()
        vm.load(3)
        advanceUntilIdle()
        val s = vm.state.value
        assertEquals(2, s.summary?.count)
        assertEquals(listOf(1L, 2L), s.reviews.map { it.id })
        assertTrue(s.hasMoreReviews)
    }

    @Test
    fun failedSummaryHidesTheReviewsBlock() = runTest(main.dispatcher) {
        api.summary = ApiResponse.Error("boom", 500)
        api.pages[1] = reviewPage(review(1))
        val vm = vm()
        vm.load(3)
        advanceUntilIdle()
        assertNull(vm.state.value.summary)
    }

    @Test
    fun loadMoreAppendsTheNextPageAndStopsAtTheEnd() = runTest(main.dispatcher) {
        api.pages[1] = reviewPage(review(1), next = 2)
        api.pages[2] = reviewPage(review(2), review(1), next = null)
        val vm = vm()
        vm.load(3)
        advanceUntilIdle()
        vm.loadMoreReviews()
        advanceUntilIdle()
        assertEquals(listOf(1L, 2L), vm.state.value.reviews.map { it.id })
        assertFalse(vm.state.value.hasMoreReviews)
        vm.loadMoreReviews() // nothing left: no further request
        advanceUntilIdle()
        assertEquals(listOf(1, 2), api.requestedPages)
    }

    @Test
    fun loadMoreFailureKeepsTheListAndReportsTheError() = runTest(main.dispatcher) {
        api.pages[1] = reviewPage(review(1), next = 2)
        val vm = vm()
        vm.load(3)
        advanceUntilIdle()
        vm.messages.test {
            vm.loadMoreReviews()
            assertIs<DetailMessage.Error>(awaitItem())
        }
        assertEquals(1, vm.state.value.reviews.size)
        assertFalse(vm.state.value.isLoadingMoreReviews)
    }

    @Test
    fun averageIsOneDecimalInPersianDigits() {
        assertEquals("۴٫۵", formatRatingAverage(4.5))
        assertEquals("۳٫۷", formatRatingAverage(3.666))
        assertEquals("۵٫۰", formatRatingAverage(5.0))
        assertEquals("۰٫۰", formatRatingAverage(0.0))
    }

    // ── own review ──

    @Test
    fun signedOutVisitorHasNoOwnReview() = runTest(main.dispatcher) {
        val vm = vm()
        vm.load(3)
        advanceUntilIdle()
        assertFalse(vm.state.value.isSignedIn)
        assertNull(vm.state.value.myReview)
    }

    @Test
    fun signedInVisitorWithAReviewSeesIt() = runTest(main.dispatcher) {
        prefs.token.value = "tok"
        api.mine = ApiResponse.Success(MyReviewDto(7, 3, 4, "خوب"))
        val vm = vm()
        vm.load(3)
        advanceUntilIdle()
        assertTrue(vm.state.value.isSignedIn)
        assertEquals(7L, vm.state.value.myReview?.id)
    }

    @Test
    fun signedInVisitorWithoutAReviewCanWrite() = runTest(main.dispatcher) {
        prefs.token.value = "tok"
        val vm = vm()
        vm.load(3)
        advanceUntilIdle()
        assertTrue(vm.state.value.isSignedIn)
        assertNull(vm.state.value.myReview)
    }

    @Test
    fun expiredSessionSignsOut() = runTest(main.dispatcher) {
        prefs.token.value = "stale"
        api.mine = ApiResponse.Error("unauthorized", 401)
        val vm = vm()
        vm.load(3)
        advanceUntilIdle()
        assertNull(prefs.token.value)
        assertFalse(vm.state.value.isSignedIn)
    }

    @Test
    fun createSendsRatingAndTrimmedTextThenRefreshes() = runTest(main.dispatcher) {
        prefs.token.value = "tok"
        api.pages[1] = reviewPage(review(1))
        val vm = vm()
        vm.load(3)
        advanceUntilIdle()
        var done = false
        vm.messages.test {
            vm.saveReview(5, "  عالی بود  ") { done = true }
            assertEquals(DetailMessage.Success("نظر شما ثبت شد"), awaitItem())
        }
        val w = api.writes.single()
        assertEquals("create", w.kind)
        assertEquals("tok", w.token)
        assertEquals(5, w.rating)
        assertEquals("عالی بود", w.text)
        assertTrue(done)
        assertEquals(9L, vm.state.value.myReview?.id)
        assertEquals(listOf(1, 1), api.requestedPages) // list reloaded
        assertFalse(vm.state.value.isSavingReview)
    }

    @Test
    fun editUsesPutWhenAReviewExists() = runTest(main.dispatcher) {
        prefs.token.value = "tok"
        api.mine = ApiResponse.Success(MyReviewDto(7, 3, 4, "خوب"))
        val vm = vm()
        vm.load(3)
        advanceUntilIdle()
        vm.messages.test {
            vm.saveReview(2, "") {}
            assertEquals(DetailMessage.Success("نظر شما ویرایش شد"), awaitItem())
        }
        assertEquals("update", api.writes.single().kind)
    }

    @Test
    fun editAfterTheWindowShowsTheServerMessage() = runTest(main.dispatcher) {
        prefs.token.value = "tok"
        api.mine = ApiResponse.Success(MyReviewDto(7, 3, 4, "خوب"))
        api.updateResponse = ApiResponse.Error("مهلت ویرایش نظر گذشته است", 403)
        val vm = vm()
        vm.load(3)
        advanceUntilIdle()
        var done = false
        vm.messages.test {
            vm.saveReview(2, "x") { done = true }
            assertEquals(DetailMessage.Error("مهلت ویرایش نظر گذشته است"), awaitItem())
        }
        assertFalse(done)
        assertEquals(7L, vm.state.value.myReview?.id) // untouched
        assertFalse(vm.state.value.isSavingReview)
    }

    @Test
    fun invalidInputNeverReachesTheServer() = runTest(main.dispatcher) {
        prefs.token.value = "tok"
        val vm = vm()
        vm.load(3)
        advanceUntilIdle()
        vm.saveReview(0, "x") {}
        vm.saveReview(6, "x") {}
        vm.saveReview(3, "a".repeat(1001)) {}
        advanceUntilIdle()
        assertTrue(api.writes.isEmpty())
    }

    @Test
    fun deleteClearsTheOwnReview() = runTest(main.dispatcher) {
        prefs.token.value = "tok"
        api.mine = ApiResponse.Success(MyReviewDto(7, 3, 4, "خوب"))
        val vm = vm()
        vm.load(3)
        advanceUntilIdle()
        var done = false
        vm.messages.test {
            vm.deleteReview { done = true }
            assertEquals(DetailMessage.Success("نظر شما حذف شد"), awaitItem())
        }
        assertEquals("delete", api.writes.single().kind)
        assertNull(vm.state.value.myReview)
        assertTrue(done)
    }

    @Test
    fun deleteFailureKeepsTheReview() = runTest(main.dispatcher) {
        prefs.token.value = "tok"
        api.mine = ApiResponse.Success(MyReviewDto(7, 3, 4, "خوب"))
        api.deleteResponse = ApiResponse.Error("429", 429)
        val vm = vm()
        vm.load(3)
        advanceUntilIdle()
        vm.messages.test {
            vm.deleteReview {}
            assertEquals(DetailMessage.Error("429"), awaitItem())
        }
        assertEquals(7L, vm.state.value.myReview?.id)
    }

    // ── report a review ──

    @Test
    fun reportSendsReasonAndDetailAnonymously() = runTest(main.dispatcher) {
        val vm = vm()
        vm.load(3)
        advanceUntilIdle()
        var done = false
        vm.messages.test {
            vm.reportReview(11, "SPAM", " تبلیغ ") { done = true }
            assertIs<DetailMessage.Success>(awaitItem())
        }
        val r = api.reports.single()
        assertNull(r.token)
        assertEquals(11L, r.reviewId)
        assertEquals("SPAM", r.reason)
        assertEquals("تبلیغ", r.detail)
        assertTrue(done)
        assertFalse(vm.state.value.isReportingReview)
    }

    @Test
    fun reportAsASignedInVisitorCarriesTheToken() = runTest(main.dispatcher) {
        prefs.token.value = "tok"
        val vm = vm()
        vm.load(3)
        advanceUntilIdle()
        vm.messages.test {
            vm.reportReview(11, "OTHER", "") {}
            awaitItem()
        }
        assertEquals("tok", api.reports.single().token)
    }

    @Test
    fun reportErrorIsShownAndTheDialogStaysOpen() = runTest(main.dispatcher) {
        api.reportResponse = ApiResponse.Error("too many", 429)
        val vm = vm()
        vm.load(3)
        advanceUntilIdle()
        var done = false
        vm.messages.test {
            vm.reportReview(11, "SPAM", "") { done = true }
            assertEquals(DetailMessage.Error("too many"), awaitItem())
        }
        assertFalse(done)
        assertFalse(vm.state.value.isReportingReview)
    }
}
