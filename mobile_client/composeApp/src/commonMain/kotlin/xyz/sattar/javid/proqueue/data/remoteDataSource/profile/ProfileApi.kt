package xyz.sattar.javid.proqueue.data.remoteDataSource.profile

import io.ktor.client.HttpClient
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.delete
import io.ktor.client.request.forms.formData
import io.ktor.client.request.forms.submitFormWithBinaryData
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.patch
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.Headers
import io.ktor.http.HttpHeaders
import io.ktor.http.contentType
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import xyz.sattar.javid.proqueue.core.network.ApiResponse
import xyz.sattar.javid.proqueue.core.network.safeApiCall
import xyz.sattar.javid.proqueue.core.network.toApiResponse

/** The signed-in visitor's own profile (`client/auth/me/`). */
@Serializable
data class MeDto(
    val id: Long = 0,
    @SerialName("full_name") val fullName: String = "",
    @SerialName("phone_number") val phoneNumber: String = "",
    /** Absolute URL of the 512 px square photo, or null. */
    val avatar: String? = null,
    /** Up to [BIO_MAX_LENGTH] characters; only ever shown to the visitor themself. */
    val bio: String = "",
)

/** Same limit as the server (visitor.serializers.BIO_MAX_LENGTH). */
const val BIO_MAX_LENGTH = 150

@Serializable
private data class UpdateMeDto(
    @SerialName("full_name") val fullName: String,
    @SerialName("bio") val bio: String,
)

/** Read and edit the visitor's own name and photo. Every call needs the visitor token. */
interface ProfileApi {
    suspend fun me(token: String): ApiResponse<MeDto>
    /** Saves the name and the bio together. */
    suspend fun update(token: String, fullName: String, bio: String): ApiResponse<MeDto>
    /** A JPEG; the server centre-crops it square and scales it to 512 px. */
    suspend fun uploadAvatar(token: String, jpeg: ByteArray): ApiResponse<MeDto>
    suspend fun deleteAvatar(token: String): ApiResponse<MeDto>
}

class KtorProfileApi(private val httpClient: HttpClient) : ProfileApi {

    private fun HttpRequestBuilder.visitor(token: String) = header(HttpHeaders.Authorization, "Visitor $token")

    override suspend fun me(token: String): ApiResponse<MeDto> = safeApiCall {
        httpClient.get("client/auth/me/") { visitor(token) }.toApiResponse()
    }

    override suspend fun update(token: String, fullName: String, bio: String): ApiResponse<MeDto> = safeApiCall {
        httpClient.patch("client/auth/me/") {
            visitor(token)
            contentType(ContentType.Application.Json)
            setBody(UpdateMeDto(fullName, bio))
        }.toApiResponse()
    }

    override suspend fun uploadAvatar(token: String, jpeg: ByteArray): ApiResponse<MeDto> = safeApiCall {
        httpClient.submitFormWithBinaryData(
            url = "client/auth/me/avatar/",
            formData = formData {
                append("avatar", jpeg, Headers.build {
                    append(HttpHeaders.ContentType, "image/jpeg")
                    append(HttpHeaders.ContentDisposition, "filename=\"avatar.jpg\"")
                })
            },
        ) { visitor(token) }.toApiResponse()
    }

    override suspend fun deleteAvatar(token: String): ApiResponse<MeDto> = safeApiCall {
        httpClient.delete("client/auth/me/avatar/") { visitor(token) }.toApiResponse()
    }
}
