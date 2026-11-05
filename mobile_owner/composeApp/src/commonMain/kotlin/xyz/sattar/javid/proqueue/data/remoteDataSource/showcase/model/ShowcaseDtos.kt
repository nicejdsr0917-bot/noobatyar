package xyz.sattar.javid.proqueue.data.remoteDataSource.showcase.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** One slide of `GET /api/owner/banners/`. */
@Serializable
data class BannerDto(
    val id: Long,
    val image: String,
    val order: Int = 0
)

/** [linkUrl] is shared by every banner and may be blank. */
@Serializable
data class BannersDto(
    val banners: List<BannerDto> = emptyList(),
    @SerialName("link_url") val linkUrl: String = ""
)

/**
 * A portfolio photo or a certificate. The two endpoints share their shape and
 * differ only in which text fields they fill (caption vs title/issuer/year),
 * so one DTO with defaults reads both.
 */
@Serializable
data class ShowcaseItemDto(
    val id: Long,
    val image: String = "",
    val thumbnail: String? = null,
    val caption: String = "",
    val title: String = "",
    val issuer: String = "",
    val year: Int? = null,
    val order: Int = 0,
    val status: String = "PENDING",
    @SerialName("reject_reason") val rejectReason: String = ""
)

@Serializable
data class ReorderRequestDto(
    @SerialName("business_id") val businessId: Long,
    val ids: List<Long>
)

/** One row of `GET /api/reviews/owner/`; hidden rows are included for the owner. */
@Serializable
data class OwnerReviewDto(
    val id: Long,
    val rating: Int = 0,
    val text: String = "",
    @SerialName("created_at") val createdAt: String = "",
    @SerialName("visitor_name") val visitorName: String = "",
    @SerialName("visitor_phone") val visitorPhone: String = "",
    @SerialName("is_hidden") val isHidden: Boolean = false
)

/** `{"avg":4.5,"count":2,"histogram":{"1":0,...,"5":1}}` — hidden reviews excluded. */
@Serializable
data class ReviewSummaryDto(
    val avg: Double = 0.0,
    val count: Int = 0,
    val histogram: Map<String, Int> = emptyMap()
) {
    /** Reviews with exactly [stars] stars (0 when the key is absent). */
    fun countFor(stars: Int): Int = histogram[stars.toString()] ?: 0
}

/** One row of `GET /api/business/<id>/followers/`: first name and photo only. */
@Serializable
data class FollowerDto(
    val name: String = "",
    val avatar: String? = null,
    @SerialName("created_at") val createdAt: String = ""
)

/** One active story of `GET /api/owner/stories/` (docs/api/P2_SOCIAL_API.md §10). */
@Serializable
data class StoryDto(
    val id: Long,
    val image: String = "",
    val caption: String = "",
    val button: String = "",
    @SerialName("discount_type") val discountType: String = "",
    @SerialName("discount_value") val discountValue: Long? = null,
    @SerialName("created_at") val createdAt: String = "",
    @SerialName("expires_at") val expiresAt: String = ""
)

/** What the owner composes before posting. [discountType] is "percent" or "amount" (toman). */
data class StoryDraft(
    val image: ByteArray,
    val caption: String = "",
    val discountType: String? = null,
    val discountValue: Long? = null
)

@Serializable
data class FollowersPageDto(
    val count: Int = 0,
    val results: List<FollowerDto> = emptyList()
)

@Serializable
data class OwnerReviewsPageDto(
    val count: Int = 0,
    @SerialName("total_pages") val totalPages: Int? = null,
    @SerialName("current_page") val currentPage: Int? = null,
    val next: String? = null,
    val previous: String? = null,
    val results: List<OwnerReviewDto> = emptyList(),
    val summary: ReviewSummaryDto? = null
)
