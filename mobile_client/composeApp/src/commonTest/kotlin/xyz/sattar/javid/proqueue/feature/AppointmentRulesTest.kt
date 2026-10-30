package xyz.sattar.javid.proqueue.feature

import app.cash.turbine.test
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import xyz.sattar.javid.proqueue.core.network.ApiResponse
import xyz.sattar.javid.proqueue.data.remoteDataSource.booking.model.AppointmentPageDto
import xyz.sattar.javid.proqueue.data.remoteDataSource.booking.model.ClientAppointmentDto
import xyz.sattar.javid.proqueue.feature.appointments.AppointmentDetailViewModel
import xyz.sattar.javid.proqueue.feature.appointments.AppointmentTab
import xyz.sattar.javid.proqueue.feature.appointments.AppointmentsMessage
import xyz.sattar.javid.proqueue.feature.appointments.canCancelAt
import xyz.sattar.javid.proqueue.feature.appointments.depositMethodLabel
import xyz.sattar.javid.proqueue.feature.appointments.showsCountdown
import xyz.sattar.javid.proqueue.feature.appointments.tabOf
import xyz.sattar.javid.proqueue.feature.appointments.timeLeftLabel
import xyz.sattar.javid.proqueue.testsupport.FakeBookingApi
import xyz.sattar.javid.proqueue.testsupport.FakeClientPreferences
import xyz.sattar.javid.proqueue.testsupport.MainDispatcherSetup
import xyz.sattar.javid.proqueue.testsupport.businessDto
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class AppointmentRulesTest {
    private val main = MainDispatcherSetup()
    private val api = FakeBookingApi()
    private val prefs = FakeClientPreferences()

    @BeforeTest fun setUp() = main.setUp()
    @AfterTest fun tearDown() = main.tearDown()

    private fun apt(id: Long, status: String, at: Long = 0) = ClientAppointmentDto(id, businessDto(7), at, status)

    @Test
    fun tabsSplitLikeTheWeb() {
        assertEquals(AppointmentTab.UPCOMING, tabOf("LOCKED"))
        assertEquals(AppointmentTab.UPCOMING, tabOf("WAITING"))
        assertEquals(AppointmentTab.PAST, tabOf("COMPLETED"))
        assertEquals(AppointmentTab.CANCELLED, tabOf("NO_SHOW"))
        assertEquals(AppointmentTab.CANCELLED, tabOf("CANCELLED"))
    }

    @Test
    fun countdownReadsDaysThenHours() {
        val minute = 60_000L
        assertEquals("۲ روز و ۳ ساعت مانده", timeLeftLabel(0, (2 * 24 * 60 + 3 * 60 + 5) * minute))
        assertEquals("۴ ساعت و ۱۰ دقیقه مانده", timeLeftLabel(0, (4 * 60 + 10) * minute))
        assertEquals("زمان نوبت فرا رسیده", timeLeftLabel(10 * minute, 5 * minute))
        assertTrue(showsCountdown("CONFIRMED"))
        assertFalse(showsCountdown("LOCKED"))
    }

    @Test
    fun onlyFutureCancellableAppointmentsCanBeCancelled() {
        assertTrue(apt(1, "CONFIRMED", at = 100).canCancelAt(50))
        assertFalse(apt(1, "CONFIRMED", at = 100).canCancelAt(150))
        assertFalse(apt(1, "COMPLETED", at = 100).canCancelAt(50))
    }

    @Test
    fun depositMethodOnTheSlip() {
        assertEquals("کارت به کارت", depositMethodLabel("CARD"))
        assertEquals("درگاه آنلاین", depositMethodLabel("GATEWAY"))
        assertNull(depositMethodLabel("NONE"))
    }

    @Test
    fun detailPicksTheAppointmentOutOfTheList() = runTest(main.dispatcher) {
        prefs.token.value = "tok"
        api.appointments = ApiResponse.Success(
            AppointmentPageDto(listOf(apt(1, "CONFIRMED"), apt(2, "PENDING_VERIFICATION").copy(paymentReceipt = "/media/r.jpg")))
        )
        val vm = AppointmentDetailViewModel(api, prefs, main.dispatcher)
        vm.load(2)
        assertEquals("/media/r.jpg", vm.state.value.appointment?.paymentReceipt)
        vm.load(9)
        assertEquals("نوبت پیدا نشد", vm.state.value.errorMessage)
    }

    @Test
    fun detailCancelsThenReloads() = runTest(main.dispatcher) {
        prefs.token.value = "tok"
        api.appointments = ApiResponse.Success(AppointmentPageDto(listOf(apt(3, "CONFIRMED", at = Long.MAX_VALUE))))
        val vm = AppointmentDetailViewModel(api, prefs, main.dispatcher)
        vm.load(3)
        vm.messages.test {
            vm.cancel()
            assertIs<AppointmentsMessage.Info>(awaitItem())
        }
        assertEquals(listOf(3L), api.cancelled)
        assertFalse(vm.state.value.cancelling)
    }

    @Test
    fun signedOutDetailAsksToSignIn() = runTest(main.dispatcher) {
        val vm = AppointmentDetailViewModel(api, prefs, main.dispatcher)
        vm.load(1)
        assertEquals("برای دیدن نوبت وارد شوید", vm.state.value.errorMessage)
    }
}
