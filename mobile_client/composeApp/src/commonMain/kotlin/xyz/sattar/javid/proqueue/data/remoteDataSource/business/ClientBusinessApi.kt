package xyz.sattar.javid.proqueue.data.remoteDataSource.business

import xyz.sattar.javid.proqueue.core.network.ApiResponse
import xyz.sattar.javid.proqueue.core.network.PaginatedResponseDto
import xyz.sattar.javid.proqueue.data.remoteDataSource.business.model.BusinessDto
import xyz.sattar.javid.proqueue.data.remoteDataSource.business.model.InsuranceDto
import xyz.sattar.javid.proqueue.data.remoteDataSource.business.model.RequestBookingResponseDto

/**
 * The public `client/businesses/...` endpoints the consumer app talks to.
 * An interface so repositories (and, through them, view-models) can be tested
 * against a fake instead of a live HTTP client.
 */
interface ClientBusinessApi {

    suspend fun getBusinesses(
        page: Int = 1,
        pageSize: Int = 30,
        search: String? = null,
        category: String? = null,
        insuranceSlug: String? = null,
        lat: Double? = null,
        lng: Double? = null,
        radiusKm: Double? = null,
        cityCode: Int? = null,
        onlyClaimed: Boolean = false,
        sort: String? = null,
    ): ApiResponse<PaginatedResponseDto<BusinessDto>>

    suspend fun getBusinessDetail(
        businessId: Long,
        lat: Double? = null,
        lng: Double? = null,
    ): ApiResponse<BusinessDto>

    suspend fun getInsurances(): ApiResponse<List<InsuranceDto>>

    suspend fun requestOnlineBooking(businessId: Long): ApiResponse<RequestBookingResponseDto>

    suspend fun reportBusiness(businessId: Long, reason: String, detail: String): ApiResponse<Unit>
}
