package xyz.sattar.javid.proqueue.data.repository.user

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.map
import xyz.sattar.javid.proqueue.core.network.ApiResponse
import xyz.sattar.javid.proqueue.core.network.TokenStore
import xyz.sattar.javid.proqueue.data.localDataSource.LocalCacheCleaner
import xyz.sattar.javid.proqueue.data.localDataSource.user.UserLocalSource
import xyz.sattar.javid.proqueue.data.remoteDataSource.user.mapper.toDomain
import xyz.sattar.javid.proqueue.data.remoteDataSource.user.model.request.CheckVersionRequestDto
import xyz.sattar.javid.proqueue.data.remoteDataSource.user.model.request.RegisterRequestDto
import xyz.sattar.javid.proqueue.data.remoteDataSource.user.model.RegisterResponseDto
import xyz.sattar.javid.proqueue.data.remoteDataSource.user.UserApi
import xyz.sattar.javid.proqueue.data.remoteDataSource.user.model.request.LoginRequestDto
import xyz.sattar.javid.proqueue.data.remoteDataSource.user.model.SendOTPResponseDto
import xyz.sattar.javid.proqueue.data.remoteDataSource.user.model.SubscriptionDto
import xyz.sattar.javid.proqueue.data.remoteDataSource.user.model.PlanDto
import xyz.sattar.javid.proqueue.data.remoteDataSource.user.model.UserDto
import xyz.sattar.javid.proqueue.data.remoteDataSource.user.model.VerifyOTPResponseDto
import xyz.sattar.javid.proqueue.data.remoteDataSource.user.model.PaymentResponseDto
import xyz.sattar.javid.proqueue.data.remoteDataSource.user.model.request.PaymentRequestDto
import xyz.sattar.javid.proqueue.data.remoteDataSource.user.model.request.ResetPasswordRequestDto
import xyz.sattar.javid.proqueue.data.remoteDataSource.user.model.request.SendOTPRequestDto
import xyz.sattar.javid.proqueue.data.remoteDataSource.user.model.request.VerifyOTPRequestDto
import xyz.sattar.javid.proqueue.domain.UserRepository
import xyz.sattar.javid.proqueue.domain.model.VersionInfo

class UserRepositoryImpl(
    private val userApiService: UserApi,
    private val userDao: UserLocalSource,
    private val cacheCleaner: LocalCacheCleaner,
    private val tokens: TokenStore
) : UserRepository {

    override suspend fun checkVersion(versionCode: Int): ApiResponse<VersionInfo> {
        return userApiService.checkVersion(CheckVersionRequestDto(versionCode = versionCode))
    }

    override suspend fun register(
        phone: String,
        password: String,
        name: String
    ): ApiResponse<RegisterResponseDto> {
        val response = userApiService.register(
            RegisterRequestDto(
                phone = phone,
                password = password,
                name = name
            )
        )
        if (response is ApiResponse.Success) {
            saveUserToDb(response.data.user)
        }
        return response
    }

    override suspend fun login(
        phone: String,
        password: String
    ): ApiResponse<RegisterResponseDto> {
        val response = userApiService.login(
            LoginRequestDto(
                phone = phone,
                password = password,
            )
        )
        if (response is ApiResponse.Success) {
            saveUserToDb(response.data.user)
        }
        return response
    }

    override suspend fun logout(): ApiResponse<Unit> {
        // Server first (it needs the refresh token), then drop the tokens, then
        // wipe: wiping first let in-flight syncs re-insert the old account's
        // rows, and a failing wipe step used to skip the revocation entirely.
        // Wiped in `finally` (non-cancellable) so it happens whatever the
        // network did.
        try {
            return try {
                userApiService.logout()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                ApiResponse.Error(e.message ?: "logout failed", 0)
            }
        } finally {
            withContext(NonCancellable) {
                try {
                    tokens.clearTokens()
                } finally {
                    cacheCleaner.clearAll()
                }
            }
        }
    }

    override suspend fun getUserProfile(id: Int): ApiResponse<UserDto> {
        return userApiService.getUserProfile(id)
    }

    override suspend fun getMySubscription(): ApiResponse<SubscriptionDto> {
        return userApiService.getMySubscription()
    }

    override suspend fun getPlans(): ApiResponse<List<PlanDto>> {
        return userApiService.getPlans()
    }

    override suspend fun createPayment(planId: String, useWallet: Boolean, walletOnly: Boolean): ApiResponse<PaymentResponseDto> {
        return userApiService.createPayment(PaymentRequestDto(planId, useWallet, walletOnly))
    }

    override suspend fun getMyEntitlements(): ApiResponse<xyz.sattar.javid.proqueue.data.remoteDataSource.user.model.EntitlementsResponseDto> {
        return userApiService.getMyEntitlements()
    }

    override suspend fun getAddons(): ApiResponse<List<xyz.sattar.javid.proqueue.data.remoteDataSource.user.model.AddOnPackDto>> {
        return userApiService.getAddons()
    }

    override suspend fun getWalletHistory(page: Int, pageSize: Int): ApiResponse<xyz.sattar.javid.proqueue.data.remoteDataSource.user.model.WalletHistoryDto> {
        return userApiService.getWalletHistory(page, pageSize)
    }

    override suspend fun convertWallet(kind: String, count: Int) =
        userApiService.convertWallet(xyz.sattar.javid.proqueue.data.remoteDataSource.user.model.WalletConvertRequestDto(kind, count))

    override suspend fun buyAddon(packId: Int, useWallet: Boolean, walletOnly: Boolean): ApiResponse<PaymentResponseDto> {
        return userApiService.buyAddon(packId, useWallet, walletOnly)
    }

    override suspend fun checkoutQuote(kind: String, id: Int, channel: String, useWallet: Boolean) =
        userApiService.checkoutQuote(kind, id, channel, useWallet)

    override suspend fun storeIntent(store: String, kind: String, id: Int) =
        userApiService.storeIntent(store, xyz.sattar.javid.proqueue.data.remoteDataSource.user.model.StoreIntentRequestDto(kind, id))

    override suspend fun storeVerify(store: String, body: xyz.sattar.javid.proqueue.data.remoteDataSource.user.model.StoreVerifyRequestDto) =
        userApiService.storeVerify(store, body)

    override fun getLocalUser(id: Int): Flow<UserDto?> {
        return userDao.getUserById(id).map { user ->
            user?.let {
                UserDto(
                    id = it.id,
                    phone = it.phone,
                    name = it.name,
                    userType = it.userType.value,
                    isEmployee = it.isEmployee,
                    joinedAt = it.joinedAt
                )
            }
        }
    }

    override fun getCurrentUser(): Flow<UserDto?> {
        return userDao.getCurrentUser().map { user ->
            user?.let {
                UserDto(
                    id = it.id,
                    phone = it.phone,
                    name = it.name,
                    userType = it.userType.value,
                    isEmployee = it.isEmployee,
                    joinedAt = it.joinedAt
                )
            }
        }
    }

    override fun getLocalSubscription(): Flow<SubscriptionDto?> {
        return userDao.getActiveSubscription().map { subscription ->
            subscription?.let {
                SubscriptionDto(
                    id = it.id,
                    plan = it.planName?.let { name ->
                        PlanDto(id = 0, name = name, price = 0, priceDisplay = "", durationDisplay = "", isVip = true)
                    },
                    startedAt = it.startedAt,
                    endsAt = it.endsAt,
                    isValid = it.isValid
                )
            }
        }
    }

    override suspend fun syncUserProfile(id: Int): ApiResponse<UserDto> {
        val response = userApiService.getUserProfile(id)
        if (response is ApiResponse.Success) {
            saveUserToDb(response.data)
        }
        return response
    }

    override suspend fun syncSubscription(): ApiResponse<SubscriptionDto> {
        val response = userApiService.getMySubscription()
        if (response is ApiResponse.Success) {
            userDao.insertSubscription(response.data.toDomain())
        }
        return response
    }

    private suspend fun saveUserToDb(user: UserDto) {
        userDao.insertUser(user.toDomain())
    }

    override suspend fun sendOTP(phone: String): ApiResponse<SendOTPResponseDto> {
        return userApiService.sendOTP(SendOTPRequestDto(phone = phone))
    }

    override suspend fun verifyOTP(
        phone: String,
        code: String
    ): ApiResponse<VerifyOTPResponseDto> {
        return userApiService.verifyOTP(VerifyOTPRequestDto(phone = phone, code = code))
    }

    override suspend fun resetPassword(
        phone: String,
        resetToken: String,
        newPassword: String
    ): ApiResponse<Unit> {
        return userApiService.resetPassword(
            ResetPasswordRequestDto(
                phone = phone,
                resetToken = resetToken,
                newPassword = newPassword,
            )
        )
    }
}
