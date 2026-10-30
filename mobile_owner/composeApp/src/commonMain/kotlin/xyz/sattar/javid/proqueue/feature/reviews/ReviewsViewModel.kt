package xyz.sattar.javid.proqueue.feature.reviews

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import xyz.sattar.javid.proqueue.core.i18n.Str
import xyz.sattar.javid.proqueue.core.network.ApiResponse
import xyz.sattar.javid.proqueue.core.state.SelectedBusinessStore
import xyz.sattar.javid.proqueue.core.ui.BaseViewModel
import xyz.sattar.javid.proqueue.core.ui.components.UiMessage
import xyz.sattar.javid.proqueue.data.remoteDataSource.showcase.model.OwnerReviewDto
import xyz.sattar.javid.proqueue.data.remoteDataSource.showcase.model.ReviewSummaryDto
import xyz.sattar.javid.proqueue.domain.ShowcaseRepository

data class ReviewsState(
    val isLoading: Boolean = false,
    /** Set only while appending a further page, so the list stays on screen. */
    val isPaginating: Boolean = false,
    val reviews: List<OwnerReviewDto> = emptyList(),
    val summary: ReviewSummaryDto? = null,
    val currentPage: Int = 0,
    val canLoadMore: Boolean = true,
    /** Blocking error — shown instead of the list when nothing has loaded. */
    val errorMessage: String? = null,
    /** Transient error — a toast over whatever is already loaded. */
    val message: UiMessage? = null
) {
    sealed interface PartialState {
        data class IsLoading(val loading: Boolean) : PartialState
        data class IsPaginating(val paginating: Boolean) : PartialState
        data class LoadPage(
            val reviews: List<OwnerReviewDto>,
            val summary: ReviewSummaryDto?,
            val page: Int,
            val canLoadMore: Boolean
        ) : PartialState
        data class ShowError(val message: String) : PartialState
        data class ShowMessage(val message: UiMessage?) : PartialState
    }
}

sealed interface ReviewsIntent {
    /** First page; also the retry after an error and the pull-to-refresh. */
    data object Load : ReviewsIntent
    data object LoadMore : ReviewsIntent
    data object ClearMessage : ReviewsIntent
}

/** The owner's read-only view of their business's reviews, hidden ones included. */
class ReviewsViewModel(
    private val repository: ShowcaseRepository,
    private val businessStore: SelectedBusinessStore
) : BaseViewModel<ReviewsState, ReviewsState.PartialState, Unit, ReviewsIntent>(
    initialState = ReviewsState()
) {
    private val pageSize = 20

    override fun handleIntent(intent: ReviewsIntent): Flow<ReviewsState.PartialState> = when (intent) {
        ReviewsIntent.Load -> load(reset = true)
        ReviewsIntent.LoadMore -> load(reset = false)
        ReviewsIntent.ClearMessage -> flow { emit(ReviewsState.PartialState.ShowMessage(null)) }
    }

    private fun load(reset: Boolean): Flow<ReviewsState.PartialState> = flow {
        val current = uiState.value
        val businessId = businessStore.selectedBusiness.value?.id
        if (businessId == null) {
            emit(ReviewsState.PartialState.ShowError(Str.showcase_no_business))
            return@flow
        }
        if (!reset && (!current.canLoadMore || current.isPaginating || current.isLoading)) return@flow

        val page = if (reset) 1 else current.currentPage + 1
        emit(if (reset) ReviewsState.PartialState.IsLoading(true) else ReviewsState.PartialState.IsPaginating(true))

        when (val response = repository.getOwnerReviews(businessId, page, pageSize)) {
            is ApiResponse.Success -> emit(
                ReviewsState.PartialState.LoadPage(
                    reviews = response.data.results,
                    summary = response.data.summary,
                    page = page,
                    canLoadMore = response.data.next != null
                )
            )
            // A failed first page blocks the screen; a failed further page
            // must not — the rows already shown are still valid.
            is ApiResponse.Error -> emit(
                if (reset) ReviewsState.PartialState.ShowError(response.message)
                else ReviewsState.PartialState.ShowMessage(UiMessage.error(response.message))
            )
        }
    }

    override fun reduceState(currentState: ReviewsState, partialState: ReviewsState.PartialState): ReviewsState =
        when (partialState) {
            is ReviewsState.PartialState.IsLoading ->
                currentState.copy(isLoading = partialState.loading, errorMessage = null)

            is ReviewsState.PartialState.IsPaginating ->
                currentState.copy(isPaginating = partialState.paginating)

            is ReviewsState.PartialState.LoadPage -> {
                // Append, never replace, from page 2 on; distinctBy covers a
                // review shifting between pages while new ones arrive.
                val merged = if (partialState.page == 1) partialState.reviews
                else (currentState.reviews + partialState.reviews).distinctBy { it.id }
                currentState.copy(
                    reviews = merged,
                    summary = partialState.summary ?: currentState.summary,
                    currentPage = partialState.page,
                    canLoadMore = partialState.canLoadMore,
                    isLoading = false,
                    isPaginating = false,
                    errorMessage = null
                )
            }

            is ReviewsState.PartialState.ShowError ->
                currentState.copy(errorMessage = partialState.message, isLoading = false, isPaginating = false)

            is ReviewsState.PartialState.ShowMessage ->
                currentState.copy(message = partialState.message, isLoading = false, isPaginating = false)
        }

    override fun createErrorState(message: String): ReviewsState.PartialState =
        ReviewsState.PartialState.ShowError(message)
}
