package xyz.sattar.javid.proqueue.feature.profile

import xyz.sattar.javid.proqueue.core.state.SelectedBusinessStore
import xyz.sattar.javid.proqueue.core.prefs.AppPreferences

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import xyz.sattar.javid.proqueue.core.network.ApiResponse
import xyz.sattar.javid.proqueue.core.ui.BaseViewModel
import xyz.sattar.javid.proqueue.domain.usecase.UserLogoutUseCase
import xyz.sattar.javid.proqueue.domain.usecase.push.UnregisterPushTokenUseCase
import xyz.sattar.javid.proqueue.domain.usecase.user.ClearTokenUseCase
import xyz.sattar.javid.proqueue.domain.usecase.user.GetCurrentUserUseCase
import xyz.sattar.javid.proqueue.domain.usecase.user.GetMyEntitlementsUseCase
import xyz.sattar.javid.proqueue.domain.usecase.user.GetMySubscriptionUseCase
import xyz.sattar.javid.proqueue.domain.usecase.user.SyncUserProfileUseCase

class UserViewModel(
    private val getCurrentUserUseCase: GetCurrentUserUseCase,
    private val getMySubscriptionUseCase: GetMySubscriptionUseCase,
    private val userLogoutUseCase: UserLogoutUseCase,
    private val unregisterPushTokenUseCase: UnregisterPushTokenUseCase,
    private val clearTokenUseCase: ClearTokenUseCase,
    private val syncUserProfileUseCase: SyncUserProfileUseCase,
    private val businessStore: SelectedBusinessStore,
    private val preferences: AppPreferences,
    private val getMyEntitlementsUseCase: GetMyEntitlementsUseCase? = null,
) : BaseViewModel<UserState, UserState.PartialState, UserEvent, UserIntent>(
    initialState = UserState()
) {
    init {
        sendIntent(UserIntent.ObserveUser)
        sendIntent(UserIntent.LoadProfile)
    }

    override fun handleIntent(intent: UserIntent): Flow<UserState.PartialState> {
        return when (intent) {
            UserIntent.ObserveUser -> observeCurrentUser()
            UserIntent.LoadProfile -> loadSubscription()
            UserIntent.Logout -> logout()
        }
    }

    private fun observeCurrentUser(): Flow<UserState.PartialState> {
        return getCurrentUserUseCase().map { user ->
            if (user != null) {
                UserState.PartialState.UserProfile(user.name, user.phone)
            } else {
                UserState.PartialState.IsLoading(false)
            }
        }
    }

    override fun reduceState(currentState: UserState, partialState: UserState.PartialState): UserState {
        return when (partialState) {
            is UserState.PartialState.IsLoading -> currentState.copy(isLoading = partialState.isLoading)
            is UserState.PartialState.UserProfile -> currentState.copy(
                userName = partialState.name,
                userNumber = partialState.phone,
                isLoading = false
            )
            is UserState.PartialState.Subscription -> currentState.copy(
                subscription = partialState.subscription,
                isLoading = false
            )
            is UserState.PartialState.TomanWallet -> currentState.copy(tomanWallet = partialState.balance)
            is UserState.PartialState.Error -> currentState.copy(error = partialState.message, isLoading = false)
        }
    }

    override fun createErrorState(message: String): UserState.PartialState = UserState.PartialState.Error(message)

    private fun loadSubscription(): Flow<UserState.PartialState> = flow {
        emit(UserState.PartialState.IsLoading(true))
        // Name/phone come from the local store (observeCurrentUser). Nothing
        // ever refreshed it from the server, which on the web panel — where
        // the store doesn't survive a reload — left the card on "کاربر
        // نوبت‌یار" for good. Failure here just keeps whatever is stored.
        try {
            syncUserProfileUseCase()
        } catch (e: Exception) {
        }
        try {
            when (val subResponse = getMySubscriptionUseCase()) {
                is ApiResponse.Success -> {
                    emit(UserState.PartialState.Subscription(subResponse.data))
                }
                is ApiResponse.Error -> {
                    // Fail silently
                }
            }
        } catch (e: Exception) {
            // Error handling
        }
        try {
            val response = getMyEntitlementsUseCase?.invoke()
            if (response is ApiResponse.Success) {
                emit(UserState.PartialState.TomanWallet(response.data.usage.tomanWallet))
            }
        } catch (e: Exception) {
            // The sheet just leaves the wallet row out.
        } finally {
            emit(UserState.PartialState.IsLoading(false))
        }
    }

    private fun logout(): Flow<UserState.PartialState> = flow {
        emit(UserState.PartialState.IsLoading(true))
        try {
            // Before the token is cleared, and before the account changes: the
            // next owner to sign in on this phone must not inherit push
            // notifications about the previous one's appointments.
            unregisterPushTokenUseCase()
            userLogoutUseCase()
        } catch (e: Exception) {
        } finally {
            clearTokenUseCase()
            businessStore.clearBusiness()
            preferences.setDefaultBusinessId(null)
            emit(UserState.PartialState.IsLoading(false))
            sendEvent(UserEvent.LogoutSuccess)
        }
    }
}
