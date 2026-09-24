package xyz.sattar.javid.proqueue.data.remoteDataSource.business.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Owner promo-SMS campaigns — backend/business/promo_views.py.
 * Plain JSON (read with toDirectApiResponse), same as the SMS report.
 */

/** GET business/{id}/promo-sms/ — everything the campaign screen opens with. */
@Serializable
data class PromoSmsOverviewDto(
    @SerialName("entitled") val entitled: Boolean = false,
    @SerialName("within_send_window") val withinSendWindow: Boolean = true,
    @SerialName("send_window_start") val sendWindowStart: Int = 8,
    @SerialName("send_window_end") val sendWindowEnd: Int = 22,
    @SerialName("max_message_chars") val maxMessageChars: Int = 250,
    /** SMS parts the owner can still spend; null = unlimited. */
    @SerialName("available_credit") val availableCredit: Int? = null,
    @SerialName("audiences") val audiences: List<PromoAudienceDto> = emptyList(),
    @SerialName("ready_texts") val readyTexts: List<String> = emptyList(),
    @SerialName("campaigns") val campaigns: List<PromoCampaignDto> = emptyList()
)

@Serializable
data class PromoAudienceDto(
    @SerialName("key") val key: String,
    @SerialName("label") val label: String = "",
    @SerialName("count") val count: Int = 0
)

@Serializable
data class PromoCampaignDto(
    @SerialName("id") val id: Long,
    @SerialName("audience") val audience: String = "",
    @SerialName("audience_label") val audienceLabel: String = "",
    @SerialName("message") val message: String = "",
    /** SENDING | DONE */
    @SerialName("status") val status: String = "",
    @SerialName("recipient_count") val recipientCount: Int = 0,
    @SerialName("total_parts") val totalParts: Int = 0,
    @SerialName("sent_count") val sentCount: Int = 0,
    @SerialName("failed_count") val failedCount: Int = 0,
    @SerialName("skipped_count") val skippedCount: Int = 0,
    @SerialName("created_at") val createdAt: String? = null,
    @SerialName("finished_at") val finishedAt: String? = null,
    @SerialName("recipients") val recipients: List<PromoRecipientDto> = emptyList()
)

@Serializable
data class PromoRecipientDto(
    @SerialName("name") val name: String = "",
    @SerialName("phone") val phone: String = "",
    /** PENDING | SENDING | SENT | FAILED | SKIPPED_QUOTA */
    @SerialName("status") val status: String = "",
    @SerialName("error_detail") val errorDetail: String = ""
)

/** POST business/{id}/promo-sms/preview/ — exact cost before confirming. */
@Serializable
data class PromoPreviewDto(
    @SerialName("audience") val audience: String = "",
    @SerialName("recipient_count") val recipientCount: Int = 0,
    @SerialName("total_parts") val totalParts: Int = 0,
    @SerialName("parts_per_message") val partsPerMessage: Int = 0,
    @SerialName("sample_text") val sampleText: String = "",
    @SerialName("sample_length") val sampleLength: Int = 0,
    @SerialName("available_credit") val availableCredit: Int? = null,
    @SerialName("enough_credit") val enoughCredit: Boolean = true,
    @SerialName("within_send_window") val withinSendWindow: Boolean = true
)

@Serializable
data class PromoSmsRequestDto(
    @SerialName("audience") val audience: String,
    @SerialName("message") val message: String
)
