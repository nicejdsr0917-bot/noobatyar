package xyz.sattar.javid.proqueue.feature

import app.cash.turbine.test
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import xyz.sattar.javid.proqueue.core.network.ApiResponse
import xyz.sattar.javid.proqueue.data.remoteDataSource.booking.model.OtpVerifyResponseDto
import xyz.sattar.javid.proqueue.feature.auth.LoginStep
import xyz.sattar.javid.proqueue.feature.auth.LoginViewModel
import xyz.sattar.javid.proqueue.feature.auth.RESEND_AFTER_SECONDS
import xyz.sattar.javid.proqueue.testsupport.FakeBookingApi
import xyz.sattar.javid.proqueue.testsupport.FakeClientPreferences
import xyz.sattar.javid.proqueue.testsupport.MainDispatcherSetup
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class LoginViewModelTest {
    private val main = MainDispatcherSetup()
    private val api = FakeBookingApi()
    private val prefs = FakeClientPreferences()

    @BeforeTest fun setUp() = main.setUp()
    @AfterTest fun tearDown() = main.tearDown()

    private fun vm() = LoginViewModel(api, prefs, main.dispatcher)

    @Test
    fun aReturningVisitorSignsInAsSoonAsTheSixthDigitIsTyped() = runTest(main.dispatcher) {
        val vm = vm()
        vm.setPhone("۰۹۱۲۳۴۵۶۷۸۹")
        vm.sendCode()
        assertEquals("09123456789", api.otpSentTo)
        assertEquals(LoginStep.CODE, vm.state.value.step)
        assertEquals(RESEND_AFTER_SECONDS, vm.state.value.resendIn)

        vm.setCode("12345")
        assertNull(prefs.token.value)
        vm.setCode("۱۲۳۴۵۶")
        assertEquals("tok", prefs.token.value)
        assertTrue(vm.state.value.signedIn)
    }

    @Test
    fun aNewVisitorIsAskedForAName() = runTest(main.dispatcher) {
        api.verify = ApiResponse.Success(OtpVerifyResponseDto(isRegistered = false, registerToken = "reg"))
        val vm = vm()
        vm.setPhone("09123456789")
        vm.sendCode()
        vm.setCode("123456")
        assertEquals(LoginStep.NAME, vm.state.value.step)
        assertNull(prefs.token.value)

        vm.setName("   ")
        vm.register()
        assertNull(prefs.token.value)

        vm.setName(" مریم ")
        vm.register()
        assertEquals("new-tok", prefs.token.value)
        assertTrue(vm.state.value.signedIn)
    }

    @Test
    fun aWrongCodeClearsTheBoxesAndSaysWhy() = runTest(main.dispatcher) {
        api.verify = ApiResponse.Error("کد وارد شده اشتباه است", 400)
        val vm = vm()
        vm.setPhone("09123456789")
        vm.sendCode()
        vm.errors.test {
            vm.setCode("111111")
            assertEquals("کد وارد شده اشتباه است", awaitItem())
        }
        assertEquals("", vm.state.value.code)
        assertEquals(LoginStep.CODE, vm.state.value.step)
        assertFalse(vm.state.value.signedIn)
    }

    @Test
    fun aBadPhoneSendsNothing() = runTest(main.dispatcher) {
        val vm = vm()
        vm.setPhone("12345")
        vm.sendCode()
        assertNull(api.otpSentTo)
        assertEquals(LoginStep.PHONE, vm.state.value.step)
    }

    @Test
    fun backWalksTheStepsAndLeavesFromTheFirst() = runTest(main.dispatcher) {
        api.verify = ApiResponse.Success(OtpVerifyResponseDto(isRegistered = false, registerToken = "reg"))
        val vm = vm()
        assertFalse(vm.back())
        vm.setPhone("09123456789")
        vm.sendCode()
        vm.setCode("123456")
        assertEquals(LoginStep.NAME, vm.state.value.step)
        assertTrue(vm.back())
        assertEquals(LoginStep.CODE, vm.state.value.step)
        assertTrue(vm.back())
        assertEquals(LoginStep.PHONE, vm.state.value.step)
        assertFalse(vm.back())
    }

    @Test
    fun theCodeCannotBeResentDuringTheCountdown() = runTest(main.dispatcher) {
        val vm = vm()
        vm.setPhone("09123456789")
        vm.sendCode()
        api.otpSentTo = null
        vm.resend()
        assertNull(api.otpSentTo)
    }
}
