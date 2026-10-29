package xyz.sattar.javid.proqueue.data.remoteDataSource.business

import io.ktor.client.HttpClient
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.client.request.post
import io.ktor.client.request.put
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType
import xyz.sattar.javid.proqueue.core.network.ApiResponse
import xyz.sattar.javid.proqueue.core.network.PaginatedResponseDto
import xyz.sattar.javid.proqueue.core.network.toApiResponse
import xyz.sattar.javid.proqueue.core.network.toDirectApiResponse
import xyz.sattar.javid.proqueue.data.remoteDataSource.business.model.BusinessCategoriesDto
import xyz.sattar.javid.proqueue.data.remoteDataSource.business.model.BusinessDto
import xyz.sattar.javid.proqueue.data.remoteDataSource.business.model.ClaimStartRequestDto
import xyz.sattar.javid.proqueue.data.remoteDataSource.business.model.ClaimStartResponseDto
import xyz.sattar.javid.proqueue.data.remoteDataSource.business.model.ClaimVerifyRequestDto
import xyz.sattar.javid.proqueue.data.remoteDataSource.business.model.ClaimVerifyResponseDto
import xyz.sattar.javid.proqueue.data.remoteDataSource.business.model.ClaimableSearchDto
import xyz.sattar.javid.proqueue.data.remoteDataSource.business.model.CreateBusinessRequestDto
import xyz.sattar.javid.proqueue.data.remoteDataSource.business.model.AddServiceCatalogItemRequestDto
import xyz.sattar.javid.proqueue.data.remoteDataSource.business.model.ServiceCatalogItemDto
import xyz.sattar.javid.proqueue.data.remoteDataSource.business.model.PushLogPageDto
import xyz.sattar.javid.proqueue.data.remoteDataSource.business.model.PushLogSummaryDto
import xyz.sattar.javid.proqueue.data.remoteDataSource.business.model.SmsLogPageDto
import xyz.sattar.javid.proqueue.data.remoteDataSource.business.model.SmsLogSummaryDto
import xyz.sattar.javid.proqueue.data.remoteDataSource.business.model.PromoCampaignDto
import xyz.sattar.javid.proqueue.data.remoteDataSource.business.model.PromoPreviewDto
import xyz.sattar.javid.proqueue.data.remoteDataSource.business.model.PromoSmsOverviewDto
import xyz.sattar.javid.proqueue.data.remoteDataSource.business.model.PromoSmsRequestDto
import xyz.sattar.javid.proqueue.domain.model.business.Business

/**
 * Contract the repositories depend on; [BusinessApiService] is the Ktor
 * implementation, tests substitute a fake.
 */
interface BusinessApi {
    suspend fun getBusinesses(page: Int, pageSize: Int): ApiResponse<PaginatedResponseDto<BusinessDto>>

    suspend fun createBusiness(business: Business): ApiResponse<BusinessDto>

    suspend fun updateBusiness(id: Long, business: Business): ApiResponse<BusinessDto>

    suspend fun getSmsLogs(
        businessId: Long,
        page: Int,
        pageSize: Int,
        status: String? = null,
        search: String? = null,
        dateFrom: String? = null,
        dateTo: String? = null
    ): ApiResponse<SmsLogPageDto>

    suspend fun getSmsLogSummary(businessId: Long): ApiResponse<SmsLogSummaryDto>

    suspend fun getPushLogs(
        businessId: Long,
        page: Int,
        pageSize: Int,
        status: String? = null,
        search: String? = null,
        dateFrom: String? = null,
        dateTo: String? = null
    ): ApiResponse<PushLogPageDto>

    suspend fun getPushLogSummary(businessId: Long): ApiResponse<PushLogSummaryDto>

    suspend fun deleteBusiness(id: Long): ApiResponse<Unit>

    suspend fun getServiceCatalog(category: String): ApiResponse<List<ServiceCatalogItemDto>>

    suspend fun getPromoSms(businessId: Long): ApiResponse<PromoSmsOverviewDto>

    suspend fun previewPromoSms(businessId: Long, audience: String, message: String): ApiResponse<PromoPreviewDto>

    suspend fun sendPromoSms(businessId: Long, audience: String, message: String): ApiResponse<PromoCampaignDto>

    suspend fun getPromoCampaign(businessId: Long, campaignId: Long): ApiResponse<PromoCampaignDto>

    suspend fun addServiceCatalogItem(category: String, name: String): ApiResponse<ServiceCatalogItemDto>

    suspend fun getCategories(): ApiResponse<BusinessCategoriesDto>

    suspend fun searchClaimable(search: String, page: Int = 1): ApiResponse<ClaimableSearchDto>

    suspend fun startClaim(businessId: Long, note: String): ApiResponse<ClaimStartResponseDto>

    suspend fun verifyClaim(businessId: Long, code: String): ApiResponse<ClaimVerifyResponseDto>
}
