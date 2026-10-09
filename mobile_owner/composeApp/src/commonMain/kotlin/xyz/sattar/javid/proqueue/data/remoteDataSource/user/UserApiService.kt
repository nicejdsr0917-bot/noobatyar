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

class UserApiService(private val httpClient: HttpClient) {

    suspend fun checkVersion(body: CheckVersionRequestDto): ApiResponse<VersionInfo> {
        return httpClient.post("version/check/") {
            contentType(ContentType.Application.Json)
            setBody(body)
        }.toApiResponse()
    }

    suspend fun register(
        body: RegisterRequestDto
    ): ApiResponse<RegisterResponseDto> {
        return httpClient.post("auth/register/") {
            contentType(ContentType.Application.Json)
            setBody(body)
        }.toApiResponse()
    }
    suspend fun login(
        body: LoginRequestDto
    ): ApiResponse<RegisterResponseDto> {
        return httpClient.post("auth/login/") {
            contentType(ContentType.Application.Json)
            setBody(body)
        }.toApiResponse()
    }
    /**
     * Blacklists the refresh token on the server. It used to post no body,
     * while the server requires `refresh`, so every logout failed silently
     * and the session stayed usable for the token's full 14 days — on a
     * shared desktop, by whoever had copied it.
     */
    suspend fun logout(): ApiResponse<Unit> {
        return httpClient.post("auth/logout/") {
            contentType(ContentType.Application.Json)
            setBody(LogoutRequestDto(refresh = TokenManager.getRefreshToken().orEmpty()))
        }.toApiResponse()
    }

    suspend fun getUserProfile(id: Int): ApiResponse<UserDto> {
        return httpClient.get("users/$id/") {
            contentType(ContentType.Application.Json)
        }.toApiResponse()
    }

    suspend fun getMySubscription(): ApiResponse<SubscriptionDto> {
        return httpClient.get("accounting/my-subscription/") {
            contentType(ContentType.Application.Json)
        }.toApiResponse()
    }

    suspend fun getPlans(): ApiResponse<List<PlanDto>> {
        return httpClient.get("accounting/plans/") {
            contentType(ContentType.Application.Json)
        }.toApiResponse()
    }

    suspend fun getMyEntitlements(): ApiResponse<EntitlementsResponseDto> {
        return httpClient.get("accounting/my-entitlements/") {
            contentType(ContentType.Application.Json)
        }.toApiResponse()
    }

    suspend fun getAddons(): ApiResponse<List<AddOnPackDto>> {
        return httpClient.get("accounting/addons/") {
            contentType(ContentType.Application.Json)
        }.toApiResponse()
    }

    suspend fun buyAddon(packId: Int): ApiResponse<PaymentResponseDto> {
        return httpClient.post("accounting/addons/buy/") {
            contentType(ContentType.Application.Json)
            setBody(BuyAddonRequestDto(packId))
        }.toApiResponse()
    }

    suspend fun checkoutQuote(kind: String, id: Int, channel: String): ApiResponse<xyz.sattar.javid.proqueue.data.remoteDataSource.user.model.CheckoutQuoteDto> {
        return httpClient.get("accounting/checkout/quote/") {
            parameter("kind", kind)
            parameter("id", id)
            parameter("channel", channel)
        }.toApiResponse()
    }

    suspend fun storeIntent(store: String, body: xyz.sattar.javid.proqueue.data.remoteDataSource.user.model.StoreIntentRequestDto): ApiResponse<xyz.sattar.javid.proqueue.data.remoteDataSource.user.model.StoreIntentDto> {
        return httpClient.post("accounting/store/$store/intent/") {
            contentType(ContentType.Application.Json)
            setBody(body)
        }.toApiResponse()
    }

    suspend fun storeVerify(store: String, body: xyz.sattar.javid.proqueue.data.remoteDataSource.user.model.StoreVerifyRequestDto): ApiResponse<xyz.sattar.javid.proqueue.data.remoteDataSource.user.model.StoreVerifyDto> {
        return httpClient.post("accounting/store/$store/verify/") {
            contentType(ContentType.Application.Json)
            setBody(body)
        }.toApiResponse()
    }

    suspend fun createPayment(body: PaymentRequestDto): ApiResponse<PaymentResponseDto> {
        return httpClient.post("accounting/plans/payment/") {
            contentType(ContentType.Application.Json)
            setBody(body)
        }.toApiResponse()
    }

    suspend fun sendOTP(
        body: SendOTPRequestDto
    ): ApiResponse<SendOTPResponseDto> {
        return httpClient.post("auth/forgot-password/send/") {
            contentType(ContentType.Application.Json)
            setBody(body)
        }.toApiResponse()
    }
    suspend fun verifyOTP(
        body: VerifyOTPRequestDto
    ): ApiResponse<VerifyOTPResponseDto> {
        return httpClient.post("auth/forgot-password/verify/") {
            contentType(ContentType.Application.Json)
            setBody(body)
        }.toApiResponse()
    }

    suspend fun resetPassword(
        body: ResetPasswordRequestDto
    ): ApiResponse<Unit> {
        return httpClient.post("auth/forgot-password/reset/") {
            contentType(ContentType.Application.Json)
            setBody(body)
        }.toApiResponse()
    }

    suspend fun updateUserProfile(id: Int, body: xyz.sattar.javid.proqueue.data.remoteDataSource.user.model.request.UpdateUserProfileRequestDto): ApiResponse<UserDto> {
        return httpClient.patch("users/$id/") {
            contentType(ContentType.Application.Json)
            setBody(body)
        }.toApiResponse()
    }

    suspend fun getChangelog(): ApiResponse<List<xyz.sattar.javid.proqueue.data.remoteDataSource.user.model.ChangelogDto>> {
        return httpClient.get("version/changelog/") {
            contentType(ContentType.Application.Json)
        }.toApiResponse()
    }
}
