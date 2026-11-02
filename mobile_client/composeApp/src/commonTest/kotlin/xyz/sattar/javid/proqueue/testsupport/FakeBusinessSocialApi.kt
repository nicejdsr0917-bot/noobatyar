package xyz.sattar.javid.proqueue.testsupport

import xyz.sattar.javid.proqueue.core.network.ApiResponse
import xyz.sattar.javid.proqueue.core.network.PaginatedResponseDto
import xyz.sattar.javid.proqueue.data.remoteDataSource.social.BusinessSocialApi
import xyz.sattar.javid.proqueue.data.remoteDataSource.social.model.MyReviewDto
import xyz.sattar.javid.proqueue.data.remoteDataSource.social.model.ReviewDto
import xyz.sattar.javid.proqueue.data.remoteDataSource.social.model.ReviewSummaryDto
import xyz.sattar.javid.proqueue.data.remoteDataSource.social.model.ShowcaseDto

fun review(id: Long, rating: Int = 5, text: String? = "عالی") =
    ReviewDto(id = id, rating = rating, text = text, createdAt = "2026-10-30T09:00:00Z", visitorName = "علی")

fun reviewPage(vararg items: ReviewDto, next: Int? = null) =
    ApiResponse.Success(PaginatedResponseDto(count = items.size, next = next, results = items.toList()))

class ReviewWrite(val kind: String, val token: String, val businessId: Long, val rating: Int, val text: String)
class ReviewReportCall(val token: String?, val reviewId: Long, val reason: String, val detail: String)

class FakeBusinessSocialApi : BusinessSocialApi {
    var showcase: ApiResponse<ShowcaseDto> = ApiResponse.Success(ShowcaseDto())
    var summary: ApiResponse<ReviewSummaryDto> = ApiResponse.Success(ReviewSummaryDto(4.5, 2, mapOf("4" to 1, "5" to 1)))
    val pages = mutableMapOf<Int, ApiResponse<PaginatedResponseDto<ReviewDto>>>()
    var mine: ApiResponse<MyReviewDto> = ApiResponse.Error("not found", 404)
    var createResponse: ApiResponse<MyReviewDto>? = null
    var updateResponse: ApiResponse<MyReviewDto>? = null
    var deleteResponse: ApiResponse<Unit> = ApiResponse.Success(Unit)
    var reportResponse: ApiResponse<Unit> = ApiResponse.Success(Unit)

    val writes = mutableListOf<ReviewWrite>()
    val reports = mutableListOf<ReviewReportCall>()
    val requestedPages = mutableListOf<Int>()

    override suspend fun showcase(businessId: Long) = showcase
    override suspend fun reviewSummary(businessId: Long) = summary

    override suspend fun reviews(businessId: Long, page: Int): ApiResponse<PaginatedResponseDto<ReviewDto>> {
        requestedPages += page
        return pages[page] ?: ApiResponse.Error("none", 404)
    }

    override suspend fun myReview(token: String, businessId: Long) = mine

    override suspend fun createReview(token: String, businessId: Long, rating: Int, text: String): ApiResponse<MyReviewDto> {
        writes += ReviewWrite("create", token, businessId, rating, text)
        return createResponse ?: ApiResponse.Success(MyReviewDto(9, businessId, rating, text))
    }

    override suspend fun updateReview(token: String, businessId: Long, rating: Int, text: String): ApiResponse<MyReviewDto> {
        writes += ReviewWrite("update", token, businessId, rating, text)
        return updateResponse ?: ApiResponse.Success(MyReviewDto(9, businessId, rating, text))
    }

    override suspend fun deleteReview(token: String, businessId: Long): ApiResponse<Unit> {
        writes += ReviewWrite("delete", token, businessId, 0, "")
        return deleteResponse
    }

    override suspend fun reportReview(token: String?, reviewId: Long, reason: String, detail: String): ApiResponse<Unit> {
        reports += ReviewReportCall(token, reviewId, reason, detail)
        return reportResponse
    }
}
