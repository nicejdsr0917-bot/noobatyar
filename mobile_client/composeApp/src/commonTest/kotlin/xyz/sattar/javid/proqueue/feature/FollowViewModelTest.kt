package xyz.sattar.javid.proqueue.feature

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import xyz.sattar.javid.proqueue.core.network.ApiResponse
import xyz.sattar.javid.proqueue.feature.businessDetail.ClientBusinessDetailViewModel
import xyz.sattar.javid.proqueue.feature.following.FollowingViewModel
import xyz.sattar.javid.proqueue.testsupport.FakeBusinessRepository
import xyz.sattar.javid.proqueue.testsupport.FakeClientPreferences
import xyz.sattar.javid.proqueue.testsupport.MainDispatcherSetup
import xyz.sattar.javid.proqueue.testsupport.businessDto
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class FollowViewModelTest {
    private val main = MainDispatcherSetup()
    private val repo = FakeBusinessRepository()

    @BeforeTest fun setUp() = main.setUp()
    @AfterTest fun tearDown() = main.tearDown()

    private fun signedIn() = FakeClientPreferences().apply { token.value = "t" }

    @Test
    fun followShowsAtOnceAndTakesTheServersCount() = runTest(main.dispatcher) {
        repo.details[5] = ApiResponse.Success(businessDto(5).copy(followersCount = 9, isFollowing = false))
        repo.followResponse = { id, f -> ApiResponse.Success(xyz.sattar.javid.proqueue.data.remoteDataSource.business.model.FollowResponseDto(id, f, 12)) }
        val vm = ClientBusinessDetailViewModel(repo, signedIn(), main.dispatcher)
        vm.load(5)
        advanceUntilIdle()
        assertEquals(9, vm.state.value.followersCount)
        vm.toggleFollow {}
        advanceUntilIdle()
        assertTrue(vm.state.value.isFollowing)
        assertEquals(12, vm.state.value.followersCount)
        assertEquals(listOf(5L to true), repo.followCalls)
    }

    @Test
    fun aRefusedFollowIsPutBack() = runTest(main.dispatcher) {
        repo.details[5] = ApiResponse.Success(businessDto(5).copy(followersCount = 3, isFollowing = true))
        repo.followResponse = { _, _ -> ApiResponse.Error("خطا", 500) }
        val vm = ClientBusinessDetailViewModel(repo, signedIn(), main.dispatcher)
        vm.load(5)
        advanceUntilIdle()
        vm.toggleFollow {}
        advanceUntilIdle()
        assertTrue(vm.state.value.isFollowing)
        assertEquals(3, vm.state.value.followersCount)
        assertFalse(vm.state.value.followBusy)
    }

    @Test
    fun aGuestIsSentToSignIn() = runTest(main.dispatcher) {
        repo.details[5] = ApiResponse.Success(businessDto(5))
        val vm = ClientBusinessDetailViewModel(repo, FakeClientPreferences(), main.dispatcher)
        vm.load(5)
        advanceUntilIdle()
        var askedToSignIn = false
        vm.toggleFollow { askedToSignIn = true }
        advanceUntilIdle()
        assertTrue(askedToSignIn)
        assertTrue(repo.followCalls.isEmpty())
    }

    @Test
    fun followingListLoadsAndUnfollowDropsTheRow() = runTest(main.dispatcher) {
        repo.followingList = listOf(businessDto(1, "الف"), businessDto(2, "ب"))
        val vm = FollowingViewModel(repo, signedIn(), main.dispatcher)
        advanceUntilIdle()
        assertEquals(listOf(1L, 2L), vm.state.value.businesses.map { it.id })
        vm.unfollow(1)
        advanceUntilIdle()
        assertEquals(listOf(2L), vm.state.value.businesses.map { it.id })
        assertEquals(listOf(1L to false), repo.followCalls)
    }
}
