package xyz.sattar.javid.proqueue.data.repository.showcase

import xyz.sattar.javid.proqueue.core.network.ApiResponse
import xyz.sattar.javid.proqueue.data.remoteDataSource.showcase.ShowcaseApi
import xyz.sattar.javid.proqueue.data.remoteDataSource.showcase.model.BannersDto
import xyz.sattar.javid.proqueue.data.remoteDataSource.showcase.model.OwnerReviewsPageDto
import xyz.sattar.javid.proqueue.data.remoteDataSource.showcase.model.FollowersPageDto
import xyz.sattar.javid.proqueue.data.remoteDataSource.showcase.model.ShowcaseItemDto
import xyz.sattar.javid.proqueue.domain.ShowcaseRepository
import xyz.sattar.javid.proqueue.domain.model.showcase.ShowcaseFields
import xyz.sattar.javid.proqueue.domain.model.showcase.ShowcaseKind

class ShowcaseRepositoryImpl(private val api: ShowcaseApi) : ShowcaseRepository {

    override suspend fun getBanners(businessId: Long): ApiResponse<BannersDto> =
        guarded { api.getBanners(businessId) }.let { response ->
            if (response !is ApiResponse.Success) return@let response
            // A banner without an image URL can never be drawn, and the server
            // already orders them, but sorting again costs nothing and keeps
            // the carousel deterministic if that ever changes.
            ApiResponse.Success(
                response.data.copy(
                    banners = response.data.banners
                        .filter { it.image.isNotBlank() }
                        .sortedBy { it.order },
                    linkUrl = response.data.linkUrl.trim()
                )
            )
        }

    override suspend fun getOwnerReviews(businessId: Long, page: Int, pageSize: Int): ApiResponse<OwnerReviewsPageDto> =
        guarded { api.getOwnerReviews(businessId, page, pageSize) }

    override suspend fun getFollowers(businessId: Long, pageSize: Int): ApiResponse<FollowersPageDto> =
        guarded { api.getFollowers(businessId, pageSize) }

    override suspend fun listItems(kind: ShowcaseKind, businessId: Long): ApiResponse<List<ShowcaseItemDto>> =
        guarded { api.listItems(kind, businessId) }.let { response ->
            if (response is ApiResponse.Success) ApiResponse.Success(response.data.sortedBy { it.order }) else response
        }

    override suspend fun addItem(
        kind: ShowcaseKind,
        businessId: Long,
        image: ByteArray,
        fields: ShowcaseFields
    ): ApiResponse<ShowcaseItemDto> = guarded { api.createItem(kind, businessId, image, fields) }

    override suspend fun updateItem(kind: ShowcaseKind, id: Long, fields: ShowcaseFields): ApiResponse<ShowcaseItemDto> =
        guarded { api.updateItem(kind, id, fields) }

    override suspend fun deleteItem(kind: ShowcaseKind, id: Long): ApiResponse<Unit> =
        guarded { api.deleteItem(kind, id) }

    override suspend fun reorder(kind: ShowcaseKind, businessId: Long, ids: List<Long>): ApiResponse<List<ShowcaseItemDto>> =
        guarded { api.reorder(kind, businessId, ids) }

    /** The API layer already maps transport errors; this catches anything it let through. */
    private suspend fun <T> guarded(block: suspend () -> ApiResponse<T>): ApiResponse<T> =
        try {
            block()
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            ApiResponse.Error(e.message ?: "", 0)
        }
}
