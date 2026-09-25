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
