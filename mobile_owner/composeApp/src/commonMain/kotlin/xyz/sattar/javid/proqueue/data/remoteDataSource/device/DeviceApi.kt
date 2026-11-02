package xyz.sattar.javid.proqueue.data.remoteDataSource.device

import io.ktor.client.HttpClient
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import xyz.sattar.javid.proqueue.core.network.ApiResponse
import xyz.sattar.javid.proqueue.core.network.toApiResponse

/**
 * Contract the repositories depend on; [DeviceApiService] is the Ktor
 * implementation, tests substitute a fake.
 */
interface DeviceApi {
    suspend fun register(body: RegisterDeviceRequestDto): ApiResponse<Unit>

    suspend fun unregister(body: UnregisterDeviceRequestDto): ApiResponse<Unit>
}
