package xyz.sattar.javid.proqueue.data.remoteDataSource.user

import io.ktor.client.HttpClient
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType
import xyz.sattar.javid.proqueue.core.network.ApiResponse
import xyz.sattar.javid.proqueue.core.network.toApiResponse
import xyz.sattar.javid.proqueue.data.remoteDataSource.user.model.request.CheckVersionRequestDto
import xyz.sattar.javid.proqueue.domain.model.VersionInfo

class UserApiService(private val httpClient: HttpClient) {

    suspend fun checkVersion(body: CheckVersionRequestDto): ApiResponse<VersionInfo> {
        return httpClient.post("version/check/") {
            contentType(ContentType.Application.Json)
            setBody(body)
        }.toApiResponse()
    }
}
