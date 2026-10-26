package xyz.sattar.javid.proqueue.data.remoteDataSource.business.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** An imported directory listing an owner can take over (backend business/claim_views.py). */
@Serializable
data class ClaimableBusinessDto(
    @SerialName("id") val id: Long,
    @SerialName("title") val title: String,
    @SerialName("category_display") val categoryDisplay: String? = null,
    @SerialName("address") val address: String? = null,
    @SerialName("city") val city: String? = null,
    @SerialName("neighborhood") val neighborhood: String? = null,
    @SerialName("phone") val phone: String? = null,
)

@Serializable
data class ClaimableSearchDto(
    @SerialName("count") val count: Int = 0,
    @SerialName("next") val next: Int? = null,
    @SerialName("results") val results: List<ClaimableBusinessDto> = emptyList(),
)

@Serializable
data class ClaimStartRequestDto(@SerialName("note") val note: String = "")

@Serializable
data class ClaimStartResponseDto(
    @SerialName("claim_id") val claimId: Long,
    /** "OTP" — a code went to the listing's mobile; "MANUAL" — waits for support. */
    @SerialName("method") val method: String,
    @SerialName("masked_phone") val maskedPhone: String = "",
)

@Serializable
data class ClaimVerifyRequestDto(@SerialName("code") val code: String)

@Serializable
data class ClaimVerifyResponseDto(@SerialName("business_id") val businessId: Long)
