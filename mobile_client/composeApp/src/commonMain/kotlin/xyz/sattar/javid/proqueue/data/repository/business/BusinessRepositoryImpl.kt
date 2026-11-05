package xyz.sattar.javid.proqueue.data.repository.business

import xyz.sattar.javid.proqueue.core.network.ApiResponse
import xyz.sattar.javid.proqueue.core.network.PaginatedResponseDto
import xyz.sattar.javid.proqueue.data.remoteDataSource.business.ClientBusinessApi
import xyz.sattar.javid.proqueue.data.remoteDataSource.business.model.BusinessDto
import xyz.sattar.javid.proqueue.data.remoteDataSource.business.model.InsuranceDto
import xyz.sattar.javid.proqueue.data.remoteDataSource.business.model.FollowResponseDto
import xyz.sattar.javid.proqueue.data.remoteDataSource.business.model.RequestBookingResponseDto
import xyz.sattar.javid.proqueue.domain.BusinessRepository

class BusinessRepositoryImpl(
    private val api: ClientBusinessApi,
) : BusinessRepository {

    // The last search centre, so opening a detail page from a list can ask the
    // server for the distance to the same point the list was sorted by.
    private var lastSelectedLat: Double? = 35.7575
    private var lastSelectedLng: Double? = 51.4100

    override suspend fun getClientBusinesses(
        page: Int,
        pageSize: Int,
        search: String?,
        category: String?,
        insuranceSlug: String?,
        lat: Double?,
        lng: Double?,
        radiusKm: Double?,
        cityCode: Int?,
        onlyClaimed: Boolean,
        sort: String?
    ): ApiResponse<PaginatedResponseDto<BusinessDto>> {
        lastSelectedLat = lat
        lastSelectedLng = lng
        return api.getBusinesses(
            page = page,
            pageSize = pageSize,
            search = search,
            category = category,
            insuranceSlug = insuranceSlug,
            lat = lat,
            lng = lng,
            radiusKm = radiusKm,
            cityCode = cityCode,
            onlyClaimed = onlyClaimed,
            sort = sort
        )
    }

    override suspend fun getClientBusinessDetail(
        businessId: Long,
        lat: Double?,
        lng: Double?
    ): ApiResponse<BusinessDto> =
        api.getBusinessDetail(
            businessId = businessId,
            lat = lat ?: lastSelectedLat,
            lng = lng ?: lastSelectedLng
        )

    override suspend fun getInsurances(): ApiResponse<List<InsuranceDto>> = api.getInsurances()

    override suspend fun requestOnlineBooking(businessId: Long): ApiResponse<RequestBookingResponseDto> =
        api.requestOnlineBooking(businessId)

    override suspend fun setFollowing(token: String, businessId: Long, follow: Boolean): ApiResponse<FollowResponseDto> =
        api.setFollowing(token, businessId, follow)

    override suspend fun getFollowing(token: String, page: Int): ApiResponse<PaginatedResponseDto<BusinessDto>> =
        api.getFollowing(token, page)

    override suspend fun reportBusiness(businessId: Long, reason: String, detail: String): ApiResponse<Unit> =
        api.reportBusiness(businessId, reason, detail)
}
