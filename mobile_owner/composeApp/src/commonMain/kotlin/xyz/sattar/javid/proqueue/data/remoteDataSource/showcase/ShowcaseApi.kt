package xyz.sattar.javid.proqueue.data.remoteDataSource.showcase

import xyz.sattar.javid.proqueue.core.network.ApiResponse
import xyz.sattar.javid.proqueue.data.remoteDataSource.showcase.model.BannersDto
import xyz.sattar.javid.proqueue.data.remoteDataSource.showcase.model.OwnerReviewsPageDto
import xyz.sattar.javid.proqueue.data.remoteDataSource.showcase.model.BookingCapacityDto
import xyz.sattar.javid.proqueue.data.remoteDataSource.showcase.model.FirstVisitDiscountDto
import xyz.sattar.javid.proqueue.data.remoteDataSource.showcase.model.FollowersPageDto
import xyz.sattar.javid.proqueue.data.remoteDataSource.showcase.model.StoryDto
import xyz.sattar.javid.proqueue.data.remoteDataSource.showcase.model.StoryDraft
import xyz.sattar.javid.proqueue.data.remoteDataSource.showcase.model.ShowcaseItemDto
import xyz.sattar.javid.proqueue.domain.model.showcase.ShowcaseFields
import xyz.sattar.javid.proqueue.domain.model.showcase.ShowcaseKind

/**
 * Home banners, the owner's read-only reviews and portfolio/certificate CRUD
 * (docs/api/P2_SHOWCASE_API.md, P2_SOCIAL_API.md). Kept apart from
 * [xyz.sattar.javid.proqueue.data.remoteDataSource.business.BusinessApi] so
 * that contract (and every fake of it) stays untouched.
 */
interface ShowcaseApi {
    suspend fun getBanners(businessId: Long): ApiResponse<BannersDto>

    suspend fun getOwnerReviews(businessId: Long, page: Int, pageSize: Int): ApiResponse<OwnerReviewsPageDto>

    /** Newest followers of the owner's business, first name and photo only. */
    suspend fun getFollowers(businessId: Long, pageSize: Int): ApiResponse<FollowersPageDto>

    /** One page of the full followers list (first name and photo only); fakes need not override it. */
    suspend fun getFollowersPage(businessId: Long, page: Int, pageSize: Int): ApiResponse<FollowersPageDto> =
        if (page == 1) getFollowers(businessId, pageSize) else ApiResponse.Success(FollowersPageDto())

    /** How a slot is booked (clients per slot, deposit hold); fakes need not override these. */
    suspend fun getBookingCapacity(businessId: Long): ApiResponse<BookingCapacityDto> =
        ApiResponse.Success(BookingCapacityDto())
    /** [changes]: only the keys being changed ("slot_capacity", "hold_minutes"). */
    suspend fun setBookingCapacity(businessId: Long, changes: Map<String, Int>): ApiResponse<BookingCapacityDto> =
        ApiResponse.Success(BookingCapacityDto())

    /** The business's first-visit discount percent (0 = off); fakes need not override these. */
    suspend fun getFirstVisitDiscount(businessId: Long): ApiResponse<FirstVisitDiscountDto> =
        ApiResponse.Success(FirstVisitDiscountDto(0))
    suspend fun setFirstVisitDiscount(businessId: Long, percent: Int): ApiResponse<FirstVisitDiscountDto> =
        ApiResponse.Success(FirstVisitDiscountDto(percent))

    suspend fun listStories(businessId: Long): ApiResponse<List<StoryDto>>

    suspend fun postStory(businessId: Long, draft: StoryDraft): ApiResponse<StoryDto>

    suspend fun deleteStory(id: Long): ApiResponse<Unit>

    suspend fun listItems(kind: ShowcaseKind, businessId: Long): ApiResponse<List<ShowcaseItemDto>>

    suspend fun createItem(
        kind: ShowcaseKind,
        businessId: Long,
        image: ByteArray,
        fields: ShowcaseFields
    ): ApiResponse<ShowcaseItemDto>

    suspend fun updateItem(kind: ShowcaseKind, id: Long, fields: ShowcaseFields): ApiResponse<ShowcaseItemDto>

    suspend fun deleteItem(kind: ShowcaseKind, id: Long): ApiResponse<Unit>

    /** [ids] must list every item of the business exactly once. */
    suspend fun reorder(kind: ShowcaseKind, businessId: Long, ids: List<Long>): ApiResponse<List<ShowcaseItemDto>>
}
