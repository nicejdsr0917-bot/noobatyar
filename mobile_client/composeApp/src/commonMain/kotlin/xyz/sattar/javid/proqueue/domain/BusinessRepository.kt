package xyz.sattar.javid.proqueue.domain

import xyz.sattar.javid.proqueue.core.network.ApiResponse
import xyz.sattar.javid.proqueue.core.network.PaginatedResponseDto
import xyz.sattar.javid.proqueue.data.remoteDataSource.business.model.BusinessDto
import xyz.sattar.javid.proqueue.data.remoteDataSource.business.model.InsuranceDto
import xyz.sattar.javid.proqueue.data.remoteDataSource.business.model.RequestBookingResponseDto

interface BusinessRepository {
    suspend fun getClientBusinesses(
        page: Int = 1,
        pageSize: Int = 30,
        search: String? = null,
        category: String? = null,
        insuranceSlug: String? = null,
        lat: Double? = null,
        lng: Double? = null,
        radiusKm: Double? = null,
        /** Divar city id: businesses the owner put in that city. */
        cityCode: Int? = null,
        onlyClaimed: Boolean = false,
        sort: String? = null
    ): ApiResponse<PaginatedResponseDto<BusinessDto>>

    suspend fun getClientBusinessDetail(
        businessId: Long,
        lat: Double? = null,
        lng: Double? = null
    ): ApiResponse<BusinessDto>
    suspend fun getInsurances(): ApiResponse<List<InsuranceDto>>
    suspend fun requestOnlineBooking(businessId: Long): ApiResponse<RequestBookingResponseDto>
    suspend fun reportBusiness(businessId: Long, reason: String, detail: String): ApiResponse<Unit>
}
