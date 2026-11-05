package xyz.sattar.javid.proqueue.data.remoteDataSource.social.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

// Shapes of the public showcase (`client/businesses/<id>/showcase/`) and the
// reviews surface (`client/reviews/...`). See docs/api/P2_SHOWCASE_API.md and
// P2_SOCIAL_API.md.

@Serializable
data class PortfolioItemDto(
    val id: Long,
    val image: String = "",
    val thumbnail: String? = null,
    val caption: String? = null,
    val order: Int = 0,
)

@Serializable
data class CertificateDto(
    val id: Long,
    val image: String = "",
    val thumbnail: String? = null,
    val title: String = "",
    val issuer: String? = null,
    /** Jalali year, e.g. 1402. */
    val year: Int? = null,
    val order: Int = 0,
)

@Serializable
data class ShowcaseDto(
    val portfolio: List<PortfolioItemDto> = emptyList(),
    val certificates: List<CertificateDto> = emptyList(),
)

@Serializable
data class ReviewSummaryDto(
    val avg: Double = 0.0,
    val count: Int = 0,
    /** Keys "1".."5". */
    val histogram: Map<String, Int> = emptyMap(),
)

/** A public review row: first name only, phone masked by the server. */
@Serializable
data class ReviewDto(
    val id: Long,
    val rating: Int,
    val text: String? = null,
    @SerialName("created_at") val createdAt: String = "",
    @SerialName("visitor_name") val visitorName: String? = null,
    /** The reviewer's profile photo (absolute URL), or null. Never any contact detail. */
    @SerialName("visitor_avatar") val visitorAvatar: String? = null,
)

@Serializable
data class MyReviewDto(
    val id: Long,
    @SerialName("business_id") val businessId: Long = 0,
    val rating: Int,
    val text: String? = null,
    @SerialName("created_at") val createdAt: String = "",
    @SerialName("updated_at") val updatedAt: String = "",
)

// No defaults on purpose: the client Json does not encode default values, and
// both fields must always reach the server.
@Serializable
data class ReviewRequestDto(val rating: Int, val text: String)

@Serializable
data class ReviewReportRequestDto(val reason: String, val detail: String)
