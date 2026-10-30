package xyz.sattar.javid.proqueue.feature

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import xyz.sattar.javid.proqueue.core.network.ApiResponse
import xyz.sattar.javid.proqueue.domain.model.appointment.AppointmentOrdering
import xyz.sattar.javid.proqueue.domain.model.appointment.AppointmentWithDetails
import xyz.sattar.javid.proqueue.core.utils.DateTimeUtils
import xyz.sattar.javid.proqueue.data.remoteDataSource.business.model.DailyCountDto
import xyz.sattar.javid.proqueue.domain.usecase.GetDailyCountsUseCase
import xyz.sattar.javid.proqueue.domain.usecase.GetSourceSummaryUseCase
import xyz.sattar.javid.proqueue.domain.usecase.GetTodayStatsUseCase
import xyz.sattar.javid.proqueue.domain.usecase.GetWaitingQueueUseCase
import xyz.sattar.javid.proqueue.domain.usecase.MarkAppointmentCompletedUseCase
import xyz.sattar.javid.proqueue.domain.usecase.MarkAppointmentNoShowUseCase
import xyz.sattar.javid.proqueue.domain.usecase.RemoveAppointmentUseCase
import xyz.sattar.javid.proqueue.domain.usecase.SendMessageUseCase
import xyz.sattar.javid.proqueue.domain.usecase.SyncAppointmentsUseCase
import xyz.sattar.javid.proqueue.domain.usecase.user.BuyAddonUseCase
import xyz.sattar.javid.proqueue.domain.usecase.user.CreatePaymentUseCase
import xyz.sattar.javid.proqueue.domain.usecase.user.GetMyEntitlementsUseCase
import xyz.sattar.javid.proqueue.domain.usecase.user.GetMySubscriptionUseCase
import xyz.sattar.javid.proqueue.domain.usecase.user.GetPlansUseCase
import xyz.sattar.javid.proqueue.domain.usecase.user.PurchaseUseCase
import xyz.sattar.javid.proqueue.feature.home.HomeIntent
import xyz.sattar.javid.proqueue.feature.home.HomeViewModel
import xyz.sattar.javid.proqueue.feature.home.SourceSummary
import xyz.sattar.javid.proqueue.testsupport.FakeBusinessStore
import xyz.sattar.javid.proqueue.testsupport.MainDispatcherSetup
import xyz.sattar.javid.proqueue.testsupport.StubAppointmentRepository
import xyz.sattar.javid.proqueue.testsupport.StubMessageRepository
import xyz.sattar.javid.proqueue.testsupport.StubUserRepository
import xyz.sattar.javid.proqueue.testsupport.testAppointmentWithDetails
import xyz.sattar.javid.proqueue.testsupport.testBusiness
import xyz.sattar.javid.proqueue.testsupport.testVisitor
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class HomeViewModelTest {
    private val main = MainDispatcherSetup()

    @BeforeTest fun setUp() = main.setUp()
    @AfterTest fun tearDown() = main.tearDown()

    private val shopA = testBusiness(1, "A")
    private val shopB = testBusiness(2, "B")

    private val queues = mutableMapOf<Long, List<AppointmentWithDetails>>()
    private val queueGates = mutableMapOf<Long, CompletableDeferred<Unit>>()
    val syncedFor = mutableListOf<Long>()
    val syncedReplace = mutableListOf<Boolean>()
    private var dailyWindow = emptyList<DailyCountDto>()
    private var sourceCounts: Map<String, Int>? = null
    private val sourceRequests = mutableListOf<Triple<Long, Long, Long>>()

    private val appointments = object : StubAppointmentRepository() {
        override suspend fun syncAppointments(
            businessId: Long, visitorId: Long?, status: String?, date: Long?, dateFrom: Long?, dateTo: Long?,
            ordering: AppointmentOrdering?, page: Int, pageSize: Int, replaceWindow: Boolean,
        ): Boolean {
            syncedFor += businessId; syncedReplace += replaceWindow; return true
        }
        override suspend fun getWaitingQueue(businessId: Long, date: Long): List<AppointmentWithDetails> {
            queueGates[businessId]?.await()
            return queues[businessId].orEmpty()
        }
        override suspend fun getTodayAppointments(businessId: Long) = emptyList<AppointmentWithDetails>()
        override suspend fun getDailyCounts(businessId: Long, days: Int, daysAhead: Int) = dailyWindow
        override suspend fun getSourceCounts(businessId: Long, dateFrom: Long, dateTo: Long): Map<String, Int>? {
            sourceRequests += Triple(businessId, dateFrom, dateTo)
            return sourceCounts
        }
        override suspend fun updateAppointmentStatus(appointmentId: Long, status: String): Boolean = true
    }

    private val users = object : StubUserRepository() {
        override suspend fun getPlans() = ApiResponse.Success(emptyList<xyz.sattar.javid.proqueue.data.remoteDataSource.user.model.PlanDto>())
        override suspend fun getMySubscription() = ApiResponse.Error("none", 404)
        override suspend fun getMyEntitlements() = ApiResponse.Error("none", 404)
    }

    private fun viewModel(store: FakeBusinessStore) = HomeViewModel(
        getWaitingQueueUseCase = GetWaitingQueueUseCase(appointments),
        getTodayStatsUseCase = GetTodayStatsUseCase(appointments),
        removeAppointmentUseCase = RemoveAppointmentUseCase(appointments),
        markAppointmentCompletedUseCase = MarkAppointmentCompletedUseCase(appointments),
        markAppointmentNoShowUseCase = MarkAppointmentNoShowUseCase(appointments),
        sendMessageUseCase = SendMessageUseCase(StubMessageRepository()),
        getPlansUseCase = GetPlansUseCase(users),
        purchaseUseCase = PurchaseUseCase(users, CreatePaymentUseCase(users), BuyAddonUseCase(users)),
        syncAppointmentsUseCase = SyncAppointmentsUseCase(appointments),
        getMySubscriptionUseCase = GetMySubscriptionUseCase(users),
        getMyEntitlementsUseCase = GetMyEntitlementsUseCase(users),
        getDailyCountsUseCase = GetDailyCountsUseCase(appointments),
        getSourceSummaryUseCase = GetSourceSummaryUseCase(appointments),
        businessStore = store,
    )

    private fun queueNames(vm: HomeViewModel) = vm.uiState.value.queue.map { it.visitorName }

    @Test
    fun loadsTheSelectedBusinessQueueAfterSyncingItsToday() = runTest {
        queues[1] = listOf(
            testAppointmentWithDetails(10, shopA, testVisitor(10, "Sara")),
            testAppointmentWithDetails(11, shopA, testVisitor(11, "Reza")),
        )
        val vm = viewModel(FakeBusinessStore(shopA))
        advanceUntilIdle()

        val state = vm.uiState.value
        assertEquals(shopA, state.business)
        assertEquals(listOf("Sara", "Reza"), queueNames(vm))
        assertEquals(listOf(1L), syncedFor.distinct(), "synced only the selected business")
        assertTrue(syncedReplace.all { it }, "today's window is replaced, not merged")
        assertTrue(state.statsLoaded)
        assertTrue(!state.isLoading)
        // the queue is laid out back to back: the second starts when the first ends
        val (first, second) = state.queue
        assertEquals(first.estimatedEndTime, second.estimatedStartTime)
    }

    @Test
    fun sourceSummaryCountsThisMonthFromItsFirstDayThroughToday() = runTest {
        dailyWindow = listOf(DailyCountDto(DateTimeUtils.isoDateDaysAgo(0), 3))
        sourceCounts = mapOf("APP" to 7, "WEB" to 2, "OWNER" to 5, "UNKNOWN" to 0)
        val vm = viewModel(FakeBusinessStore(shopA))
        advanceUntilIdle()

        assertEquals(SourceSummary(app = 7, web = 2), vm.uiState.value.sourceSummary)
        val (business, from, to) = sourceRequests.single()
        assertEquals(1L, business)
        assertEquals(vm.uiState.value.monthOverview!!.current.rangeStart, from)
        assertEquals(DateTimeUtils.startOfTodayMillis(), to)
    }

    @Test
    fun oldServerWithoutSourcesShowsNoSummary() = runTest {
        dailyWindow = listOf(DailyCountDto(DateTimeUtils.isoDateDaysAgo(0), 3))
        sourceCounts = null
        val vm = viewModel(FakeBusinessStore(shopA))
        advanceUntilIdle()

        assertNull(vm.uiState.value.sourceSummary)
    }

    @Test
    fun withoutABusinessNothingIsFetched() = runTest {
        val vm = viewModel(FakeBusinessStore(null))
        advanceUntilIdle()

        assertNull(vm.uiState.value.business)
        assertEquals(emptyList(), queueNames(vm))
        assertEquals(emptyList(), syncedFor)
    }

    @Test
    fun switchingBusinessReloadsTheQueueForTheNewOne() = runTest {
        queues[1] = listOf(testAppointmentWithDetails(10, shopA, testVisitor(10, "from-A")))
        queues[2] = listOf(testAppointmentWithDetails(20, shopB, testVisitor(20, "from-B")))
        val store = FakeBusinessStore(shopA)
        val vm = viewModel(store)
        advanceUntilIdle()
        assertEquals(listOf("from-A"), queueNames(vm))

        store.selectBusiness(shopB)
        advanceUntilIdle()

        assertEquals(shopB, vm.uiState.value.business)
        assertEquals(listOf("from-B"), queueNames(vm))
        assertTrue(2L in syncedFor)
    }

    @Test
    fun aSlowQueueForTheOldBusinessNeverOverwritesTheNewOne() = runTest {
        queues[1] = listOf(testAppointmentWithDetails(10, shopA, testVisitor(10, "from-A")))
        queues[2] = listOf(testAppointmentWithDetails(20, shopB, testVisitor(20, "from-B")))
        val gateA = CompletableDeferred<Unit>().also { queueGates[1] = it }
        val store = FakeBusinessStore(shopA)
        val vm = viewModel(store)
        advanceUntilIdle()           // A's request is parked on the gate

        store.selectBusiness(shopB)
        advanceUntilIdle()
        assertEquals(listOf("from-B"), queueNames(vm))

        gateA.complete(Unit)         // A answers late
        advanceUntilIdle()

        assertEquals(listOf("from-B"), queueNames(vm), "stale result from A was dropped")
        assertEquals(shopB, vm.uiState.value.business)
    }

    @Test
    fun completingAnAppointmentRefreshesTheQueue() = runTest {
        queues[1] = listOf(testAppointmentWithDetails(10, shopA, testVisitor(10, "Sara")))
        val vm = viewModel(FakeBusinessStore(shopA))
        advanceUntilIdle()
        val syncsBefore = syncedFor.size

        queues[1] = emptyList()      // the server no longer lists it as waiting
        vm.sendIntent(HomeIntent.MarkAppointmentCompleted(10))
        advanceUntilIdle()

        assertEquals(emptyList(), queueNames(vm))
        assertTrue(syncedFor.size > syncsBefore, "a completion re-syncs before re-reading")
    }
}
