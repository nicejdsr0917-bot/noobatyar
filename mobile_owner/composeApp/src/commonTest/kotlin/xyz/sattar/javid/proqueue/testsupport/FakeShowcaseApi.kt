package xyz.sattar.javid.proqueue.testsupport

import xyz.sattar.javid.proqueue.core.network.ApiResponse
import xyz.sattar.javid.proqueue.data.remoteDataSource.showcase.ShowcaseApi
import xyz.sattar.javid.proqueue.data.remoteDataSource.showcase.model.BannersDto
import xyz.sattar.javid.proqueue.data.remoteDataSource.showcase.model.OwnerReviewsPageDto
import xyz.sattar.javid.proqueue.data.remoteDataSource.showcase.model.ShowcaseItemDto
import xyz.sattar.javid.proqueue.domain.model.showcase.ShowcaseFields
import xyz.sattar.javid.proqueue.domain.model.showcase.ShowcaseKind

/** In-memory server: each member is a replaceable lambda so a test scripts only what it uses. */
class FakeShowcaseApi : ShowcaseApi {
    var banners: (Long) -> ApiResponse<BannersDto> = { ApiResponse.Success(BannersDto()) }
    var reviews: (Long, Int, Int) -> ApiResponse<OwnerReviewsPageDto> = { _, _, _ -> ApiResponse.Success(OwnerReviewsPageDto()) }

    val items = mutableMapOf(
        ShowcaseKind.PORTFOLIO to mutableListOf<ShowcaseItemDto>(),
        ShowcaseKind.CERTIFICATES to mutableListOf<ShowcaseItemDto>()
    )
    var failNext: String? = null
    var nextId = 100L
    val created = mutableListOf<Triple<ShowcaseKind, Long, ShowcaseFields>>()
    val reorders = mutableListOf<List<Long>>()
    val bannerCalls = mutableListOf<Long>()

    private fun <T> failure(): ApiResponse<T>? = failNext?.let { msg -> failNext = null; ApiResponse.Error(msg, 400) }

    override suspend fun getBanners(businessId: Long): ApiResponse<BannersDto> {
        bannerCalls += businessId
        return banners(businessId)
    }

    override suspend fun getOwnerReviews(businessId: Long, page: Int, pageSize: Int) = reviews(businessId, page, pageSize)

    override suspend fun listItems(kind: ShowcaseKind, businessId: Long): ApiResponse<List<ShowcaseItemDto>> =
        failure() ?: ApiResponse.Success(items.getValue(kind).toList())

    override suspend fun createItem(kind: ShowcaseKind, businessId: Long, image: ByteArray, fields: ShowcaseFields): ApiResponse<ShowcaseItemDto> {
        failure<ShowcaseItemDto>()?.let { return it }
        created += Triple(kind, businessId, fields)
        val list = items.getValue(kind)
        val item = ShowcaseItemDto(
            id = nextId++, image = "https://x/i.jpg", caption = fields.caption, title = fields.title,
            issuer = fields.issuer, year = fields.year, order = list.size, status = "PENDING"
        )
        list += item
        return ApiResponse.Success(item)
    }

    override suspend fun updateItem(kind: ShowcaseKind, id: Long, fields: ShowcaseFields): ApiResponse<ShowcaseItemDto> {
        failure<ShowcaseItemDto>()?.let { return it }
        val list = items.getValue(kind)
        val index = list.indexOfFirst { it.id == id }
        // The server re-queues an edited item, whatever its status was.
        val updated = list[index].copy(caption = fields.caption, title = fields.title, issuer = fields.issuer, year = fields.year, status = "PENDING", rejectReason = "")
        list[index] = updated
        return ApiResponse.Success(updated)
    }

    override suspend fun deleteItem(kind: ShowcaseKind, id: Long): ApiResponse<Unit> {
        failure<Unit>()?.let { return it }
        items.getValue(kind).removeAll { it.id == id }
        return ApiResponse.Success(Unit)
    }

    override suspend fun reorder(kind: ShowcaseKind, businessId: Long, ids: List<Long>): ApiResponse<List<ShowcaseItemDto>> {
        failure<List<ShowcaseItemDto>>()?.let { return it }
        reorders += ids
        val list = items.getValue(kind)
        val reordered = ids.mapIndexed { index, id -> list.first { it.id == id }.copy(order = index) }
        list.clear(); list += reordered
        return ApiResponse.Success(reordered)
    }
}

fun showcaseItem(id: Long, order: Int = 0, status: String = "APPROVED", caption: String = "", title: String = "") =
    ShowcaseItemDto(id = id, image = "https://x/$id.jpg", caption = caption, title = title, order = order, status = status)
