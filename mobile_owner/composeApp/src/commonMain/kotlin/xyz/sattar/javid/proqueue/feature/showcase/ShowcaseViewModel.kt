package xyz.sattar.javid.proqueue.feature.showcase

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import xyz.sattar.javid.proqueue.core.i18n.Str
import xyz.sattar.javid.proqueue.core.network.ApiResponse
import xyz.sattar.javid.proqueue.core.state.SelectedBusinessStore
import xyz.sattar.javid.proqueue.core.ui.BaseViewModel
import xyz.sattar.javid.proqueue.core.ui.components.UiMessage
import xyz.sattar.javid.proqueue.data.remoteDataSource.showcase.model.ShowcaseItemDto
import xyz.sattar.javid.proqueue.domain.ShowcaseRepository
import xyz.sattar.javid.proqueue.domain.model.showcase.SHOWCASE_MAX_ITEMS
import xyz.sattar.javid.proqueue.domain.model.showcase.ShowcaseFields
import xyz.sattar.javid.proqueue.domain.model.showcase.ShowcaseKind

data class ShowcaseState(
    val selectedKind: ShowcaseKind = ShowcaseKind.PORTFOLIO,
    val portfolio: List<ShowcaseItemDto> = emptyList(),
    val certificates: List<ShowcaseItemDto> = emptyList(),
    /** First fetch of the open tab. */
    val isLoading: Boolean = false,
    /** An upload, edit, delete or reorder is in flight; the buttons wait on it. */
    val isBusy: Boolean = false,
    /** Blocking error — shown when the tab has nothing to display. */
    val errorMessage: String? = null,
    val message: UiMessage? = null
) {
    fun itemsOf(kind: ShowcaseKind): List<ShowcaseItemDto> =
        if (kind == ShowcaseKind.PORTFOLIO) portfolio else certificates

    val items: List<ShowcaseItemDto> get() = itemsOf(selectedKind)

    sealed interface PartialState {
        data class SelectKind(val kind: ShowcaseKind) : PartialState
        data class IsLoading(val loading: Boolean) : PartialState
        data class IsBusy(val busy: Boolean) : PartialState
        data class SetItems(val kind: ShowcaseKind, val items: List<ShowcaseItemDto>) : PartialState
        data class ShowError(val message: String) : PartialState
        data class ShowMessage(val message: UiMessage?) : PartialState
    }
}

sealed interface ShowcaseIntent {
    /** Fetches the open tab; also the retry after an error. */
    data object Load : ShowcaseIntent
    data class SelectKind(val kind: ShowcaseKind) : ShowcaseIntent
    /** [image] is already cropped and compressed by the caller. */
    data class Add(val kind: ShowcaseKind, val image: ByteArray, val fields: ShowcaseFields) : ShowcaseIntent
    data class Edit(val kind: ShowcaseKind, val id: Long, val fields: ShowcaseFields) : ShowcaseIntent
    data class Delete(val kind: ShowcaseKind, val id: Long) : ShowcaseIntent
    /** [delta] is -1 to move the item one place earlier, +1 one place later. */
    data class Move(val kind: ShowcaseKind, val id: Long, val delta: Int) : ShowcaseIntent
    data object ClearMessage : ShowcaseIntent
}

/** Owner CRUD for portfolio photos and certificates of the selected business. */
class ShowcaseViewModel(
    private val repository: ShowcaseRepository,
    private val businessStore: SelectedBusinessStore
) : BaseViewModel<ShowcaseState, ShowcaseState.PartialState, Unit, ShowcaseIntent>(
    initialState = ShowcaseState()
) {
    // Tabs that have been fetched at least once, so switching back doesn't refetch.
    private val loaded = mutableSetOf<ShowcaseKind>()

    override fun handleIntent(intent: ShowcaseIntent): Flow<ShowcaseState.PartialState> = when (intent) {
        ShowcaseIntent.Load -> load(uiState.value.selectedKind)
        is ShowcaseIntent.SelectKind -> flow {
            emit(ShowcaseState.PartialState.SelectKind(intent.kind))
            if (intent.kind !in loaded) emitAll(load(intent.kind))
        }
        is ShowcaseIntent.Add -> add(intent)
        is ShowcaseIntent.Edit -> edit(intent)
        is ShowcaseIntent.Delete -> delete(intent)
        is ShowcaseIntent.Move -> move(intent)
        ShowcaseIntent.ClearMessage -> flow { emit(ShowcaseState.PartialState.ShowMessage(null)) }
    }

    private fun businessId(): Long? = businessStore.selectedBusiness.value?.id

    private fun load(kind: ShowcaseKind): Flow<ShowcaseState.PartialState> = flow {
        val businessId = businessId()
        if (businessId == null) {
            emit(ShowcaseState.PartialState.ShowError(Str.showcase_no_business))
            return@flow
        }
        emit(ShowcaseState.PartialState.IsLoading(true))
        when (val response = repository.listItems(kind, businessId)) {
            is ApiResponse.Success -> {
                loaded += kind
                emit(ShowcaseState.PartialState.SetItems(kind, response.data))
            }
            is ApiResponse.Error -> emit(ShowcaseState.PartialState.ShowError(response.message))
        }
    }

    private fun add(intent: ShowcaseIntent.Add): Flow<ShowcaseState.PartialState> = flow {
        val businessId = businessId() ?: return@flow emit(
            ShowcaseState.PartialState.ShowMessage(UiMessage.error(Str.showcase_no_business))
        )
        val current = uiState.value.itemsOf(intent.kind)
        if (current.size >= SHOWCASE_MAX_ITEMS) {
            emit(ShowcaseState.PartialState.ShowMessage(UiMessage.error(Str.showcase_limit_reached)))
            return@flow
        }
        validate(intent.kind, intent.fields)?.let {
            emit(ShowcaseState.PartialState.ShowMessage(UiMessage.error(it)))
            return@flow
        }
        emit(ShowcaseState.PartialState.IsBusy(true))
        when (val response = repository.addItem(intent.kind, businessId, intent.image, intent.fields)) {
            is ApiResponse.Success -> {
                emit(ShowcaseState.PartialState.SetItems(intent.kind, uiState.value.itemsOf(intent.kind) + response.data))
                emit(ShowcaseState.PartialState.ShowMessage(UiMessage.success(Str.showcase_added)))
            }
            is ApiResponse.Error ->
                emit(ShowcaseState.PartialState.ShowMessage(UiMessage.error(response.message)))
        }
        emit(ShowcaseState.PartialState.IsBusy(false))
    }

    private fun edit(intent: ShowcaseIntent.Edit): Flow<ShowcaseState.PartialState> = flow {
        validate(intent.kind, intent.fields)?.let {
            emit(ShowcaseState.PartialState.ShowMessage(UiMessage.error(it)))
            return@flow
        }
        emit(ShowcaseState.PartialState.IsBusy(true))
        when (val response = repository.updateItem(intent.kind, intent.id, intent.fields)) {
            is ApiResponse.Success -> {
                // The server re-queues an edited item, so its status comes back changed.
                val updated = uiState.value.itemsOf(intent.kind).map { if (it.id == intent.id) response.data else it }
                emit(ShowcaseState.PartialState.SetItems(intent.kind, updated))
                emit(ShowcaseState.PartialState.ShowMessage(UiMessage.success(Str.showcase_updated)))
            }
            is ApiResponse.Error ->
                emit(ShowcaseState.PartialState.ShowMessage(UiMessage.error(response.message)))
        }
        emit(ShowcaseState.PartialState.IsBusy(false))
    }

    private fun delete(intent: ShowcaseIntent.Delete): Flow<ShowcaseState.PartialState> = flow {
        emit(ShowcaseState.PartialState.IsBusy(true))
        when (val response = repository.deleteItem(intent.kind, intent.id)) {
            is ApiResponse.Success -> {
                emit(ShowcaseState.PartialState.SetItems(
                    intent.kind,
                    uiState.value.itemsOf(intent.kind).filterNot { it.id == intent.id }
                ))
                emit(ShowcaseState.PartialState.ShowMessage(UiMessage.success(Str.showcase_deleted)))
            }
            is ApiResponse.Error ->
                emit(ShowcaseState.PartialState.ShowMessage(UiMessage.error(response.message)))
        }
        emit(ShowcaseState.PartialState.IsBusy(false))
    }

    private fun move(intent: ShowcaseIntent.Move): Flow<ShowcaseState.PartialState> = flow {
        val businessId = businessId() ?: return@flow
        val before = uiState.value.itemsOf(intent.kind)
        val from = before.indexOfFirst { it.id == intent.id }
        val to = from + intent.delta
        if (from < 0 || to !in before.indices) return@flow

        val after = before.toMutableList().apply { add(to, removeAt(from)) }
            .mapIndexed { index, item -> item.copy(order = index) }
        // Optimistic: the row moves at once, and snaps back if the server refuses.
        emit(ShowcaseState.PartialState.SetItems(intent.kind, after))
        emit(ShowcaseState.PartialState.IsBusy(true))
        when (val response = repository.reorder(intent.kind, businessId, after.map { it.id })) {
            is ApiResponse.Success -> {
                // Trust the server's list only if it still describes the same items.
                if (response.data.map { it.id }.toSet() == after.map { it.id }.toSet()) {
                    emit(ShowcaseState.PartialState.SetItems(intent.kind, response.data.sortedBy { it.order }))
                }
            }
            is ApiResponse.Error -> {
                emit(ShowcaseState.PartialState.SetItems(intent.kind, before))
                emit(ShowcaseState.PartialState.ShowMessage(UiMessage.error(response.message)))
            }
        }
        emit(ShowcaseState.PartialState.IsBusy(false))
    }

    /** Client-side form checks, so an obvious mistake never costs an upload. */
    private fun validate(kind: ShowcaseKind, fields: ShowcaseFields): String? {
        if (kind != ShowcaseKind.CERTIFICATES) return null
        if (fields.title.isBlank()) return Str.showcase_title_required
        if (fields.year != null && fields.year !in 1300..2100) return Str.showcase_year_invalid
        return null
    }

    override fun reduceState(currentState: ShowcaseState, partialState: ShowcaseState.PartialState): ShowcaseState =
        when (partialState) {
            is ShowcaseState.PartialState.SelectKind ->
                currentState.copy(selectedKind = partialState.kind, errorMessage = null)

            is ShowcaseState.PartialState.IsLoading ->
                currentState.copy(isLoading = partialState.loading, errorMessage = null)

            is ShowcaseState.PartialState.IsBusy -> currentState.copy(isBusy = partialState.busy)

            is ShowcaseState.PartialState.SetItems -> when (partialState.kind) {
                ShowcaseKind.PORTFOLIO -> currentState.copy(portfolio = partialState.items, isLoading = false, errorMessage = null)
                ShowcaseKind.CERTIFICATES -> currentState.copy(certificates = partialState.items, isLoading = false, errorMessage = null)
            }

            is ShowcaseState.PartialState.ShowError ->
                currentState.copy(errorMessage = partialState.message, isLoading = false, isBusy = false)

            is ShowcaseState.PartialState.ShowMessage ->
                currentState.copy(message = partialState.message)
        }

    override fun createErrorState(message: String): ShowcaseState.PartialState =
        ShowcaseState.PartialState.ShowError(message)
}
