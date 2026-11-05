package xyz.sattar.javid.proqueue.feature.stories

import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import io.ktor.http.HttpHeaders
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
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
    /** Noobatyar's stories only: button text and where it goes (https://… or business:<id>). */
    @SerialName("link_label") val linkLabel: String = "",
    @SerialName("link_url") val linkUrl: String = "",
)

@Serializable
data class BusinessStoriesDto(
    @SerialName("business_id") val businessId: Long,
    val title: String = "",
    val stories: List<StoryDto> = emptyList(),
    /** Absent for a guest. */
    @SerialName("is_following") val isFollowing: Boolean? = null,
)

/** One avatar in the explore screen's story row; [businessId] 0 is Noobatyar itself. */
@Serializable
data class StoryRingDto(
    @SerialName("business_id") val businessId: Long,
    val title: String = "",
    val logo: String? = null,
    val platform: Boolean = false,
    val followed: Boolean = false,
)

@Serializable
data class StoryFeedDto(val rings: List<StoryRingDto> = emptyList())

/** [StoryRingDto.businessId] / [StoryCenter.open] id of Noobatyar's own stories. */
const val PLATFORM_STORY_ID = 0L

@Serializable
data class ActiveStoriesDto(@SerialName("business_ids") val businessIds: List<Long> = emptyList())

/** docs/api/P2_SOCIAL_API.md §10. */
class StoryApi(private val httpClient: HttpClient) {
    suspend fun active(): ApiResponse<ActiveStoriesDto> = safeApiCall {
        httpClient.get("client/stories/active/").toApiResponse()
    }

    suspend fun feed(cityCode: Int?, token: String?): ApiResponse<StoryFeedDto> = safeApiCall {
        httpClient.get("client/stories/feed/") {
            if (cityCode != null) parameter("city_code", cityCode)
            if (token != null) header(HttpHeaders.Authorization, "Visitor $token")
        }.toApiResponse()
    }

    suspend fun platform(): ApiResponse<BusinessStoriesDto> = safeApiCall {
        httpClient.get("client/stories/platform/").toApiResponse()
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

    private val _rings = MutableStateFlow<List<StoryRingDto>>(emptyList())
    /** The explore screen's story row, in the server's order (Noobatyar, followed, newest). */
    val rings: StateFlow<List<StoryRingDto>> = _rings.asStateFlow()

    private val _seen = MutableStateFlow<Set<Long>>(emptySet())
    /** Rings watched to the end this session: grey, and moved to the end of the row. */
    val seen: StateFlow<Set<Long>> = _seen.asStateFlow()

    /** Rings still to play after the current one, when it was opened from the row. */
    private var queue: List<Long> = emptyList()

    private val _openBusiness = MutableSharedFlow<Long>(extraBufferCapacity = 1)
    /** A Noobatyar story's "business:<id>" button: the nav host opens that page. */
    val openBusiness: SharedFlow<Long> = _openBusiness.asSharedFlow()

    private var feedKey: Pair<Int?, String?>? = null
    private var lastFeed = 0L

    /** At most one request a minute for the same city and sign-in. */
    fun refreshFeed(cityCode: Int?, nowMillis: Long) {
        val token = PreferencesManager.getVisitorToken()
        val key = cityCode to token
        if (key == feedKey && nowMillis - lastFeed < 60_000) return
        feedKey = key
        lastFeed = nowMillis
        scope.launch {
            val response = api.feed(cityCode, token)
            if (response is ApiResponse.Success) _rings.value = response.data.rings
        }
    }

    /** Cheap to call often: at most one request a minute. */
    fun refresh(nowMillis: Long) {
        if (nowMillis - lastRefresh < 60_000) return
        lastRefresh = nowMillis
        scope.launch {
            val response = api.active()
            if (response is ApiResponse.Success) _activeIds.value = response.data.businessIds.toSet()
        }
    }

    /** [then]: rings to play next, Instagram-style, when this one ends. */
    fun open(businessId: Long, then: List<Long> = emptyList()) {
        queue = then
        _viewing.value = businessId
    }

    fun close() {
        queue = emptyList()
        _viewing.value = null
    }

    /** The last story of [businessId] has played: on to the next ring, or close. */
    fun finished(businessId: Long) {
        markSeen(businessId)
        val next = queue.firstOrNull()
        if (next == null) close() else {
            queue = queue.drop(1)
            _viewing.value = next
        }
    }

    /** Seen or gone: drop the ring until the next refresh says otherwise. */
    fun markSeen(businessId: Long) {
        _activeIds.value = _activeIds.value - businessId
        _seen.value = _seen.value + businessId
    }

    fun openBusinessPage(businessId: Long) {
        close()
        _openBusiness.tryEmit(businessId)
    }

    suspend fun load(businessId: Long): ApiResponse<BusinessStoriesDto> =
        if (businessId == PLATFORM_STORY_ID) api.platform()
        else api.forBusiness(businessId, PreferencesManager.getVisitorToken())

    /** Null when the visitor is not signed in. */
    suspend fun follow(businessId: Long): Boolean? {
        val token = PreferencesManager.getVisitorToken() ?: return null
        return businessRepository.setFollowing(token, businessId, true) is ApiResponse.Success
    }
}
