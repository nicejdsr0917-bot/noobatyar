package xyz.sattar.javid.proqueue.data.remoteDataSource.user.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** POST accounting/store/{store}/intent/ — backend accounting/payment/myket.py. */
@Serializable
data class StoreIntentRequestDto(
    /** "plan" | "addon" */
    @SerialName("kind") val kind: String,
    @SerialName("id") val id: Int
)

@Serializable
data class StoreIntentDto(
    @SerialName("sku") val sku: String,
    @SerialName("payload") val payload: String
)

/** POST accounting/store/{store}/verify/ — the server verifies with the store, then grants. */
@Serializable
data class StoreVerifyRequestDto(
    @SerialName("payload") val payload: String,
    @SerialName("sku") val sku: String,
    @SerialName("token") val token: String,
    @SerialName("order_id") val orderId: String
)

@Serializable
data class StoreVerifyDto(
    @SerialName("status") val status: String = "",
    @SerialName("kind") val kind: String = "",
    @SerialName("message") val message: String = ""
)

/**
 * GET accounting/checkout/quote/ — what a purchase will cost, line by line
 * (backend accounting/pricing.py). Toman. Shown in the confirm dialog before
 * the Zibal gateway or the store opens.
 */
@Serializable
data class CheckoutQuoteDto(
    @SerialName("title") val title: String = "",
    /** "zibal" | "myket" */
    @SerialName("channel") val channel: String,
    @SerialName("base") val base: Long,
    @SerialName("vat") val vat: Long,
    @SerialName("fee") val fee: Long,
    @SerialName("total") val total: Long
)
