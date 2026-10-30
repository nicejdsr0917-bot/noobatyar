package xyz.sattar.javid.proqueue.feature

import app.cash.turbine.test
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import xyz.sattar.javid.proqueue.core.i18n.Str
import xyz.sattar.javid.proqueue.core.network.ApiException
import xyz.sattar.javid.proqueue.core.notifications.NotificationScheduler
import xyz.sattar.javid.proqueue.core.ui.components.ToastyType
import xyz.sattar.javid.proqueue.domain.model.appointment.Appointment
import xyz.sattar.javid.proqueue.domain.model.appointment.AppointmentWithDetails
import xyz.sattar.javid.proqueue.domain.usecase.AddServiceCatalogItemUseCase
import xyz.sattar.javid.proqueue.domain.usecase.CheckAppointmentConflictUseCase
import xyz.sattar.javid.proqueue.domain.usecase.CreateAppointmentUseCase
import xyz.sattar.javid.proqueue.domain.usecase.GetAppointmentByIdUseCase
import xyz.sattar.javid.proqueue.domain.usecase.GetAppointmentsForDateUseCase
import xyz.sattar.javid.proqueue.domain.usecase.GetServiceCatalogUseCase
import xyz.sattar.javid.proqueue.domain.usecase.GetVisitorByIdUseCase
import xyz.sattar.javid.proqueue.domain.usecase.RemoveAppointmentUseCase
import xyz.sattar.javid.proqueue.domain.usecase.UpdateAppointmentUseCase
import xyz.sattar.javid.proqueue.feature.createAppointment.CreateAppointmentEvent
import xyz.sattar.javid.proqueue.feature.createAppointment.CreateAppointmentIntent
import xyz.sattar.javid.proqueue.feature.createAppointment.CreateAppointmentViewModel
import xyz.sattar.javid.proqueue.testsupport.FakeBusinessStore
import xyz.sattar.javid.proqueue.testsupport.MainDispatcherSetup
import xyz.sattar.javid.proqueue.testsupport.StubAppointmentRepository
import xyz.sattar.javid.proqueue.testsupport.StubBusinessApi
import xyz.sattar.javid.proqueue.testsupport.StubBusinessLocalSource
import xyz.sattar.javid.proqueue.testsupport.StubVisitorApi
import xyz.sattar.javid.proqueue.testsupport.StubVisitorLocalSource
import xyz.sattar.javid.proqueue.testsupport.testAppointmentWithDetails
import xyz.sattar.javid.proqueue.testsupport.testBusiness
import xyz.sattar.javid.proqueue.testsupport.testVisitor
import xyz.sattar.javid.proqueue.data.repository.business.BusinessRepositoryImpl
import xyz.sattar.javid.proqueue.data.repository.visitor.VisitorRepositoryImpl
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class CreateAppointmentViewModelTest {
    private val main = MainDispatcherSetup()

    @BeforeTest fun setUp() = main.setUp()
    @AfterTest fun tearDown() = main.tearDown()

    private val shop = testBusiness(3)

    private val created = mutableListOf<Appointment>()
    private var createError: Throwable? = null
    private var createdId = 100L
    private var conflicts: List<AppointmentWithDetails> = emptyList()
    private var conflictQuery: List<Long>? = null

    private val appointments = object : StubAppointmentRepository() {
        override suspend fun createAppointment(appointment: Appointment): Long {
            createError?.let { throw it }
            created += appointment
            return createdId
        }
        override suspend fun getConflictingAppointments(businessId: Long, startTime: Long, endTime: Long, defaultDuration: Int): List<AppointmentWithDetails> {
            conflictQuery = listOf(businessId, startTime, endTime)
            return conflicts
        }
    }

    private val scheduler = object : NotificationScheduler {
        override fun scheduleReminder(appointmentId: Long, customerName: String, businessName: String, triggerAtMillis: Long, minutesBefore: Int, businessId: Long, visitorId: Long) {}
        override fun cancelReminder(appointmentId: Long) {}
        override suspend fun hasPermission() = false
    }

    // The real repositories over stubbed sources: notifications are off for the
    // test business, so they are only consulted, never written to.
    private val businessRepo = BusinessRepositoryImpl(
        object : StubBusinessLocalSource() {
            override suspend fun getBusinessById(businessId: Long) = shop
        },
        StubBusinessApi()
    )
    private val visitorRepo = VisitorRepositoryImpl(StubVisitorLocalSource(), StubVisitorApi())

    private fun viewModel(store: FakeBusinessStore) = CreateAppointmentViewModel(
        getVisitorByIdUseCase = GetVisitorByIdUseCase(visitorRepo),
        createAppointmentUseCase = CreateAppointmentUseCase(appointments, visitorRepo, businessRepo, scheduler),
        getAppointmentByIdUseCase = GetAppointmentByIdUseCase(appointments),
        updateAppointmentUseCase = UpdateAppointmentUseCase(appointments),
        checkAppointmentConflictUseCase = CheckAppointmentConflictUseCase(appointments),
        getAppointmentsForDateUseCase = GetAppointmentsForDateUseCase(appointments),
        removeAppointmentUseCase = RemoveAppointmentUseCase(appointments),
        getServiceCatalogUseCase = GetServiceCatalogUseCase(businessRepo),
        addServiceCatalogItemUseCase = AddServiceCatalogItemUseCase(businessRepo),
        businessStore = store,
    )

    private fun create(vm: CreateAppointmentViewModel, force: Boolean = false, services: List<String> = emptyList()) =
        vm.sendIntent(
            CreateAppointmentIntent.CreateAppointment(
                visitorId = 7, appointmentDate = 1_000_000L, serviceDuration = 20,
                description = "note", selectedServices = services, force = force,
            )
        )

    @Test
    fun withoutABusinessItWarnsAndCreatesNothing() = runTest {
        val vm = viewModel(FakeBusinessStore(null))
        create(vm); advanceUntilIdle()

        val message = assertNotNull(vm.uiState.value.message)
        assertEquals(Str.select_business_error, message.text)
        assertEquals(ToastyType.Warning, message.type)
        assertEquals(emptyList(), created)
    }

    @Test
    fun aConflictOpensTheDialogAndDoesNotCreate() = runTest {
        conflicts = listOf(testAppointmentWithDetails(1, shop, testVisitor(1, "Ali"), date = 1_000_000L))
        val vm = viewModel(FakeBusinessStore(shop))
        create(vm); advanceUntilIdle()

        assertTrue(vm.uiState.value.showConflictDialog)
        assertEquals("Ali", vm.uiState.value.conflictingVisitorName)
        assertEquals(emptyList(), created)
        // the window checked is [start, start + 20 min) for the selected business
        assertEquals(listOf(3L, 1_000_000L, 1_000_000L + 20 * 60 * 1000), conflictQuery)
    }

    @Test
    fun forcingSkipsTheConflictCheckAndCreates() = runTest {
        conflicts = listOf(testAppointmentWithDetails(1, shop))
        val vm = viewModel(FakeBusinessStore(shop))
        create(vm, force = true); advanceUntilIdle()

        assertNull(conflictQuery, "force must not even ask")
        assertEquals(1, created.size)
    }

    @Test
    fun successCreatesForTheSelectedBusinessAndEmitsTheEvent() = runTest {
        val vm = viewModel(FakeBusinessStore(shop))
        vm.events.test {
            create(vm, services = listOf("cut", "color")); advanceUntilIdle()

            assertEquals(CreateAppointmentEvent.AppointmentCreated, awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
        val saved = created.single()
        assertEquals(3L, saved.businessId)
        assertEquals(7L, saved.visitorId)
        assertEquals(20, saved.serviceDuration)
        assertEquals("cut,color", saved.selectedServices)
        assertEquals("WAITING", saved.status)
        assertTrue(vm.uiState.value.appointmentCreated)
        assertTrue(!vm.uiState.value.isLoading)
    }

    @Test
    fun aZeroIdFromTheServerIsASaveError() = runTest {
        createdId = 0
        val vm = viewModel(FakeBusinessStore(shop))
        create(vm); advanceUntilIdle()

        assertEquals(Str.error_saving_appointment, vm.uiState.value.message?.text)
        assertTrue(!vm.uiState.value.appointmentCreated)
    }

    @Test
    fun aQuotaErrorOpensTheQuotaDialogNotAToast() = runTest {
        createError = ApiException("quota reached", 409)
        val vm = viewModel(FakeBusinessStore(shop))
        create(vm); advanceUntilIdle()

        assertEquals("quota reached", vm.uiState.value.quotaDialogMessage)
        assertNull(vm.uiState.value.message)
    }

    @Test
    fun anyOtherFailureShowsItsMessage() = runTest {
        createError = IllegalStateException("boom")
        val vm = viewModel(FakeBusinessStore(shop))
        create(vm); advanceUntilIdle()

        assertEquals("boom", vm.uiState.value.message?.text)
        assertNull(vm.uiState.value.quotaDialogMessage)
        assertTrue(!vm.uiState.value.appointmentCreated)
    }
}
