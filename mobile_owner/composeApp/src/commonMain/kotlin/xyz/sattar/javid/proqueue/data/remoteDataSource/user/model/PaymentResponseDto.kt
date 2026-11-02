package xyz.sattar.javid.proqueue.data.remoteDataSource.user.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class PaymentResponseDto(
    @SerialName("payment_url") val paymentUrl: String = "",
    @SerialName("track_id") val trackId: Long = 0,
    /** The toman wallet paid the whole price: nothing to open, already active. */
    @SerialName("paid_with_wallet") val paidWithWallet: Boolean = false
)
