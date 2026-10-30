package xyz.sattar.javid.proqueue

import kotlinx.coroutines.test.runTest
import xyz.sattar.javid.proqueue.core.network.ApiResponse
import xyz.sattar.javid.proqueue.data.remoteDataSource.business.model.BusinessDto
import xyz.sattar.javid.proqueue.data.remoteDataSource.business.model.InsuranceDto
import xyz.sattar.javid.proqueue.data.remoteDataSource.business.toDomain
import xyz.sattar.javid.proqueue.data.repository.business.BusinessRepositoryImpl
import xyz.sattar.javid.proqueue.testsupport.FakeClientBusinessApi
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class RepositoryMappingTest {
    @Test
    fun dtoMapsToDomain() {
        val dto = BusinessDto(
            id = 4, title = "کلینیک", category = null, phone = null, address = null,
            createdAt = "2024-01-01T00:00:00Z", distanceKm = 1.5,
            insurances = listOf(InsuranceDto(1, "تامین", "tamin")),
        )
        val b = dto.toDomain()
        assertEquals(4, b.id)
        assertEquals("OTHER", b.category)
        assertEquals("", b.phone)
        assertEquals("", b.address)
        assertEquals(1_704_067_200_000L, b.createdAt)
        assertEquals(1.5, b.distanceKm)
        assertEquals(listOf("tamin"), b.insurances.map { it.slug })
    }

    @Test
    fun serverCategoryLabelWins() {
        val b = BusinessDto(id = 1, title = "t", category = "DENTAL", categoryDisplay = "دندان").toDomain()
        assertEquals("دندان", b.categoryLabel)
    }

    @Test
    fun detailReusesTheLastSearchCentre() = runTest {
        val api = FakeClientBusinessApi()
        val repo = BusinessRepositoryImpl(api)
        repo.getClientBusinesses(lat = 36.0, lng = 52.0)
        repo.getClientBusinessDetail(9)
        assertEquals(Triple(9L, 36.0, 52.0), api.detailArgs.single())
        // an explicit centre overrides it
        repo.getClientBusinessDetail(9, lat = 1.0, lng = 2.0)
        assertEquals(Triple(9L, 1.0, 2.0), api.detailArgs.last())
    }

    @Test
    fun repositoryPassesThroughApiResponses() = runTest {
        val repo = BusinessRepositoryImpl(FakeClientBusinessApi())
        assertTrue(repo.getClientBusinessDetail(2) is ApiResponse.Success)
        assertEquals(2L, (repo.requestOnlineBooking(2) as ApiResponse.Success).data.id)
    }
}
