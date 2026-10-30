package xyz.sattar.javid.proqueue.feature

import app.cash.turbine.test
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import xyz.sattar.javid.proqueue.core.network.ApiResponse
import xyz.sattar.javid.proqueue.data.remoteDataSource.business.model.RequestBookingResponseDto
import xyz.sattar.javid.proqueue.feature.businessDetail.ClientBusinessDetailViewModel
import xyz.sattar.javid.proqueue.feature.businessDetail.DetailMessage
import xyz.sattar.javid.proqueue.feature.saved.SavedViewModel
import xyz.sattar.javid.proqueue.testsupport.FakeBusinessRepository
import xyz.sattar.javid.proqueue.testsupport.FakeClientPreferences
import xyz.sattar.javid.proqueue.testsupport.MainDispatcherSetup
import xyz.sattar.javid.proqueue.testsupport.businessDto
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class DetailAndSavedViewModelTest {
    private val main = MainDispatcherSetup()
    private val repo = FakeBusinessRepository()

    @BeforeTest fun setUp() = main.setUp()
    @AfterTest fun tearDown() = main.tearDown()

    // ── ClientBusinessDetailViewModel ──

    @Test
    fun loadShowsTheBusinessAndSavedFlag() = runTest(main.dispatcher) {
        repo.details[5] = ApiResponse.Success(businessDto(5, "کلینیک"))
        val vm = ClientBusinessDetailViewModel(repo, FakeClientPreferences(saved = setOf(5)), main.dispatcher)
        vm.load(5)
        advanceUntilIdle()
        val s = vm.state.value
        assertFalse(s.isLoading)
        assertEquals("کلینیک", s.business?.title)
        assertTrue(s.isSaved)
    }

    @Test
    fun loadErrorIsExposed() = runTest(main.dispatcher) {
        val vm = ClientBusinessDetailViewModel(repo, FakeClientPreferences(), main.dispatcher)
        vm.load(9)
        advanceUntilIdle()
        assertEquals("not found", vm.state.value.errorMessage)
        assertEquals(null, vm.state.value.business)
    }

    @Test
    fun toggleSavedSavesThenUnsaves() = runTest(main.dispatcher) {
        repo.details[5] = ApiResponse.Success(businessDto(5))
        val prefs = FakeClientPreferences()
        val vm = ClientBusinessDetailViewModel(repo, prefs, main.dispatcher)
        vm.load(5)
        advanceUntilIdle()
        vm.toggleSaved()
        advanceUntilIdle()
        assertTrue(vm.state.value.isSaved)
        assertEquals(setOf(5L), prefs.saved.value)
        vm.toggleSaved()
        advanceUntilIdle()
        assertFalse(vm.state.value.isSaved)
    }

    @Test
    fun requestOnlineBookingUpdatesCountAndEmitsMessageOnce() = runTest(main.dispatcher) {
        repo.details[5] = ApiResponse.Success(businessDto(5))
        repo.bookingResponse = ApiResponse.Success(RequestBookingResponseDto(5, 12))
        val vm = ClientBusinessDetailViewModel(repo, FakeClientPreferences(), main.dispatcher)
        vm.load(5)
        advanceUntilIdle()
        vm.messages.test {
            vm.requestOnlineBooking()
            assertIs<DetailMessage.Success>(awaitItem())
            vm.requestOnlineBooking() // already requested: ignored
            expectNoEvents()
        }
        assertEquals(12, vm.state.value.bookingRequestsCount)
        assertTrue(vm.state.value.hasRequestedBooking)
    }

    @Test
    fun requestOnlineBookingErrorEmitsErrorMessage() = runTest(main.dispatcher) {
        repo.details[5] = ApiResponse.Success(businessDto(5))
        repo.bookingResponse = ApiResponse.Error("rate limited", 429)
        val vm = ClientBusinessDetailViewModel(repo, FakeClientPreferences(), main.dispatcher)
        vm.load(5)
        advanceUntilIdle()
        vm.messages.test {
            vm.requestOnlineBooking()
            val m = awaitItem()
            assertIs<DetailMessage.Error>(m)
            assertEquals("rate limited", m.text)
        }
        assertFalse(vm.state.value.hasRequestedBooking)
    }

    // ── SavedViewModel ──

    @Test
    fun savedListLoadsInSavedOrderAndSkipsFailures() = runTest(main.dispatcher) {
        repo.details[1] = ApiResponse.Success(businessDto(1))
        repo.details[3] = ApiResponse.Success(businessDto(3))
        // 2 has no detail -> 404, skipped
        val vm = SavedViewModel(repo, FakeClientPreferences(saved = linkedSetOf(3, 2, 1)), main.dispatcher)
        advanceUntilIdle()
        assertFalse(vm.state.value.isLoading)
        assertEquals(listOf(3L, 1L), vm.state.value.businesses.map { it.id })
    }

    @Test
    fun unsaveRemovesWithoutRefetchingTheRest() = runTest(main.dispatcher) {
        repo.details[1] = ApiResponse.Success(businessDto(1))
        repo.details[3] = ApiResponse.Success(businessDto(3))
        val vm = SavedViewModel(repo, FakeClientPreferences(saved = linkedSetOf(1, 3)), main.dispatcher)
        advanceUntilIdle()
        val fetched = repo.detailCalls.size
        vm.unsave(1)
        advanceUntilIdle()
        assertEquals(listOf(3L), vm.state.value.businesses.map { it.id })
        assertEquals(fetched, repo.detailCalls.size)
    }

    @Test
    fun emptyBookmarksEndInEmptyNotLoading() = runTest(main.dispatcher) {
        val vm = SavedViewModel(repo, FakeClientPreferences(), main.dispatcher)
        advanceUntilIdle()
        assertFalse(vm.state.value.isLoading)
        assertTrue(vm.state.value.businesses.isEmpty())
        assertNotNull(vm.state.value)
    }
}
