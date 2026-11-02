package xyz.sattar.javid.proqueue.data

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import xyz.sattar.javid.proqueue.core.network.ApiResponse
import xyz.sattar.javid.proqueue.core.network.PaginatedResponseDto
import xyz.sattar.javid.proqueue.data.remoteDataSource.business.model.PushLogPageDto
import xyz.sattar.javid.proqueue.data.remoteDataSource.business.model.SmsLogPageDto
import xyz.sattar.javid.proqueue.data.remoteDataSource.visitor.model.MessageDto
import xyz.sattar.javid.proqueue.data.remoteDataSource.visitor.model.VisitorDto
import xyz.sattar.javid.proqueue.data.repository.business.BusinessRepositoryImpl
import xyz.sattar.javid.proqueue.data.repository.message.MessageRepositoryImpl
import xyz.sattar.javid.proqueue.data.repository.visitor.VisitorRepositoryImpl
import xyz.sattar.javid.proqueue.domain.model.visitor.Visitor
import xyz.sattar.javid.proqueue.testsupport.FakeBusinessLocalSource
import xyz.sattar.javid.proqueue.testsupport.FakeMessageLocalSource
import xyz.sattar.javid.proqueue.testsupport.FakeVisitorLocalSource
import xyz.sattar.javid.proqueue.testsupport.StubBusinessApi
import xyz.sattar.javid.proqueue.testsupport.StubVisitorApi
import xyz.sattar.javid.proqueue.testsupport.testBusiness
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class BusinessAndVisitorRepositoryTest {

    // ---- Business: every business-scoped call carries the id it was given ----

    private class RecordingBusinessApi : StubBusinessApi() {
        val ids = mutableListOf<Pair<String, Long>>()
        private val err = ApiResponse.Error("x", 500)
        override suspend fun getSmsLogs(businessId: Long, page: Int, pageSize: Int, status: String?, search: String?, dateFrom: String?, dateTo: String?): ApiResponse<SmsLogPageDto> {
            ids += "smsLogs" to businessId; return err
        }
        override suspend fun getSmsLogSummary(businessId: Long) = err.also { ids += "smsSummary" to businessId }
        override suspend fun getPushLogs(businessId: Long, page: Int, pageSize: Int, status: String?, search: String?, dateFrom: String?, dateTo: String?): ApiResponse<PushLogPageDto> {
            ids += "pushLogs" to businessId; return err
        }
        override suspend fun getPushLogSummary(businessId: Long) = err.also { ids += "pushSummary" to businessId }
        override suspend fun getPromoSms(businessId: Long) = err.also { ids += "promo" to businessId }
        override suspend fun previewPromoSms(businessId: Long, audience: String, message: String) = err.also { ids += "preview" to businessId }
        override suspend fun sendPromoSms(businessId: Long, audience: String, message: String) = err.also { ids += "send" to businessId }
        override suspend fun getPromoCampaign(businessId: Long, campaignId: Long) = err.also { ids += "campaign" to businessId }
    }

    @Test
    fun businessScopedCallsAlwaysPassTheBusinessId() = runTest {
        val api = RecordingBusinessApi()
        val repo = BusinessRepositoryImpl(FakeBusinessLocalSource(), api)

        repo.getSmsLogs(41, 1, 20, null, null, null, null)
        repo.getSmsLogSummary(42)
        repo.getPushLogs(43, 1, 20, null, null, null, null)
        repo.getPushLogSummary(44)
        repo.getPromoSms(45)
        repo.previewPromoSms(46, "ALL", "hi")
        repo.sendPromoSms(47, "ALL", "hi")
        repo.getPromoCampaign(48, 9)

        assertEquals(
            listOf("smsLogs" to 41L, "smsSummary" to 42L, "pushLogs" to 43L, "pushSummary" to 44L,
                "promo" to 45L, "preview" to 46L, "send" to 47L, "campaign" to 48L),
            api.ids
        )
    }

    @Test
    fun fetchFirstPageReplacesTheCachedBusinessesLaterPagesDoNot() = runTest {
        val local = FakeBusinessLocalSource()
        local.rows[99] = testBusiness(99)
        val api = object : StubBusinessApi() {
            override suspend fun getBusinesses(page: Int, pageSize: Int) =
                ApiResponse.Success(PaginatedResponseDto<xyz.sattar.javid.proqueue.data.remoteDataSource.business.model.BusinessDto>(count = 0, results = emptyList()))
        }
        // a stub-source subclass records upserts; the base fake only tracks clears
        val repo = BusinessRepositoryImpl(object : xyz.sattar.javid.proqueue.testsupport.StubBusinessLocalSource() {
            override suspend fun clearAllBusinesses() { local.clearCalls++ }
            override suspend fun upsertBusinesses(businesses: List<xyz.sattar.javid.proqueue.domain.model.business.Business>) {}
        }, api)

        assertTrue(repo.fetchAndCacheBusinesses(1, 20))
        assertEquals(1, local.clearCalls)
        assertTrue(repo.fetchAndCacheBusinesses(2, 20))
        assertEquals(1, local.clearCalls, "page 2 must not wipe page 1")
    }

    @Test
    fun aFailingStoreReadFallsBackButCancellationStillPropagates() = runTest {
        val failing = object : xyz.sattar.javid.proqueue.testsupport.StubBusinessLocalSource() {
            override suspend fun loadAllBusiness(): List<xyz.sattar.javid.proqueue.domain.model.business.Business> = throw IllegalStateException("disk")
            override suspend fun getBusinessById(businessId: Long): xyz.sattar.javid.proqueue.domain.model.business.Business? = throw CancellationException("cancelled")
        }
        val repo = BusinessRepositoryImpl(failing, StubBusinessApi())

        assertEquals(emptyList(), repo.loadAllBusiness())
        assertFailsWith<CancellationException> { repo.getBusinessById(1) }
    }

    // ---- Message/Visitor: the visitor-messages call is scoped to the business ----

    @Test
    fun remoteMessagesAreRequestedForTheGivenVisitorAndBusiness() = runTest {
        var asked: Pair<Long, Long>? = null
        val api = object : StubVisitorApi() {
            override suspend fun getVisitorMessages(visitorId: Long, businessId: Long, page: Int, pageSize: Int):
                ApiResponse<PaginatedResponseDto<MessageDto>> {
                asked = visitorId to businessId
                return ApiResponse.Success(
                    PaginatedResponseDto(1, results = listOf(MessageDto(1, visitorId, businessId, "hello", "SENT", "2026-01-01T10:00:00Z")))
                )
            }
        }
        val repo = MessageRepositoryImpl(FakeMessageLocalSource(), api)

        val result = repo.getRemoteMessages(visitorId = 7, businessId = 3, businessTitle = "Shop")

        assertEquals(7L to 3L, asked)
        assertEquals(listOf("hello"), result.map { it.content })
        assertEquals("Shop", result.single().businessTitle)
        assertTrue(result.single().remote)
    }

    // ---- Visitor repository ----

    private fun dto(id: Long) = VisitorDto(id, "v$id", "09$id")

    @Test
    fun firstUnfilteredPageReplacesTheLocalVisitorsFilteredOrLaterPagesDoNot() = runTest {
        val local = FakeVisitorLocalSource()
        local.rows[1] = Visitor(1, "stale", "0", 0)
        val api = object : StubVisitorApi() {
            override suspend fun getVisitors(page: Int, pageSize: Int, query: String?) =
                ApiResponse.Success(PaginatedResponseDto(count = 1, results = listOf(dto(2))))
        }
        val repo = VisitorRepositoryImpl(object : xyz.sattar.javid.proqueue.testsupport.StubVisitorLocalSource() {
            override suspend fun clearAllVisitors() { local.clearCalls++ }
            override suspend fun upsertVisitors(visitors: List<Visitor>) { visitors.forEach { local.rows[it.id] = it } }
            override suspend fun getVisitors(limit: Int, offset: Int, query: String?) = local.rows.values.toList()
        }, api)

        repo.getVisitors(1, 20, null)
        assertEquals(1, local.clearCalls)
        repo.getVisitors(1, 20, "ali")
        repo.getVisitors(2, 20, null)
        assertEquals(1, local.clearCalls, "searches and later pages never wipe the cache")
    }

    @Test
    fun whenTheNetworkIsDownTheLocalPageIsServed() = runTest {
        val api = object : StubVisitorApi() {
            override suspend fun getVisitors(page: Int, pageSize: Int, query: String?): ApiResponse<PaginatedResponseDto<VisitorDto>> =
                throw IllegalStateException("offline")
        }
        val repo = VisitorRepositoryImpl(object : xyz.sattar.javid.proqueue.testsupport.StubVisitorLocalSource() {
            override suspend fun getVisitors(limit: Int, offset: Int, query: String?) = listOf(Visitor(1, "cached", "0", 0))
        }, api)

        val page = repo.getVisitors(1, 20, null)

        assertEquals(listOf("cached"), page.visitors.map { it.fullName })
        assertFalse(page.hasMore)
    }

    @Test
    fun getVisitorByIdFallsBackToLocalOnErrorButNotOnCancellation() = runTest {
        val local = object : xyz.sattar.javid.proqueue.testsupport.StubVisitorLocalSource() {
            override suspend fun getVisitorById(visitorId: Long) = Visitor(visitorId, "local", "0", 0)
        }
        val errApi = object : StubVisitorApi() {
            override suspend fun getVisitorById(id: Long): ApiResponse<VisitorDto> = ApiResponse.Error("404", 404)
        }
        assertEquals("local", VisitorRepositoryImpl(local, errApi).getVisitorById(5)?.fullName)

        val cancelApi = object : StubVisitorApi() {
            override suspend fun getVisitorById(id: Long): ApiResponse<VisitorDto> = throw CancellationException("gone")
        }
        assertFailsWith<CancellationException> { VisitorRepositoryImpl(local, cancelApi).getVisitorById(5) }
    }
}
