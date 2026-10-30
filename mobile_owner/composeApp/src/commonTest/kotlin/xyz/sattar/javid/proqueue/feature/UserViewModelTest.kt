package xyz.sattar.javid.proqueue.feature

import app.cash.turbine.test
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import xyz.sattar.javid.proqueue.core.network.ApiResponse
import xyz.sattar.javid.proqueue.data.remoteDataSource.device.DeviceApi
import xyz.sattar.javid.proqueue.data.remoteDataSource.user.model.RegisterResponseDto
import xyz.sattar.javid.proqueue.data.remoteDataSource.user.model.TokensDto
import xyz.sattar.javid.proqueue.data.remoteDataSource.user.model.UserDto
import xyz.sattar.javid.proqueue.domain.usecase.UserLogoutUseCase
import xyz.sattar.javid.proqueue.domain.usecase.push.UnregisterPushTokenUseCase
import xyz.sattar.javid.proqueue.domain.usecase.user.ClearTokenUseCase
import xyz.sattar.javid.proqueue.domain.usecase.user.GetCurrentUserUseCase
import xyz.sattar.javid.proqueue.domain.usecase.user.GetMySubscriptionUseCase
import xyz.sattar.javid.proqueue.domain.usecase.user.HasTokenUseCase
import xyz.sattar.javid.proqueue.domain.usecase.user.LoginUseCase
import xyz.sattar.javid.proqueue.domain.usecase.user.SyncUserProfileUseCase
import xyz.sattar.javid.proqueue.feature.profile.UserEvent
import xyz.sattar.javid.proqueue.feature.profile.UserIntent
import xyz.sattar.javid.proqueue.feature.profile.UserViewModel
import xyz.sattar.javid.proqueue.testsupport.FakeAppPreferences
import xyz.sattar.javid.proqueue.testsupport.FakeBusinessStore
import xyz.sattar.javid.proqueue.testsupport.FakeTokenStore
import xyz.sattar.javid.proqueue.testsupport.MainDispatcherSetup
import xyz.sattar.javid.proqueue.testsupport.StubUserRepository
import xyz.sattar.javid.proqueue.testsupport.testBusiness
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class UserViewModelTest {
    private val main = MainDispatcherSetup()

    @BeforeTest fun setUp() = main.setUp()
    @AfterTest fun tearDown() = main.tearDown()

    private val dto = UserDto(5, "09120000000", "Sara", "BUSINESS_OWNER", false, "2026-01-01")
    private val tokens = FakeTokenStore("access-1", "refresh-1")
    private val prefs = FakeAppPreferences()
    private val log = mutableListOf<String>()
    private var logoutResult: () -> ApiResponse<Unit> = { ApiResponse.Success(Unit) }
    private var loginResult: ApiResponse<RegisterResponseDto> =
        ApiResponse.Success(RegisterResponseDto(dto, TokensDto("access-2", "refresh-2")))

    private val currentUser = MutableStateFlow<UserDto?>(dto)

    private val users = object : StubUserRepository() {
        override fun getCurrentUser() = currentUser
        override suspend fun syncUserProfile(id: Int) = ApiResponse.Success(dto)
        override suspend fun getMySubscription() = ApiResponse.Error("none", 404)
        override suspend fun logout(): ApiResponse<Unit> {
            log += "logout(tokenStillPresent=${tokens.access != null})"
            return logoutResult()
        }
        override suspend fun login(phone: String, password: String) = loginResult
    }

    private val device = object : DeviceApi {
        override suspend fun register(body: xyz.sattar.javid.proqueue.data.remoteDataSource.device.RegisterDeviceRequestDto) = ApiResponse.Success(Unit)
        override suspend fun unregister(body: xyz.sattar.javid.proqueue.data.remoteDataSource.device.UnregisterDeviceRequestDto) = ApiResponse.Success(Unit)
    }

    private fun viewModel(store: FakeBusinessStore) = UserViewModel(
        getCurrentUserUseCase = GetCurrentUserUseCase(users),
        getMySubscriptionUseCase = GetMySubscriptionUseCase(users),
        userLogoutUseCase = UserLogoutUseCase(users),
        unregisterPushTokenUseCase = UnregisterPushTokenUseCase(device),
        clearTokenUseCase = ClearTokenUseCase(tokens),
        syncUserProfileUseCase = SyncUserProfileUseCase(users, tokens),
        businessStore = store,
        preferences = prefs,
    )

    @Test
    fun showsTheCurrentUserFromTheLocalStore() = runTest {
        val vm = viewModel(FakeBusinessStore())
        advanceUntilIdle()

        assertEquals("Sara", vm.uiState.value.userName)
        assertEquals("09120000000", vm.uiState.value.userNumber)
        assertFalse(vm.uiState.value.isLoading)
    }

    @Test
    fun logoutRevokesOnServerThenDropsTokensBusinessAndDefault() = runTest {
        val store = FakeBusinessStore(testBusiness(3))
        prefs.setDefaultBusinessId(3)
        val vm = viewModel(store)
        advanceUntilIdle()

        vm.events.test {
            vm.sendIntent(UserIntent.Logout); advanceUntilIdle()
            assertEquals(UserEvent.LogoutSuccess, awaitItem())
            cancelAndIgnoreRemainingEvents()
        }

        assertEquals(listOf("logout(tokenStillPresent=true)"), log, "server sees the session before tokens go")
        assertNull(tokens.access)
        assertNull(tokens.refresh)
        assertEquals(1, tokens.clearCalls)
        assertNull(store.selectedBusiness.value)
        assertNull(prefs.defaultBusiness.value)
    }

    @Test
    fun logoutStillCleansUpWhenTheServerCallThrows() = runTest {
        logoutResult = { throw IllegalStateException("offline") }
        val store = FakeBusinessStore(testBusiness(3))
        val vm = viewModel(store)
        advanceUntilIdle()

        vm.events.test {
            vm.sendIntent(UserIntent.Logout); advanceUntilIdle()
            assertEquals(UserEvent.LogoutSuccess, awaitItem())
            cancelAndIgnoreRemainingEvents()
        }

        assertNull(tokens.access)
        assertNull(store.selectedBusiness.value)
        assertFalse(vm.uiState.value.isLoading)
    }

    @Test
    fun loginStoresTheTokensSoTheSessionExists() = runTest {
        val fresh = FakeTokenStore()
        val login = LoginUseCase(users, fresh)
        val has = HasTokenUseCase(fresh)
        assertFalse(has())

        val result = login("0912", "pw")

        assertTrue(result is ApiResponse.Success)
        assertEquals("access-2", fresh.access)
        assertEquals("refresh-2", fresh.refresh)
        assertTrue(has())
    }

    @Test
    fun aFailedLoginStoresNothing() = runTest {
        loginResult = ApiResponse.Error("bad credentials", 401)
        val fresh = FakeTokenStore()

        val result = LoginUseCase(users, fresh)("0912", "wrong")

        assertTrue(result is ApiResponse.Error)
        assertNull(fresh.access)
        assertFalse(HasTokenUseCase(fresh)())
    }
}
