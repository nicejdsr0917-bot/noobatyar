package xyz.sattar.javid.proqueue.testsupport

import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.client.request.post
import io.ktor.client.request.patch
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType
import xyz.sattar.javid.proqueue.core.network.ApiResponse
import xyz.sattar.javid.proqueue.core.network.TokenManager
import xyz.sattar.javid.proqueue.core.network.toApiResponse
import xyz.sattar.javid.proqueue.data.remoteDataSource.user.model.request.CheckVersionRequestDto
import xyz.sattar.javid.proqueue.data.remoteDataSource.user.model.request.LoginRequestDto
import xyz.sattar.javid.proqueue.data.remoteDataSource.user.model.request.LogoutRequestDto
import xyz.sattar.javid.proqueue.data.remoteDataSource.user.model.request.RegisterRequestDto
import xyz.sattar.javid.proqueue.data.remoteDataSource.user.model.RegisterResponseDto
import xyz.sattar.javid.proqueue.data.remoteDataSource.user.model.SendOTPResponseDto
import xyz.sattar.javid.proqueue.data.remoteDataSource.user.model.UserDto
import xyz.sattar.javid.proqueue.data.remoteDataSource.user.model.VerifyOTPResponseDto
import xyz.sattar.javid.proqueue.data.remoteDataSource.user.model.SubscriptionDto
import xyz.sattar.javid.proqueue.data.remoteDataSource.user.model.PlanDto
import xyz.sattar.javid.proqueue.data.remoteDataSource.user.model.PaymentResponseDto
import xyz.sattar.javid.proqueue.data.remoteDataSource.user.model.EntitlementsResponseDto
import xyz.sattar.javid.proqueue.data.remoteDataSource.user.model.AddOnPackDto
import xyz.sattar.javid.proqueue.data.remoteDataSource.user.model.BuyAddonRequestDto
import xyz.sattar.javid.proqueue.data.remoteDataSource.user.model.request.PaymentRequestDto
import xyz.sattar.javid.proqueue.data.remoteDataSource.user.model.request.ResetPasswordRequestDto
import xyz.sattar.javid.proqueue.data.remoteDataSource.user.model.request.SendOTPRequestDto
import xyz.sattar.javid.proqueue.data.remoteDataSource.user.model.request.VerifyOTPRequestDto
import xyz.sattar.javid.proqueue.domain.model.VersionInfo
import xyz.sattar.javid.proqueue.data.remoteDataSource.user.UserApi

/** Every member throws; a test subclasses it and overrides what it exercises. */
open class StubUserApi : UserApi {
    override suspend fun checkVersion(body: CheckVersionRequestDto): ApiResponse<VersionInfo> = TODO("not stubbed")

    override suspend fun register(
        body: RegisterRequestDto
    ): ApiResponse<RegisterResponseDto> = TODO("not stubbed")

    override suspend fun login(
        body: LoginRequestDto
    ): ApiResponse<RegisterResponseDto> = TODO("not stubbed")

    override suspend fun logout(): ApiResponse<Unit> = TODO("not stubbed")

    override suspend fun getUserProfile(id: Int): ApiResponse<UserDto> = TODO("not stubbed")

    override suspend fun getMySubscription(): ApiResponse<SubscriptionDto> = TODO("not stubbed")

    override suspend fun getPlans(): ApiResponse<List<PlanDto>> = TODO("not stubbed")

    override suspend fun getMyEntitlements(): ApiResponse<EntitlementsResponseDto> = TODO("not stubbed")

    override suspend fun getAddons(): ApiResponse<List<AddOnPackDto>> = TODO("not stubbed")
    override suspend fun getWalletHistory(page: Int, pageSize: Int): ApiResponse<WalletHistoryDto> = TODO("not stubbed")

    override suspend fun buyAddon(packId: Int): ApiResponse<PaymentResponseDto> = TODO("not stubbed")

    override suspend fun checkoutQuote(kind: String, id: Int, channel: String): ApiResponse<xyz.sattar.javid.proqueue.data.remoteDataSource.user.model.CheckoutQuoteDto> = TODO("not stubbed")

    override suspend fun storeIntent(store: String, body: xyz.sattar.javid.proqueue.data.remoteDataSource.user.model.StoreIntentRequestDto): ApiResponse<xyz.sattar.javid.proqueue.data.remoteDataSource.user.model.StoreIntentDto> = TODO("not stubbed")

    override suspend fun storeVerify(store: String, body: xyz.sattar.javid.proqueue.data.remoteDataSource.user.model.StoreVerifyRequestDto): ApiResponse<xyz.sattar.javid.proqueue.data.remoteDataSource.user.model.StoreVerifyDto> = TODO("not stubbed")

    override suspend fun createPayment(body: PaymentRequestDto): ApiResponse<PaymentResponseDto> = TODO("not stubbed")

    override suspend fun sendOTP(
        body: SendOTPRequestDto
    ): ApiResponse<SendOTPResponseDto> = TODO("not stubbed")

    override suspend fun verifyOTP(
        body: VerifyOTPRequestDto
    ): ApiResponse<VerifyOTPResponseDto> = TODO("not stubbed")

    override suspend fun resetPassword(
        body: ResetPasswordRequestDto
    ): ApiResponse<Unit> = TODO("not stubbed")

    override suspend fun updateUserProfile(id: Int, body: xyz.sattar.javid.proqueue.data.remoteDataSource.user.model.request.UpdateUserProfileRequestDto): ApiResponse<UserDto> = TODO("not stubbed")

    override suspend fun getChangelog(): ApiResponse<List<xyz.sattar.javid.proqueue.data.remoteDataSource.user.model.ChangelogDto>> = TODO("not stubbed")
}
