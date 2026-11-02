package xyz.sattar.javid.proqueue.feature.wallet

import xyz.sattar.javid.proqueue.core.ui.components.UiMessage
import xyz.sattar.javid.proqueue.data.remoteDataSource.user.model.EntitlementKeys
import xyz.sattar.javid.proqueue.data.remoteDataSource.user.model.WalletBalancesDto
import xyz.sattar.javid.proqueue.data.remoteDataSource.user.model.WalletLedgerEntryDto

data class WalletState(
    val isLoading: Boolean = false,
    val isPaginating: Boolean = false,
    /** Null until the first page arrives, so the cards don't flash zeros. */
    val balances: WalletBalancesDto? = null,
    val entries: List<WalletLedgerEntryDto> = emptyList(),
    val currentPage: Int = 0,
    val canLoadMore: Boolean = false,
    /** Set when the first page failed — the screen shows a retry instead of an empty list. */
    val loadFailed: Boolean = false,
    val message: UiMessage? = null
) {
    /** Appointment credit is never spent while the plan's booking quota is unlimited. */
    val appointmentCreditIdle: Boolean
        get() = balances?.appointmentQuota == EntitlementKeys.UNLIMITED

    sealed interface PartialState {
        data class IsLoading(val loading: Boolean) : PartialState
        data class IsPaginating(val paginating: Boolean) : PartialState
        data class PageLoaded(
            val page: Int,
            val entries: List<WalletLedgerEntryDto>,
            val balances: WalletBalancesDto,
            val canLoadMore: Boolean
        ) : PartialState
        data object FirstPageFailed : PartialState
        data class ShowMessage(val message: UiMessage?) : PartialState
    }
}
