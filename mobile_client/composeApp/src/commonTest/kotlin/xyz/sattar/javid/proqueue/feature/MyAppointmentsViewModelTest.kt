package xyz.sattar.javid.proqueue.feature

import app.cash.turbine.test
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import xyz.sattar.javid.proqueue.core.network.ApiResponse
import xyz.sattar.javid.proqueue.data.remoteDataSource.booking.model.AppointmentPageDto
import xyz.sattar.javid.proqueue.data.remoteDataSource.booking.model.ClientAppointmentDto
import xyz.sattar.javid.proqueue.feature.appointments.AppointmentsMessage
import xyz.sattar.javid.proqueue.feature.appointments.MyAppointmentsViewModel
import xyz.sattar.javid.proqueue.testsupport.FakeBookingApi
import xyz.sattar.javid.proqueue.testsupport.FakeClientPreferences
import xyz.sattar.javid.proqueue.testsupport.MainDispatcherSetup
import xyz.sattar.javid.proqueue.testsupport.businessDto
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class MyAppointmentsViewModelTest {
    private val main = MainDispatcherSetup()
    private val api = FakeBookingApi()
    private val prefs = FakeClientPreferences()

    @BeforeTest fun setUp() = main.setUp()
    @AfterTest fun tearDown() = main.tearDown()

    private fun vm() = MyAppointmentsViewModel(api, prefs, main.dispatcher)
    private fun apt(id: Long, status: String = "CONFIRMED") = ClientAppointmentDto(id, businessDto(7), 0, status)

    @Test
    fun signedOutShowsNoList() = runTest(main.dispatcher) {
        val vm = vm()
        vm.refresh()
        assertFalse(vm.state.value.isSignedIn)
        assertTrue(vm.state.value.appointments.isEmpty())
    }

    @Test
    fun signedInListsOwnAppointments() = runTest(main.dispatcher) {
        prefs.token.value = "tok"
        api.appointments = ApiResponse.Success(AppointmentPageDto(listOf(apt(1), apt(2, "LOCKED"))))
        val vm = vm()
        vm.refresh()
        val s = vm.state.value
        assertTrue(s.isSignedIn)
        assertFalse(s.isLoading)
        assertEquals(listOf(1L, 2L), s.appointments.map { it.id })
    }

    @Test
    fun rejectedSessionSignsOut() = runTest(main.dispatcher) {
        prefs.token.value = "stale"
        api.appointments = ApiResponse.Error("unauthorized", 401)
        val vm = vm()
        vm.refresh()
        assertNull(prefs.token.value)
        assertFalse(vm.state.value.isSignedIn)
    }

    @Test
    fun otherErrorsKeepTheSession() = runTest(main.dispatcher) {
        prefs.token.value = "tok"
        api.appointments = ApiResponse.Error("server", 500)
        val vm = vm()
        vm.refresh()
        assertEquals("tok", prefs.token.value)
        assertEquals("server", vm.state.value.errorMessage)
    }

    @Test
    fun cancelCallsTheApiAndRefreshes() = runTest(main.dispatcher) {
        prefs.token.value = "tok"
        api.appointments = ApiResponse.Success(AppointmentPageDto(listOf(apt(1))))
        val vm = vm()
        vm.messages.test {
            vm.cancel(1)
            assertEquals(AppointmentsMessage.Info("نوبت لغو شد"), awaitItem())
        }
        assertEquals(listOf(1L), api.cancelled)
        assertNull(vm.state.value.busyId)
        assertEquals(1, vm.state.value.appointments.size)
    }

    @Test
    fun payOnlineOpensTheGateway() = runTest(main.dispatcher) {
        prefs.token.value = "tok"
        val vm = vm()
        vm.messages.test {
            vm.payOnline(2)
            assertEquals(AppointmentsMessage.OpenUrl("https://gateway.zibal.ir/start/1"), awaitItem())
        }
    }

    @Test
    fun signOutForgetsTheToken() = runTest(main.dispatcher) {
        prefs.token.value = "tok"
        val vm = vm()
        vm.signOut()
        assertNull(prefs.token.value)
        assertFalse(vm.state.value.isSignedIn)
    }
}
