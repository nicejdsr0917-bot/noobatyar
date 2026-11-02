package xyz.sattar.javid.proqueue.testsupport

import kotlinx.coroutines.flow.Flow
import xyz.sattar.javid.proqueue.core.network.ApiResponse
import xyz.sattar.javid.proqueue.data.remoteDataSource.user.model.*
import xyz.sattar.javid.proqueue.domain.UserRepository
import xyz.sattar.javid.proqueue.domain.model.VersionInfo

/** Every member throws; a test subclasses it and overrides what it exercises. */
open class StubUserRepository : UserRepository {
    override suspend fun checkVersion(versionCode: Int): ApiResponse<VersionInfo> = TODO("not stubbed")
    override suspend fun register(phone: String, password: String, name: String): ApiResponse<RegisterResponseDto> = TODO("not stubbed")
    override suspend fun login(phone: String, password: String): ApiResponse<RegisterResponseDto> = TODO("not stubbed")
    override suspend fun logout(): ApiResponse<Unit> = TODO("not stubbed")
    override suspend fun getUserProfile(id: Int): ApiResponse<UserDto> = TODO("not stubbed")
    override suspend fun getMySubscription(): ApiResponse<SubscriptionDto> = TODO("not stubbed")
    override suspend fun getPlans(): ApiResponse<List<PlanDto>> = TODO("not stubbed")
    override suspend fun createPayment(planId: String, useWallet: Boolean, walletOnly: Boolean): ApiResponse<PaymentResponseDto> = TODO("not stubbed")
    override suspend fun getMyEntitlements(): ApiResponse<EntitlementsResponseDto> = TODO("not stubbed")
    override suspend fun getAddons(): ApiResponse<List<AddOnPackDto>> = TODO("not stubbed")
    override suspend fun getWalletHistory(page: Int, pageSize: Int): ApiResponse<WalletHistoryDto> = TODO("not stubbed")
    override suspend fun buyAddon(packId: Int, useWallet: Boolean, walletOnly: Boolean): ApiResponse<PaymentResponseDto> = TODO("not stubbed")
    override suspend fun convertWallet(kind: String, count: Int): ApiResponse<WalletConvertDto> = TODO("not stubbed")
    override suspend fun checkoutQuote(kind: String, id: Int, channel: String, useWallet: Boolean): ApiResponse<CheckoutQuoteDto> = TODO("not stubbed")
    override suspend fun storeIntent(store: String, kind: String, id: Int): ApiResponse<StoreIntentDto> = TODO("not stubbed")
    override suspend fun storeVerify(store: String, body: StoreVerifyRequestDto): ApiResponse<StoreVerifyDto> = TODO("not stubbed")
    override fun getLocalUser(id: Int): Flow<UserDto?> = TODO("not stubbed")
    override fun getCurrentUser(): Flow<UserDto?> = TODO("not stubbed")
    override fun getLocalSubscription(): Flow<SubscriptionDto?> = TODO("not stubbed")
    override suspend fun syncUserProfile(id: Int): ApiResponse<UserDto> = TODO("not stubbed")
    override suspend fun syncSubscription(): ApiResponse<SubscriptionDto> = TODO("not stubbed")
    override suspend fun sendOTP(phone: String): ApiResponse<SendOTPResponseDto> = TODO("not stubbed")
    override suspend fun verifyOTP(phone: String, code: String): ApiResponse<VerifyOTPResponseDto> = TODO("not stubbed")
    override suspend fun resetPassword(phone: String, resetToken: String, newPassword: String): ApiResponse<Unit> = TODO("not stubbed")
}
