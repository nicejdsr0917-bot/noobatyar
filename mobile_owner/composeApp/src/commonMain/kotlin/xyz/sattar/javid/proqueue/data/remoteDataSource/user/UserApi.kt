package xyz.sattar.javid.proqueue.data.remoteDataSource.user

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

/**
 * Contract the repositories depend on; [UserApiService] is the Ktor
 * implementation, tests substitute a fake.
 */
interface UserApi {
    suspend fun checkVersion(body: CheckVersionRequestDto): ApiResponse<VersionInfo>

    suspend fun register(
        body: RegisterRequestDto
    ): ApiResponse<RegisterResponseDto>

    suspend fun login(
        body: LoginRequestDto
    ): ApiResponse<RegisterResponseDto>

    suspend fun logout(): ApiResponse<Unit>

    suspend fun getUserProfile(id: Int): ApiResponse<UserDto>

    suspend fun getMySubscription(): ApiResponse<SubscriptionDto>

    suspend fun getPlans(): ApiResponse<List<PlanDto>>

    suspend fun getMyEntitlements(): ApiResponse<EntitlementsResponseDto>

    suspend fun getAddons(): ApiResponse<List<AddOnPackDto>>
    suspend fun getWalletHistory(page: Int, pageSize: Int): ApiResponse<xyz.sattar.javid.proqueue.data.remoteDataSource.user.model.WalletHistoryDto>

    suspend fun buyAddon(packId: Int): ApiResponse<PaymentResponseDto>

    suspend fun checkoutQuote(kind: String, id: Int, channel: String): ApiResponse<xyz.sattar.javid.proqueue.data.remoteDataSource.user.model.CheckoutQuoteDto>

    suspend fun storeIntent(store: String, body: xyz.sattar.javid.proqueue.data.remoteDataSource.user.model.StoreIntentRequestDto): ApiResponse<xyz.sattar.javid.proqueue.data.remoteDataSource.user.model.StoreIntentDto>

    suspend fun storeVerify(store: String, body: xyz.sattar.javid.proqueue.data.remoteDataSource.user.model.StoreVerifyRequestDto): ApiResponse<xyz.sattar.javid.proqueue.data.remoteDataSource.user.model.StoreVerifyDto>

    suspend fun createPayment(body: PaymentRequestDto): ApiResponse<PaymentResponseDto>

    suspend fun sendOTP(
        body: SendOTPRequestDto
    ): ApiResponse<SendOTPResponseDto>

    suspend fun verifyOTP(
        body: VerifyOTPRequestDto
    ): ApiResponse<VerifyOTPResponseDto>

    suspend fun resetPassword(
        body: ResetPasswordRequestDto
    ): ApiResponse<Unit>

    suspend fun updateUserProfile(id: Int, body: xyz.sattar.javid.proqueue.data.remoteDataSource.user.model.request.UpdateUserProfileRequestDto): ApiResponse<UserDto>

    suspend fun getChangelog(): ApiResponse<List<xyz.sattar.javid.proqueue.data.remoteDataSource.user.model.ChangelogDto>>
}
