package xyz.sattar.javid.proqueue.feature.addons

import xyz.sattar.javid.proqueue.core.i18n.Str
import xyz.sattar.javid.proqueue.core.ui.components.UiMessage
import xyz.sattar.javid.proqueue.domain.usecase.user.PurchaseOutcome
import xyz.sattar.javid.proqueue.domain.usecase.user.PurchaseUseCase
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import xyz.sattar.javid.proqueue.core.network.ApiResponse
import xyz.sattar.javid.proqueue.core.ui.BaseViewModel
import xyz.sattar.javid.proqueue.domain.usecase.user.BuyAddonUseCase
import xyz.sattar.javid.proqueue.domain.usecase.user.GetAddonsUseCase

class AddonsViewModel(
    private val getAddonsUseCase: GetAddonsUseCase,
    private val purchaseUseCase: PurchaseUseCase
) : BaseViewModel<AddonsState, AddonsState.PartialState, AddonsEvent, AddonsIntent>(
    initialState = AddonsState()
) {
    override fun handleIntent(intent: AddonsIntent): Flow<AddonsState.PartialState> {
        return when (intent) {
            AddonsIntent.Load -> load()
            is AddonsIntent.Buy -> buy(intent.packId)
            AddonsIntent.ClearMessage -> flow { emit(AddonsState.PartialState.ShowMessage(null)) }
        }
    }

    private fun load(): Flow<AddonsState.PartialState> = flow {
        emit(AddonsState.PartialState.IsLoading(true))
        try {
            when (val response = getAddonsUseCase()) {
                is ApiResponse.Success -> emit(AddonsState.PartialState.LoadPacks(response.data))
                is ApiResponse.Error -> emit(AddonsState.PartialState.ShowMessage(UiMessage.error(response.message)))
            }
        } catch (e: Exception) {
            emit(AddonsState.PartialState.ShowMessage(UiMessage.error(e.message ?: Str.couldnt_load_packs)))
        }
        emit(AddonsState.PartialState.IsLoading(false))
    }

    private fun buy(packId: Int): Flow<AddonsState.PartialState> = flow {
        emit(AddonsState.PartialState.SetPurchasing(packId))
        try {
            when (val outcome = purchaseUseCase.addon(packId)) {
                is PurchaseOutcome.Redirect -> sendEvent(AddonsEvent.OpenUrl(outcome.url))
                is PurchaseOutcome.Completed -> emit(AddonsState.PartialState.ShowMessage(UiMessage.success(outcome.message)))
                PurchaseOutcome.Cancelled -> Unit
                is PurchaseOutcome.Failed -> emit(AddonsState.PartialState.ShowMessage(UiMessage.error(outcome.message)))
            }
        } catch (e: Exception) {
            emit(AddonsState.PartialState.ShowMessage(UiMessage.error(e.message ?: Str.connection_error)))
        }
        emit(AddonsState.PartialState.SetPurchasing(null))
    }

    override fun reduceState(
        currentState: AddonsState,
        partialState: AddonsState.PartialState
    ): AddonsState {
        return when (partialState) {
            is AddonsState.PartialState.IsLoading -> currentState.copy(isLoading = partialState.loading)
            is AddonsState.PartialState.LoadPacks -> currentState.copy(packs = partialState.packs)
            is AddonsState.PartialState.ShowMessage -> currentState.copy(message = partialState.message)
            is AddonsState.PartialState.SetPurchasing -> currentState.copy(purchasingPackId = partialState.packId)
        }
    }

    override fun createErrorState(message: String): AddonsState.PartialState =
        AddonsState.PartialState.ShowMessage(UiMessage.error(message))
}
