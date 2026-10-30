package xyz.sattar.javid.proqueue.testsupport

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import xyz.sattar.javid.proqueue.core.location.CityCatalog
import xyz.sattar.javid.proqueue.core.location.IranCity
import xyz.sattar.javid.proqueue.core.location.IranProvince
import xyz.sattar.javid.proqueue.core.network.ApiResponse
import xyz.sattar.javid.proqueue.core.network.PaginatedResponseDto
import xyz.sattar.javid.proqueue.core.prefs.ClientPreferences
import xyz.sattar.javid.proqueue.core.prefs.withRecent
import xyz.sattar.javid.proqueue.core.prefs.withRecentCity
import xyz.sattar.javid.proqueue.data.remoteDataSource.business.ClientBusinessApi
import xyz.sattar.javid.proqueue.data.remoteDataSource.business.model.BusinessDto
import xyz.sattar.javid.proqueue.data.remoteDataSource.business.model.InsuranceDto
import xyz.sattar.javid.proqueue.data.remoteDataSource.business.model.RequestBookingResponseDto
import xyz.sattar.javid.proqueue.domain.BusinessRepository

fun businessDto(
    id: Long,
    title: String = "مرکز $id",
    claimed: Boolean = true,
    bookable: Boolean = true,
    firstFreeSlot: Long? = null,
) = BusinessDto(
    id = id, title = title, category = "DENTAL", isClaimed = claimed, bookingEnabled = bookable,
    firstFreeSlot = firstFreeSlot,
)

fun page(vararg items: BusinessDto, next: Int? = null) =
    ApiResponse.Success(PaginatedResponseDto(count = items.size, next = next, results = items.toList()))

class FakeClientPreferences(
    saved: Set<Long> = emptySet(),
    recents: List<String> = emptyList(),
    cities: List<Int> = emptyList(),
) : ClientPreferences {
    val saved = MutableStateFlow(saved)
    val recents = MutableStateFlow(recents)
    val cities = MutableStateFlow(cities)

    override val savedBusinessIds get() = saved
    override suspend fun toggleSavedBusiness(id: Long) =
        saved.update { if (id in it) it - id else it + id }

    override val recentSearches get() = recents
    override suspend fun addRecentSearch(query: String) = recents.update { it.withRecent(query) }
    override suspend fun clearRecentSearches() = recents.update { emptyList() }

    override val recentCityCodes get() = cities
    override suspend fun selectCity(code: Int) = cities.update { it.withRecentCity(code) }

    val token = MutableStateFlow<String?>(null)
    override val visitorToken get() = token
    override suspend fun setVisitorToken(token: String?) { this.token.value = token }
}

class FakeCityCatalog(private val cities: List<IranCity> = emptyList()) : CityCatalog {
    private val province = IranProvince(1, "استان", cities)
    override suspend fun provinces() = listOf(province)
    override suspend fun city(id: Int) = cities.firstOrNull { it.id == id }?.let { it to province }
}

class SearchCall(val search: String?, val cityCode: Int?, val category: String?, val onlyClaimed: Boolean, val page: Int)

class FakeBusinessRepository : BusinessRepository {
    val searches = mutableListOf<SearchCall>()
    var listResponse: (SearchCall) -> ApiResponse<PaginatedResponseDto<BusinessDto>> = { page() }
    var details = mutableMapOf<Long, ApiResponse<BusinessDto>>()
    var bookingResponse: ApiResponse<RequestBookingResponseDto> =
        ApiResponse.Success(RequestBookingResponseDto(1, 1))
    var detailCalls = mutableListOf<Long>()
    var reportResponse: ApiResponse<Unit> = ApiResponse.Success(Unit)
    /** When set, [reportBusiness] suspends until it completes — to observe the in-flight state. */
    var reportGate: kotlinx.coroutines.CompletableDeferred<Unit>? = null
    val reports = mutableListOf<Triple<Long, String, String>>()

    override suspend fun getClientBusinesses(
        page: Int, pageSize: Int, search: String?, category: String?, insuranceSlug: String?,
        lat: Double?, lng: Double?, radiusKm: Double?, cityCode: Int?, onlyClaimed: Boolean, sort: String?,
    ): ApiResponse<PaginatedResponseDto<BusinessDto>> {
        val call = SearchCall(search, cityCode, category, onlyClaimed, page)
        searches += call
        return listResponse(call)
    }

    override suspend fun getClientBusinessDetail(businessId: Long, lat: Double?, lng: Double?): ApiResponse<BusinessDto> {
        detailCalls += businessId
        return details[businessId] ?: ApiResponse.Error("not found", 404)
    }

    override suspend fun getInsurances(): ApiResponse<List<InsuranceDto>> = ApiResponse.Success(emptyList())
    override suspend fun requestOnlineBooking(businessId: Long) = bookingResponse
    override suspend fun reportBusiness(businessId: Long, reason: String, detail: String): ApiResponse<Unit> {
        reports += Triple(businessId, reason, detail)
        reportGate?.await()
        return reportResponse
    }
}

class FakeClientBusinessApi : ClientBusinessApi {
    val detailArgs = mutableListOf<Triple<Long, Double?, Double?>>()
    val listArgs = mutableListOf<Pair<Double?, Double?>>()

    override suspend fun getBusinesses(
        page: Int, pageSize: Int, search: String?, category: String?, insuranceSlug: String?,
        lat: Double?, lng: Double?, radiusKm: Double?, cityCode: Int?, onlyClaimed: Boolean, sort: String?,
    ): ApiResponse<PaginatedResponseDto<BusinessDto>> {
        listArgs += lat to lng
        return page()
    }

    override suspend fun getBusinessDetail(businessId: Long, lat: Double?, lng: Double?): ApiResponse<BusinessDto> {
        detailArgs += Triple(businessId, lat, lng)
        return ApiResponse.Success(businessDto(businessId))
    }

    override suspend fun getInsurances(): ApiResponse<List<InsuranceDto>> = ApiResponse.Success(emptyList())
    override suspend fun requestOnlineBooking(businessId: Long) = ApiResponse.Success(RequestBookingResponseDto(businessId, 1))
    override suspend fun reportBusiness(businessId: Long, reason: String, detail: String): ApiResponse<Unit> =
        ApiResponse.Success(Unit)
}
