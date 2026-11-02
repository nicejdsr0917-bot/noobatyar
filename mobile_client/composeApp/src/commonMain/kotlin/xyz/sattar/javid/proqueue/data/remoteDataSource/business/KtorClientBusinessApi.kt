package xyz.sattar.javid.proqueue.data.remoteDataSource.business

import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.contentType
import xyz.sattar.javid.proqueue.core.network.ApiResponse
import xyz.sattar.javid.proqueue.core.network.PaginatedResponseDto
import xyz.sattar.javid.proqueue.core.network.safeApiCall
import xyz.sattar.javid.proqueue.core.network.toApiResponse
import xyz.sattar.javid.proqueue.core.prefs.PreferencesManager
import xyz.sattar.javid.proqueue.data.remoteDataSource.business.model.BusinessDto
import xyz.sattar.javid.proqueue.data.remoteDataSource.business.model.ContentReportRequestDto
import xyz.sattar.javid.proqueue.data.remoteDataSource.business.model.InsuranceDto
import xyz.sattar.javid.proqueue.data.remoteDataSource.business.model.RequestBookingResponseDto

class KtorClientBusinessApi(
    private val httpClient: HttpClient,
    private val deviceId: () -> String = { PreferencesManager.deviceId() },
    private val visitorToken: () -> String? = { PreferencesManager.getVisitorToken() },
    private val signOut: suspend () -> Unit = { PreferencesManager.setVisitorToken(null) },
) : ClientBusinessApi {

    /**
     * Runs a public read as the signed-in visitor, if there is one: the owner's
     * "hide my phone and address from guests" switch is lifted for a customer
     * who has signed in. A token the server no longer accepts would 401 a page
     * anyone may read, so it is dropped and the read repeated as a guest.
     */
    private suspend fun <T> asCustomer(call: suspend (token: String?) -> ApiResponse<T>): ApiResponse<T> {
        val token = visitorToken()
        val response = call(token)
        if (token != null && response is ApiResponse.Error && response.code == 401) {
            signOut()
            return call(null)
        }
        return response
    }

    override suspend fun getBusinesses(
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
        sort: String?,
    ): ApiResponse<PaginatedResponseDto<BusinessDto>> = asCustomer { token -> safeApiCall {
        httpClient.get("client/businesses/") {
            contentType(ContentType.Application.Json)
            if (token != null) header(HttpHeaders.Authorization, "Visitor $token")
            parameter("page", page)
            parameter("page_size", pageSize)
            if (!search.isNullOrBlank()) parameter("search", search)
            if (!category.isNullOrBlank()) parameter("category", category)
            if (!insuranceSlug.isNullOrBlank()) parameter("insurance", insuranceSlug)
            if (lat != null) parameter("lat", lat)
            if (lng != null) parameter("lng", lng)
            if (radiusKm != null) parameter("radius_km", radiusKm)
            if (cityCode != null) parameter("city_code", cityCode)
            if (onlyClaimed) parameter("only_claimed", true)
            if (!sort.isNullOrBlank()) parameter("sort", sort)
        }.toApiResponse()
    } }

    override suspend fun getBusinessDetail(
        businessId: Long,
        lat: Double?,
        lng: Double?,
    ): ApiResponse<BusinessDto> = asCustomer { token -> safeApiCall {
        httpClient.get("client/businesses/$businessId/") {
            contentType(ContentType.Application.Json)
            if (token != null) header(HttpHeaders.Authorization, "Visitor $token")
            if (lat != null) parameter("lat", lat)
            if (lng != null) parameter("lng", lng)
        }.toApiResponse()
    } }

    override suspend fun getInsurances(): ApiResponse<List<InsuranceDto>> = safeApiCall {
        httpClient.get("client/businesses/insurances/") {
            contentType(ContentType.Application.Json)
        }.toApiResponse()
    }

    override suspend fun requestOnlineBooking(businessId: Long): ApiResponse<RequestBookingResponseDto> = safeApiCall {
        httpClient.post("client/businesses/$businessId/request-booking/") {
            contentType(ContentType.Application.Json)
            // One vote per install: the server de-duplicates on a hash of this.
            header("X-Device-Id", deviceId())
        }.toApiResponse()
    }

    override suspend fun reportBusiness(businessId: Long, reason: String, detail: String): ApiResponse<Unit> = safeApiCall {
        httpClient.post("client/businesses/$businessId/report/") {
            contentType(ContentType.Application.Json)
            setBody(ContentReportRequestDto(reason = reason, detail = detail))
        }.toApiResponse()
    }
}
