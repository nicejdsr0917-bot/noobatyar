package xyz.sattar.javid.proqueue.data.remoteDataSource.promo

import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import xyz.sattar.javid.proqueue.core.network.ApiResponse
import xyz.sattar.javid.proqueue.core.network.safeApiCall
import xyz.sattar.javid.proqueue.core.network.toApiResponse

/** Where a promo strip shows; the server's `ClientPromo.placement`. */
enum class PromoPlacement { EXPLORE, BUSINESS_MAP }

@Serializable
data class PromoDto(
    val id: Long,
    val text: String,
    @SerialName("action_label") val actionLabel: String = "",
    @SerialName("business_id") val businessId: Long? = null,
    @SerialName("link_url") val linkUrl: String = "",
)

@Serializable
data class PromoListDto(val promos: List<PromoDto> = emptyList())

/** The admin-managed promo strips of the client app. */
interface PromoApi {
    suspend fun promos(placement: PromoPlacement): ApiResponse<PromoListDto>
}

class KtorPromoApi(private val httpClient: HttpClient) : PromoApi {
    override suspend fun promos(placement: PromoPlacement): ApiResponse<PromoListDto> = safeApiCall {
        httpClient.get("client/promos/") { parameter("placement", placement.name) }.toApiResponse()
    }
}
