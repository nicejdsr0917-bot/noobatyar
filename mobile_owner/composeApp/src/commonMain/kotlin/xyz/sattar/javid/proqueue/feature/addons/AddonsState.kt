package xyz.sattar.javid.proqueue.feature.addons

import xyz.sattar.javid.proqueue.core.ui.components.UiMessage
import xyz.sattar.javid.proqueue.data.remoteDataSource.user.model.AddOnPackDto

data class AddonsState(
    val isLoading: Boolean = false,
    val packs: List<AddOnPackDto> = emptyList(),
    val purchasingPackId: Int? = null,
    val message: UiMessage? = null
) {
    sealed interface PartialState {
        data class IsLoading(val loading: Boolean) : PartialState
        data class LoadPacks(val packs: List<AddOnPackDto>) : PartialState
        data class ShowMessage(val message: UiMessage?) : PartialState
        data class SetPurchasing(val packId: Int?) : PartialState
    }
}
