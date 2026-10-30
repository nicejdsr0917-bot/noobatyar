package xyz.sattar.javid.proqueue.feature

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import xyz.sattar.javid.proqueue.core.i18n.Str
import xyz.sattar.javid.proqueue.domain.model.appointment.AppointmentOrdering
import xyz.sattar.javid.proqueue.domain.model.appointment.AppointmentWithDetails
import xyz.sattar.javid.proqueue.domain.usecase.GetAppointmentsForDateUseCase
import xyz.sattar.javid.proqueue.domain.usecase.SyncAppointmentsUseCase
import xyz.sattar.javid.proqueue.feature.calendar.CalendarIntent
import xyz.sattar.javid.proqueue.feature.calendar.CalendarViewModel
import xyz.sattar.javid.proqueue.testsupport.FakeBusinessStore
import xyz.sattar.javid.proqueue.testsupport.MainDispatcherSetup
import xyz.sattar.javid.proqueue.testsupport.StubAppointmentRepository
import xyz.sattar.javid.proqueue.testsupport.testAppointmentWithDetails
import xyz.sattar.javid.proqueue.testsupport.testBusiness
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class CalendarViewModelTest {
    private val main = MainDispatcherSetup()

    @BeforeTest fun setUp() = main.setUp()
    @AfterTest fun tearDown() = main.tearDown()

    private data class Sync(val businessId: Long, val date: Long?, val replaceWindow: Boolean)

    private val syncs = mutableListOf<Sync>()
    private val reads = mutableListOf<Pair<Long, Long>>()
    private val rowsByDate = mutableMapOf<Long, List<AppointmentWithDetails>>()
    private var failSync: Throwable? = null

    private val appointments = object : StubAppointmentRepository() {
        override suspend fun syncAppointments(
            businessId: Long, visitorId: Long?, status: String?, date: Long?, dateFrom: Long?, dateTo: Long?,
            ordering: AppointmentOrdering?, page: Int, pageSize: Int, replaceWindow: Boolean,
        ): Boolean {
            failSync?.let { throw it }
            syncs += Sync(businessId, date, replaceWindow); return true
        }
        override suspend fun getAppointmentsForDate(businessId: Long, date: Long): List<AppointmentWithDetails> {
            reads += businessId to date
            return rowsByDate[date].orEmpty()
        }
    }

    private val shop = testBusiness(5)
    private fun viewModel(store: FakeBusinessStore) = CalendarViewModel(
        getAppointmentsForDateUseCase = GetAppointmentsForDateUseCase(appointments),
        syncAppointmentsUseCase = SyncAppointmentsUseCase(appointments),
        businessStore = store,
    )

    private val day1 = 1_700_000_000_000L
    private val day2 = day1 + 24 * 60 * 60 * 1000L

    @Test
    fun selectingADateSyncsThatDateForTheBusinessThenReadsIt() = runTest {
        rowsByDate[day2] = listOf(testAppointmentWithDetails(1, shop))
        val vm = viewModel(FakeBusinessStore(shop))
        advanceUntilIdle()
        syncs.clear(); reads.clear()

        vm.sendIntent(CalendarIntent.SelectDate(day2))
        advanceUntilIdle()

        assertEquals(listOf(Sync(5, day2, replaceWindow = true)), syncs)
        assertEquals(listOf(5L to day2), reads)
        assertEquals(day2, vm.uiState.value.selectedDate)
        assertEquals(1, vm.uiState.value.appointments.size)
        assertTrue(!vm.uiState.value.isLoading)
    }

    @Test
    fun eachDateChangeUsesItsOwnWindowAndReplacesTheRows() = runTest {
        rowsByDate[day1] = listOf(testAppointmentWithDetails(1, shop))
        rowsByDate[day2] = emptyList()
        val vm = viewModel(FakeBusinessStore(shop))
        advanceUntilIdle()
        syncs.clear()

        vm.sendIntent(CalendarIntent.SelectDate(day1)); advanceUntilIdle()
        assertEquals(1, vm.uiState.value.appointments.size)
        vm.sendIntent(CalendarIntent.SelectDate(day2)); advanceUntilIdle()

        assertEquals(listOf(day1, day2), syncs.map { it.date })
        assertTrue(syncs.all { it.replaceWindow })
        assertEquals(emptyList(), vm.uiState.value.appointments, "day 2 shows its own rows, not day 1's")
    }

    @Test
    fun switchingBusinessReloadsForTheNewBusinessId() = runTest {
        val store = FakeBusinessStore(shop)
        val vm = viewModel(store)
        advanceUntilIdle()
        syncs.clear()

        store.selectBusiness(testBusiness(9))
        advanceUntilIdle()

        assertEquals(listOf(9L), syncs.map { it.businessId })
        assertEquals(9L, reads.last().first)
        assertEquals(vm.uiState.value.selectedDate, syncs.single().date, "keeps the date the user is on")
    }

    @Test
    fun noBusinessSelectedShowsAnErrorAndFetchesNothing() = runTest {
        val vm = viewModel(FakeBusinessStore(null))
        advanceUntilIdle()

        assertEquals(Str.no_business_selected_2, vm.uiState.value.error)
        assertEquals(emptyList(), syncs)
        assertEquals(emptyList(), reads)
    }

    @Test
    fun aFailingSyncSurfacesItsMessageAndStopsLoading() = runTest {
        failSync = IllegalStateException("offline")
        val vm = viewModel(FakeBusinessStore(shop))
        advanceUntilIdle()

        assertEquals("offline", vm.uiState.value.error)
        assertTrue(!vm.uiState.value.isLoading)
    }
}
