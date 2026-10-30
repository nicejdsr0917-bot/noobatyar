package xyz.sattar.javid.proqueue.feature.banner

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import xyz.sattar.javid.proqueue.core.network.ApiResponse
import xyz.sattar.javid.proqueue.core.ui.BaseViewModel
import xyz.sattar.javid.proqueue.data.remoteDataSource.showcase.model.BannerDto
import xyz.sattar.javid.proqueue.domain.ShowcaseRepository

data class BannerState(
    /** Business the banners below were fetched for; null before the first load. */
    val businessId: Long? = null,
    val banners: List<BannerDto> = emptyList(),
    /** Shared tap target of every banner; blank means taps do nothing. */
    val linkUrl: String = ""
) {
    /** The carousel is drawn only when there is something to draw. */
    val visible: Boolean get() = banners.isNotEmpty()

    sealed interface PartialState {
        data class Requested(val businessId: Long) : PartialState
        data class Loaded(val businessId: Long, val banners: List<BannerDto>, val linkUrl: String) : PartialState
        data class Failed(val businessId: Long?) : PartialState
    }
}

sealed interface BannerIntent {
    data class Load(val businessId: Long) : BannerIntent
}

/**
 * Home-screen banners. A banner is decoration, so every failure — network,
 * server, a business the call rejects — collapses to "no banners" instead of
 * surfacing an error on the dashboard.
 */
class BannerViewModel(
    private val repository: ShowcaseRepository
) : BaseViewModel<BannerState, BannerState.PartialState, Unit, BannerIntent>(
    initialState = BannerState()
) {
    override fun handleIntent(intent: BannerIntent): Flow<BannerState.PartialState> = when (intent) {
        is BannerIntent.Load -> load(intent.businessId)
    }

    private fun load(businessId: Long): Flow<BannerState.PartialState> = flow {
        emit(BannerState.PartialState.Requested(businessId))
        when (val response = repository.getBanners(businessId)) {
            is ApiResponse.Success -> emit(
                BannerState.PartialState.Loaded(businessId, response.data.banners, response.data.linkUrl)
            )
            is ApiResponse.Error -> emit(BannerState.PartialState.Failed(businessId))
        }
    }

    override fun reduceState(currentState: BannerState, partialState: BannerState.PartialState): BannerState =
        when (partialState) {
            // Switching business drops the old business's banners at once so
            // they can't flash under the new one while the call is in flight.
            is BannerState.PartialState.Requested ->
                if (currentState.businessId == partialState.businessId) currentState
                else BannerState(businessId = partialState.businessId)

            // A late answer for a business the owner already left is ignored.
            is BannerState.PartialState.Loaded ->
                if (currentState.businessId != partialState.businessId) currentState
                else currentState.copy(banners = partialState.banners, linkUrl = partialState.linkUrl)

            is BannerState.PartialState.Failed ->
                if (partialState.businessId != null && currentState.businessId != partialState.businessId) currentState
                else currentState.copy(banners = emptyList(), linkUrl = "")
        }

    override fun createErrorState(message: String): BannerState.PartialState =
        BannerState.PartialState.Failed(null)
}
