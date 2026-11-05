package xyz.sattar.javid.proqueue.feature.stories

import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.http.HttpHeaders
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import xyz.sattar.javid.proqueue.core.network.ApiResponse
import xyz.sattar.javid.proqueue.core.network.safeApiCall
import xyz.sattar.javid.proqueue.core.network.toApiResponse
import xyz.sattar.javid.proqueue.core.prefs.PreferencesManager
import xyz.sattar.javid.proqueue.domain.BusinessRepository

@Serializable
data class StoryDto(
    val id: Long,
    val image: String = "",
    val caption: String = "",
    val button: String = "",
    @SerialName("discount_type") val discountType: String = "",
    @SerialName("discount_value") val discountValue: Long? = null,
)

@Serializable
data class BusinessStoriesDto(
    @SerialName("business_id") val businessId: Long,
    val title: String = "",
    val stories: List<StoryDto> = emptyList(),
    /** Absent for a guest. */
    @SerialName("is_following") val isFollowing: Boolean? = null,
)

@Serializable
data class ActiveStoriesDto(@SerialName("business_ids") val businessIds: List<Long> = emptyList())

/** docs/api/P2_SOCIAL_API.md §10. */
class StoryApi(private val httpClient: HttpClient) {
    suspend fun active(): ApiResponse<ActiveStoriesDto> = safeApiCall {
        httpClient.get("client/stories/active/").toApiResponse()
    }

    suspend fun forBusiness(businessId: Long, token: String?): ApiResponse<BusinessStoriesDto> = safeApiCall {
        httpClient.get("client/businesses/$businessId/stories/") {
            if (token != null) header(HttpHeaders.Authorization, "Visitor $token")
        }.toApiResponse()
    }
}

/**
 * App-wide story state: which businesses have a story up (the purple ring on
 * their avatar) and which one the viewer is showing. One instance (Koin single),
 * so every avatar on every screen agrees.
 */
class StoryCenter(
    private val api: StoryApi,
    private val businessRepository: BusinessRepository,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val _activeIds = MutableStateFlow<Set<Long>>(emptySet())
    val activeIds: StateFlow<Set<Long>> = _activeIds.asStateFlow()

    private val _viewing = MutableStateFlow<Long?>(null)
    /** Business whose stories are open in the viewer, or null. */
    val viewing: StateFlow<Long?> = _viewing.asStateFlow()

    private var lastRefresh = 0L

    /** Cheap to call often: at most one request a minute. */
    fun refresh(nowMillis: Long) {
        if (nowMillis - lastRefresh < 60_000) return
        lastRefresh = nowMillis
        scope.launch {
            val response = api.active()
            if (response is ApiResponse.Success) _activeIds.value = response.data.businessIds.toSet()
        }
    }

    fun open(businessId: Long) { _viewing.value = businessId }

    fun close() { _viewing.value = null }

    /** Seen or gone: drop the ring until the next refresh says otherwise. */
    fun markSeen(businessId: Long) { _activeIds.value = _activeIds.value - businessId }

    suspend fun load(businessId: Long): ApiResponse<BusinessStoriesDto> =
        api.forBusiness(businessId, PreferencesManager.getVisitorToken())

    /** Null when the visitor is not signed in. */
    suspend fun follow(businessId: Long): Boolean? {
        val token = PreferencesManager.getVisitorToken() ?: return null
        return businessRepository.setFollowing(token, businessId, true) is ApiResponse.Success
    }
}
