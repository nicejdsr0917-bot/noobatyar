package xyz.sattar.javid.proqueue.data.remoteDataSource.user.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Response of GET accounting/wallet/history/ — one page of the user's add-on
 * credit history plus the current balances. Credit belongs to the user, not to
 * a business, so it spans all of their businesses.
 */
@Serializable
data class WalletHistoryDto(
    val count: Int = 0,
    val next: String? = null,
    val results: List<WalletLedgerEntryDto> = emptyList(),
    val balances: WalletBalancesDto = WalletBalancesDto()
)

@Serializable
data class WalletLedgerEntryDto(
    val id: Long,
    /** [WalletMetric.APPOINTMENTS] or [WalletMetric.SMS]. */
    val metric: String,
    val delta: Int,
    @SerialName("balance_after") val balanceAfter: Int,
    /** Machine code (booking, sms_send, addon_purchase…) — localised in the app. */
    val reason: String = "",
    /** Server's Persian wording, for codes this app version does not know. */
    @SerialName("reason_label") val reasonLabel: String = "",
    @SerialName("created_at") val createdAt: String = ""
)

@Serializable
data class WalletBalancesDto(
    /** Toman given back by Noobatyar (discounts it funds); spent on plans/packs or converted. */
    @SerialName("toman_wallet") val tomanWallet: Long = 0,
    @SerialName("sms_unit_price") val smsUnitPrice: Long = 0,
    @SerialName("appointment_unit_price") val appointmentUnitPrice: Long = 0,
    @SerialName("appointment_wallet") val appointmentWallet: Int = 0,
    @SerialName("sms_wallet") val smsWallet: Int = 0,
    /** Monthly plan quota; -1 = unlimited, where appointment credit is never spent. */
    @SerialName("appointment_quota") val appointmentQuota: Int = 0,
    @SerialName("sms_quota") val smsQuota: Int = 0
)

object WalletMetric {
    const val TOMAN = "toman_wallet"
    const val APPOINTMENTS = "appointment_wallet"
    const val SMS = "sms_wallet"
}

/** Body of POST accounting/wallet/convert/. */
@Serializable
data class WalletConvertRequestDto(
    /** [WalletConvertKind.SMS] or [WalletConvertKind.APPOINTMENT]. */
    val kind: String,
    val count: Int
)

@Serializable
data class WalletConvertDto(
    val kind: String = "",
    val count: Int = 0,
    val cost: Long = 0,
    val balance: Long = 0
)

object WalletConvertKind {
    const val SMS = "sms"
    const val APPOINTMENT = "appointment"
}
