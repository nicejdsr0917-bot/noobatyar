package xyz.sattar.javid.proqueue.data.remoteDataSource.social

import io.ktor.client.HttpClient
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import io.ktor.client.request.post
import io.ktor.client.request.put
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.contentType
import xyz.sattar.javid.proqueue.core.network.ApiResponse
import xyz.sattar.javid.proqueue.core.network.PaginatedResponseDto
import xyz.sattar.javid.proqueue.core.network.safeApiCall
import xyz.sattar.javid.proqueue.core.network.toApiResponse
import xyz.sattar.javid.proqueue.data.remoteDataSource.social.model.MyReviewDto
import xyz.sattar.javid.proqueue.data.remoteDataSource.social.model.PendingReviewDto
import xyz.sattar.javid.proqueue.data.remoteDataSource.social.model.ReviewDto
import xyz.sattar.javid.proqueue.data.remoteDataSource.social.model.ReviewReportRequestDto
import xyz.sattar.javid.proqueue.data.remoteDataSource.social.model.ReviewRequestDto
import xyz.sattar.javid.proqueue.data.remoteDataSource.social.model.ReviewSummaryDto
import xyz.sattar.javid.proqueue.data.remoteDataSource.social.model.ShowcaseDto

/**
 * Public showcase (portfolio, certificates) and reviews of a business. An
 * interface so [xyz.sattar.javid.proqueue.feature.businessDetail.BusinessSocialViewModel]
 * can be tested against a fake. Calls that act as the visitor take the token.
 */
interface BusinessSocialApi {
    suspend fun showcase(businessId: Long): ApiResponse<ShowcaseDto>
    suspend fun reviewSummary(businessId: Long): ApiResponse<ReviewSummaryDto>
    suspend fun reviews(businessId: Long, page: Int): ApiResponse<PaginatedResponseDto<ReviewDto>>

    /** 404 means the visitor has not reviewed this business yet. */
    suspend fun myReview(token: String, businessId: Long): ApiResponse<MyReviewDto>
    suspend fun createReview(token: String, businessId: Long, rating: Int, text: String): ApiResponse<MyReviewDto>
    suspend fun updateReview(token: String, businessId: Long, rating: Int, text: String): ApiResponse<MyReviewDto>
    suspend fun deleteReview(token: String, businessId: Long): ApiResponse<Unit>

    /** [token] is optional: the server accepts anonymous reports. */
    suspend fun reportReview(token: String?, reviewId: Long, reason: String, detail: String): ApiResponse<Unit>

    /** Finished visits the visitor has not rated yet; a fake need not override it. */
    suspend fun pendingReviews(token: String): ApiResponse<List<PendingReviewDto>> = ApiResponse.Success(emptyList())
}

class KtorBusinessSocialApi(private val httpClient: HttpClient) : BusinessSocialApi {

    override suspend fun showcase(businessId: Long): ApiResponse<ShowcaseDto> = safeApiCall {
        httpClient.get("client/businesses/$businessId/showcase/").toApiResponse()
    }

    override suspend fun reviewSummary(businessId: Long): ApiResponse<ReviewSummaryDto> = safeApiCall {
        httpClient.get("client/reviews/business/$businessId/summary/").toApiResponse()
    }

    override suspend fun reviews(businessId: Long, page: Int): ApiResponse<PaginatedResponseDto<ReviewDto>> = safeApiCall {
        httpClient.get("client/reviews/business/$businessId/") {
            parameter("page", page)
            parameter("page_size", REVIEWS_PAGE_SIZE)
        }.toApiResponse()
    }

    override suspend fun myReview(token: String, businessId: Long): ApiResponse<MyReviewDto> = safeApiCall {
        httpClient.get("client/reviews/business/$businessId/mine/") {
            header(HttpHeaders.Authorization, "Visitor $token")
        }.toApiResponse()
    }

    override suspend fun createReview(token: String, businessId: Long, rating: Int, text: String): ApiResponse<MyReviewDto> =
        safeApiCall {
            httpClient.post("client/reviews/business/$businessId/mine/") {
                header(HttpHeaders.Authorization, "Visitor $token")
                contentType(ContentType.Application.Json)
                setBody(ReviewRequestDto(rating, text))
            }.toApiResponse()
        }

    override suspend fun updateReview(token: String, businessId: Long, rating: Int, text: String): ApiResponse<MyReviewDto> =
        safeApiCall {
            httpClient.put("client/reviews/business/$businessId/mine/") {
                header(HttpHeaders.Authorization, "Visitor $token")
                contentType(ContentType.Application.Json)
                setBody(ReviewRequestDto(rating, text))
            }.toApiResponse()
        }

    override suspend fun deleteReview(token: String, businessId: Long): ApiResponse<Unit> = safeApiCall {
        val response = httpClient.delete("client/reviews/business/$businessId/mine/") {
            header(HttpHeaders.Authorization, "Visitor $token")
        }
        // A successful delete may have no body at all, so only errors are parsed.
        if (response.status.value >= 400) response.toApiResponse<Unit>() else ApiResponse.Success(Unit)
    }

    override suspend fun reportReview(token: String?, reviewId: Long, reason: String, detail: String): ApiResponse<Unit> =
        safeApiCall {
            httpClient.post("client/reviews/$reviewId/report/") {
                if (token != null) header(HttpHeaders.Authorization, "Visitor $token")
                contentType(ContentType.Application.Json)
                setBody(ReviewReportRequestDto(reason, detail))
            }.toApiResponse()
        }

    override suspend fun pendingReviews(token: String): ApiResponse<List<PendingReviewDto>> = safeApiCall {
        httpClient.get("client/reviews/pending/") {
            header(HttpHeaders.Authorization, "Visitor $token")
        }.toApiResponse()
    }

    private companion object {
        const val REVIEWS_PAGE_SIZE = 10
    }
}
