package xyz.sattar.javid.proqueue.feature.wallet

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import xyz.sattar.javid.proqueue.core.i18n.Str
import xyz.sattar.javid.proqueue.core.network.ApiResponse
import xyz.sattar.javid.proqueue.core.ui.BaseViewModel
import xyz.sattar.javid.proqueue.core.ui.components.UiMessage
import xyz.sattar.javid.proqueue.domain.usecase.user.GetWalletHistoryUseCase

class WalletViewModel(
    private val getWalletHistoryUseCase: GetWalletHistoryUseCase
) : BaseViewModel<WalletState, WalletState.PartialState, Nothing, WalletIntent>(
    initialState = WalletState()
) {
    private val pageSize = 20

    override fun handleIntent(intent: WalletIntent): Flow<WalletState.PartialState> {
        return when (intent) {
            WalletIntent.Load -> load(reset = true)
            WalletIntent.LoadMore -> load(reset = false)
            WalletIntent.ClearMessage -> flow { emit(WalletState.PartialState.ShowMessage(null)) }
        }
    }

    private fun load(reset: Boolean): Flow<WalletState.PartialState> = flow {
        val current = uiState.value
        if (!reset && (!current.canLoadMore || current.isPaginating || current.isLoading)) return@flow
        val page = if (reset) 1 else current.currentPage + 1
        val busy = { on: Boolean ->
            if (reset) WalletState.PartialState.IsLoading(on) else WalletState.PartialState.IsPaginating(on)
        }

        emit(busy(true))
        val failure: String? = try {
            when (val response = getWalletHistoryUseCase(page, pageSize)) {
                is ApiResponse.Success -> {
                    emit(
                        WalletState.PartialState.PageLoaded(
                            page = page,
                            entries = response.data.results,
                            balances = response.data.balances,
                            canLoadMore = response.data.next != null
                        )
                    )
                    null
                }
                is ApiResponse.Error -> response.message
            }
        } catch (e: Exception) {
            e.message ?: Str.wallet_load_error
        }
        if (failure != null) {
            // A failed first page blocks the screen; a failed further page only toasts.
            if (reset) emit(WalletState.PartialState.FirstPageFailed)
            emit(WalletState.PartialState.ShowMessage(UiMessage.error(failure.ifBlank { Str.wallet_load_error })))
        }
        emit(busy(false))
    }

    override fun reduceState(
        currentState: WalletState,
        partialState: WalletState.PartialState
    ): WalletState {
        return when (partialState) {
            is WalletState.PartialState.IsLoading -> currentState.copy(isLoading = partialState.loading)
            is WalletState.PartialState.IsPaginating -> currentState.copy(isPaginating = partialState.paginating)
            is WalletState.PartialState.PageLoaded -> {
                // Page 1 replaces; later pages append. New rows written between
                // two requests shift older ones across the page boundary, so
                // drop ids already listed.
                val merged = if (partialState.page == 1) {
                    partialState.entries
                } else {
                    val seen = currentState.entries.mapTo(HashSet()) { it.id }
                    currentState.entries + partialState.entries.filter { it.id !in seen }
                }
                currentState.copy(
                    entries = merged,
                    balances = partialState.balances,
                    currentPage = partialState.page,
                    canLoadMore = partialState.canLoadMore,
                    loadFailed = false
                )
            }
            WalletState.PartialState.FirstPageFailed -> currentState.copy(loadFailed = true)
            is WalletState.PartialState.ShowMessage -> currentState.copy(message = partialState.message)
        }
    }

    override fun createErrorState(message: String): WalletState.PartialState =
        WalletState.PartialState.ShowMessage(UiMessage.error(message))
}
