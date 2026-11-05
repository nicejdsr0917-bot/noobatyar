package xyz.sattar.javid.proqueue.feature.stories

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import xyz.sattar.javid.proqueue.core.i18n.Str
import xyz.sattar.javid.proqueue.core.network.ApiResponse
import xyz.sattar.javid.proqueue.core.state.SelectedBusinessStore
import xyz.sattar.javid.proqueue.core.ui.BaseViewModel
import xyz.sattar.javid.proqueue.core.ui.components.UiMessage
import xyz.sattar.javid.proqueue.data.remoteDataSource.showcase.model.StoryDraft
import xyz.sattar.javid.proqueue.data.remoteDataSource.showcase.model.StoryDto
import xyz.sattar.javid.proqueue.domain.ShowcaseRepository

data class StoriesState(
    val isLoading: Boolean = false,
    val isPosting: Boolean = false,
    val stories: List<StoryDto> = emptyList(),
    val message: UiMessage? = null,
    /** Bumped after a successful post so the screen can close the composer. */
    val postedCount: Int = 0
) {
    sealed interface PartialState {
        data class IsLoading(val loading: Boolean) : PartialState
        data class IsPosting(val posting: Boolean) : PartialState
        data class LoadStories(val stories: List<StoryDto>) : PartialState
        data class Posted(val story: StoryDto) : PartialState
        data class Deleted(val id: Long) : PartialState
        data class ShowMessage(val message: UiMessage?) : PartialState
    }
}

sealed interface StoriesIntent {
    data object Load : StoriesIntent
    data class Post(val draft: StoryDraft) : StoriesIntent
    data class Delete(val id: Long) : StoriesIntent
    data object ClearMessage : StoriesIntent
}

/** The owner's 24-hour stories: list the active ones, post, delete. */
class StoriesViewModel(
    private val repository: ShowcaseRepository,
    private val businessStore: SelectedBusinessStore
) : BaseViewModel<StoriesState, StoriesState.PartialState, Unit, StoriesIntent>(
    initialState = StoriesState()
) {
    override fun handleIntent(intent: StoriesIntent): Flow<StoriesState.PartialState> = when (intent) {
        StoriesIntent.Load -> load()
        is StoriesIntent.Post -> post(intent.draft)
        is StoriesIntent.Delete -> delete(intent.id)
        StoriesIntent.ClearMessage -> flow { emit(StoriesState.PartialState.ShowMessage(null)) }
    }

    private fun load(): Flow<StoriesState.PartialState> = flow {
        val businessId = businessStore.selectedBusiness.value?.id ?: return@flow
        emit(StoriesState.PartialState.IsLoading(true))
        when (val response = repository.listStories(businessId)) {
            is ApiResponse.Success -> emit(StoriesState.PartialState.LoadStories(response.data))
            is ApiResponse.Error -> emit(StoriesState.PartialState.ShowMessage(UiMessage.error(response.message)))
        }
        emit(StoriesState.PartialState.IsLoading(false))
    }

    private fun post(draft: StoryDraft): Flow<StoriesState.PartialState> = flow {
        val businessId = businessStore.selectedBusiness.value?.id ?: return@flow
        if (uiState.value.isPosting) return@flow
        emit(StoriesState.PartialState.IsPosting(true))
        when (val response = repository.postStory(businessId, draft)) {
            is ApiResponse.Success -> {
                emit(StoriesState.PartialState.Posted(response.data))
                emit(StoriesState.PartialState.ShowMessage(UiMessage.success(Str.story_posted)))
            }
            is ApiResponse.Error -> emit(StoriesState.PartialState.ShowMessage(UiMessage.error(response.message)))
        }
        emit(StoriesState.PartialState.IsPosting(false))
    }

    private fun delete(id: Long): Flow<StoriesState.PartialState> = flow {
        when (val response = repository.deleteStory(id)) {
            is ApiResponse.Success -> emit(StoriesState.PartialState.Deleted(id))
            is ApiResponse.Error -> emit(StoriesState.PartialState.ShowMessage(UiMessage.error(response.message)))
        }
    }

    override fun reduceState(currentState: StoriesState, partialState: StoriesState.PartialState): StoriesState =
        when (partialState) {
            is StoriesState.PartialState.IsLoading -> currentState.copy(isLoading = partialState.loading)
            is StoriesState.PartialState.IsPosting -> currentState.copy(isPosting = partialState.posting)
            is StoriesState.PartialState.LoadStories -> currentState.copy(stories = partialState.stories)
            is StoriesState.PartialState.Posted -> currentState.copy(
                stories = currentState.stories + partialState.story,
                postedCount = currentState.postedCount + 1
            )
            is StoriesState.PartialState.Deleted ->
                currentState.copy(stories = currentState.stories.filterNot { it.id == partialState.id })
            is StoriesState.PartialState.ShowMessage -> currentState.copy(message = partialState.message)
        }

    override fun createErrorState(message: String): StoriesState.PartialState =
        StoriesState.PartialState.ShowMessage(UiMessage.error(message))
}
