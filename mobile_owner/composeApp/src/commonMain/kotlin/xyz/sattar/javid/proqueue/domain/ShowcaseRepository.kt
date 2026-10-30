package xyz.sattar.javid.proqueue.domain

import xyz.sattar.javid.proqueue.core.network.ApiResponse
import xyz.sattar.javid.proqueue.data.remoteDataSource.showcase.model.BannersDto
import xyz.sattar.javid.proqueue.data.remoteDataSource.showcase.model.OwnerReviewsPageDto
import xyz.sattar.javid.proqueue.data.remoteDataSource.showcase.model.ShowcaseItemDto
import xyz.sattar.javid.proqueue.domain.model.showcase.ShowcaseFields
import xyz.sattar.javid.proqueue.domain.model.showcase.ShowcaseKind

/**
 * Server-only data (no local cache): home banners, the owner's reviews and
 * portfolio/certificate management.
 */
interface ShowcaseRepository {
    /** Usable banners in display order; an empty list means "show nothing". */
    suspend fun getBanners(businessId: Long): ApiResponse<BannersDto>

    suspend fun getOwnerReviews(businessId: Long, page: Int, pageSize: Int): ApiResponse<OwnerReviewsPageDto>

    suspend fun listItems(kind: ShowcaseKind, businessId: Long): ApiResponse<List<ShowcaseItemDto>>

    suspend fun addItem(kind: ShowcaseKind, businessId: Long, image: ByteArray, fields: ShowcaseFields): ApiResponse<ShowcaseItemDto>

    suspend fun updateItem(kind: ShowcaseKind, id: Long, fields: ShowcaseFields): ApiResponse<ShowcaseItemDto>

    suspend fun deleteItem(kind: ShowcaseKind, id: Long): ApiResponse<Unit>

    suspend fun reorder(kind: ShowcaseKind, businessId: Long, ids: List<Long>): ApiResponse<List<ShowcaseItemDto>>
}
