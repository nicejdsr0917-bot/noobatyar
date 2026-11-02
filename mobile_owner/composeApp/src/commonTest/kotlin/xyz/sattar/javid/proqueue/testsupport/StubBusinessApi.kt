package xyz.sattar.javid.proqueue.testsupport

import xyz.sattar.javid.proqueue.core.network.ApiResponse
import xyz.sattar.javid.proqueue.core.network.PaginatedResponseDto
import xyz.sattar.javid.proqueue.data.remoteDataSource.business.BusinessApi
import xyz.sattar.javid.proqueue.data.remoteDataSource.business.model.*
import xyz.sattar.javid.proqueue.domain.model.business.Business

/** Every member throws; a test subclasses it and overrides what it exercises. */
open class StubBusinessApi : BusinessApi {
    override suspend fun getBusinesses(page: Int, pageSize: Int): ApiResponse<PaginatedResponseDto<BusinessDto>> = TODO("not stubbed")
    override suspend fun createBusiness(business: Business): ApiResponse<BusinessDto> = TODO("not stubbed")
    override suspend fun updateBusiness(id: Long, business: Business): ApiResponse<BusinessDto> = TODO("not stubbed")
    override suspend fun getSmsLogs(businessId: Long, page: Int, pageSize: Int, status: String?, search: String?, dateFrom: String?, dateTo: String?): ApiResponse<SmsLogPageDto> = TODO("not stubbed")
    override suspend fun getSmsLogSummary(businessId: Long): ApiResponse<SmsLogSummaryDto> = TODO("not stubbed")
    override suspend fun getPushLogs(businessId: Long, page: Int, pageSize: Int, status: String?, search: String?, dateFrom: String?, dateTo: String?): ApiResponse<PushLogPageDto> = TODO("not stubbed")
    override suspend fun getPushLogSummary(businessId: Long): ApiResponse<PushLogSummaryDto> = TODO("not stubbed")
    override suspend fun deleteBusiness(id: Long): ApiResponse<Unit> = TODO("not stubbed")
    override suspend fun getServiceCatalog(category: String): ApiResponse<List<ServiceCatalogItemDto>> = TODO("not stubbed")
    override suspend fun getPromoSms(businessId: Long): ApiResponse<PromoSmsOverviewDto> = TODO("not stubbed")
    override suspend fun previewPromoSms(businessId: Long, audience: String, message: String): ApiResponse<PromoPreviewDto> = TODO("not stubbed")
    override suspend fun sendPromoSms(businessId: Long, audience: String, message: String): ApiResponse<PromoCampaignDto> = TODO("not stubbed")
    override suspend fun getPromoCampaign(businessId: Long, campaignId: Long): ApiResponse<PromoCampaignDto> = TODO("not stubbed")
    override suspend fun addServiceCatalogItem(category: String, name: String): ApiResponse<ServiceCatalogItemDto> = TODO("not stubbed")
    override suspend fun getCategories(): ApiResponse<BusinessCategoriesDto> = TODO("not stubbed")
    override suspend fun searchClaimable(search: String, page: Int): ApiResponse<ClaimableSearchDto> = TODO("not stubbed")
    override suspend fun startClaim(businessId: Long, note: String): ApiResponse<ClaimStartResponseDto> = TODO("not stubbed")
    override suspend fun verifyClaim(businessId: Long, code: String): ApiResponse<ClaimVerifyResponseDto> = TODO("not stubbed")
}
