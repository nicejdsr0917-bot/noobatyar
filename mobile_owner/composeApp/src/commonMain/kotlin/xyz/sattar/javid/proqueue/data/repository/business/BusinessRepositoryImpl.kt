package xyz.sattar.javid.proqueue.data.repository.business

import xyz.sattar.javid.proqueue.data.localDataSource.business.BusinessLocalSource
import xyz.sattar.javid.proqueue.data.remoteDataSource.business.model.toDomain
import xyz.sattar.javid.proqueue.domain.BusinessRepository
import xyz.sattar.javid.proqueue.domain.model.business.Business
import xyz.sattar.javid.proqueue.domain.model.business.CategoryGroup
import xyz.sattar.javid.proqueue.domain.model.business.CategoryOption
import xyz.sattar.javid.proqueue.domain.model.business.fallbackCategoryGroups

import xyz.sattar.javid.proqueue.data.remoteDataSource.business.BusinessApiService
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import xyz.sattar.javid.proqueue.core.network.ApiException
import xyz.sattar.javid.proqueue.core.network.ApiResponse
import xyz.sattar.javid.proqueue.data.remoteDataSource.business.model.ServiceCatalogItemDto
import xyz.sattar.javid.proqueue.data.remoteDataSource.business.model.PushLogPageDto
import xyz.sattar.javid.proqueue.data.remoteDataSource.business.model.PushLogSummaryDto
import xyz.sattar.javid.proqueue.data.remoteDataSource.business.model.SmsLogPageDto
import xyz.sattar.javid.proqueue.data.remoteDataSource.business.model.SmsLogSummaryDto
import xyz.sattar.javid.proqueue.data.remoteDataSource.business.model.PromoCampaignDto
import xyz.sattar.javid.proqueue.data.remoteDataSource.business.model.PromoPreviewDto
import xyz.sattar.javid.proqueue.data.remoteDataSource.business.model.PromoSmsOverviewDto

class BusinessRepositoryImpl(
    private val businessDao: BusinessLocalSource,
    private val businessApiService: BusinessApiService
) : BusinessRepository {
    override suspend fun upsertBusiness(business: Business): Boolean {
        return try {
            businessDao.upsertBusiness(business)
            true
        } catch (e: Exception) {
            false
        }
    }

    override suspend fun loadAllBusiness(): List<Business> {
        return try {
            businessDao.loadAllBusiness()
        } catch (e: Exception) {
            emptyList()
        }
    }

    override fun loadAllBusinessFlow(): Flow<List<Business>> {
        return businessDao.loadAllBusinessFlow()
    }

    override suspend fun fetchAndCacheBusinesses(page: Int, pageSize: Int): Boolean {
        return try {
            when (val response = businessApiService.getBusinesses(page, pageSize)) {
                is ApiResponse.Success -> {
                    if (page == 1) {
                        businessDao.clearAllBusinesses()
                    }
                    val businesses = response.data.results.map { it.toDomain() }
                    businessDao.upsertBusinesses(businesses)
                    true
                }
                is ApiResponse.Error -> false
            }
        } catch (e: Exception) {
            false
        }
    }

    override suspend fun getBusinessById(businessId: Long): Business? {
        return try {
            businessDao.getBusinessById(businessId)
        } catch (e: Exception) {
            null
        }
    }

    override suspend fun deleteBusiness(businessId: Long): Boolean {
        return try {
            when (val response = businessApiService.deleteBusiness(businessId)) {
                is ApiResponse.Success -> {
                    businessDao.deleteBusiness(businessId)
                    true
                }
                // ApiException, not a bare Exception: callers need the status
                // code to tell "your plan doesn't include this" (403) apart from
                // a generic failure.
                is ApiResponse.Error -> throw ApiException(response.message, response.code)
            }
        } catch (e: Exception) {
            throw e
        }
    }

    override suspend fun createBusiness(business: Business): Business? {
        return try {
            when (val response = businessApiService.createBusiness(business)) {
                is ApiResponse.Success -> {
                    val created = response.data.toDomain()
                    businessDao.upsertBusiness(created)
                    created
                }
                // ApiException, not a bare Exception: callers need the status
                // code to tell "your plan doesn't include this" (403) apart from
                // a generic failure.
                is ApiResponse.Error -> throw ApiException(response.message, response.code)
            }
        } catch (e: Exception) {
            throw e
        }
    }

    override suspend fun updateBusiness(business: Business): Business? {
        return try {
            when (val response = businessApiService.updateBusiness(business.id, business)) {
                is ApiResponse.Success -> {
                    val updated = response.data.toDomain()
                    businessDao.upsertBusiness(updated)
                    updated
                }
                // ApiException, not a bare Exception: callers need the status
                // code to tell "your plan doesn't include this" (403) apart from
                // a generic failure.
                is ApiResponse.Error -> throw ApiException(response.message, response.code)
            }
        } catch (e: Exception) {
            throw e
        }
    }

    // The SMS log is server-owned history — there is nothing sensible to cache
    // locally, so these pass straight through to the API.
    override suspend fun getSmsLogs(
        businessId: Long,
        page: Int,
        pageSize: Int,
        status: String?,
        search: String?,
        dateFrom: String?,
        dateTo: String?
    ): ApiResponse<SmsLogPageDto> =
        businessApiService.getSmsLogs(businessId, page, pageSize, status, search, dateFrom, dateTo)

    override suspend fun getSmsLogSummary(businessId: Long): ApiResponse<SmsLogSummaryDto> =
        businessApiService.getSmsLogSummary(businessId)

    override suspend fun getPushLogs(
        businessId: Long,
        page: Int,
        pageSize: Int,
        status: String?,
        search: String?,
        dateFrom: String?,
        dateTo: String?
    ): ApiResponse<PushLogPageDto> =
        businessApiService.getPushLogs(businessId, page, pageSize, status, search, dateFrom, dateTo)

    override suspend fun getPushLogSummary(businessId: Long): ApiResponse<PushLogSummaryDto> =
        businessApiService.getPushLogSummary(businessId)

    override suspend fun getPromoSms(businessId: Long): ApiResponse<PromoSmsOverviewDto> =
        businessApiService.getPromoSms(businessId)

    override suspend fun previewPromoSms(businessId: Long, audience: String, message: String): ApiResponse<PromoPreviewDto> =
        businessApiService.previewPromoSms(businessId, audience, message)

    override suspend fun sendPromoSms(businessId: Long, audience: String, message: String): ApiResponse<PromoCampaignDto> =
        businessApiService.sendPromoSms(businessId, audience, message)

    override suspend fun getPromoCampaign(businessId: Long, campaignId: Long): ApiResponse<PromoCampaignDto> =
        businessApiService.getPromoCampaign(businessId, campaignId)

    // Server-owned and shared across businesses — nothing to cache locally,
    // same reasoning as the SMS log above.
    override suspend fun getServiceCatalog(category: String): ApiResponse<List<ServiceCatalogItemDto>> =
        businessApiService.getServiceCatalog(category)

    override suspend fun addServiceCatalogItem(category: String, name: String): ApiResponse<ServiceCatalogItemDto> =
        businessApiService.addServiceCatalogItem(category, name)

    /**
     * Cached for the process lifetime: the vocabulary changes on the order of
     * months, and the picker would otherwise re-fetch every time it opens.
     * A failed fetch is not cached, so the next open retries rather than
     * pinning the user to the fallback for the whole session.
     */
    private var cachedCategoryGroups: List<CategoryGroup>? = null

    override suspend fun getCategoryGroups(): List<CategoryGroup> {
        cachedCategoryGroups?.let { return it }

        val remote = runCatching {
            when (val response = businessApiService.getCategories()) {
                is ApiResponse.Success -> response.data.groups
                    .map { group ->
                        CategoryGroup(
                            key = group.key,
                            label = group.label,
                            categories = group.categories.map { CategoryOption(it.value, it.label) },
                        )
                    }
                    .filter { it.categories.isNotEmpty() }
                else -> emptyList()
            }
        }.getOrDefault(emptyList())

        // Falling back on an empty result, not just on a thrown error: an old
        // server without this endpoint answers 404, which surfaces here as an
        // empty list rather than an exception, and an empty picker is the one
        // outcome that leaves the owner unable to finish creating a business.
        val groups = remote.ifEmpty { fallbackCategoryGroups() }
        if (remote.isNotEmpty()) cachedCategoryGroups = groups
        return groups
    }
}