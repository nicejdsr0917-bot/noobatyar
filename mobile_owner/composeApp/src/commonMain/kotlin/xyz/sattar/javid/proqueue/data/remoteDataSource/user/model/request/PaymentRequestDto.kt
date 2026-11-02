package xyz.sattar.javid.proqueue.data.remoteDataSource.user.model.request

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class PaymentRequestDto(
    @SerialName("plan_id") val planId: String,
    @SerialName("use_wallet") val useWallet: Boolean = false,
    /** Store builds: pay entirely from the wallet or fail — never the Zibal gateway. */
    @SerialName("wallet_only") val walletOnly: Boolean = false
)
