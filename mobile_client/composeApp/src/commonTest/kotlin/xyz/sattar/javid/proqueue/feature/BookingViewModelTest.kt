package xyz.sattar.javid.proqueue.feature

import app.cash.turbine.test
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import xyz.sattar.javid.proqueue.core.network.ApiResponse
import xyz.sattar.javid.proqueue.data.remoteDataSource.booking.model.AppointmentPageDto
import xyz.sattar.javid.proqueue.data.remoteDataSource.booking.model.BookResponseDto
import xyz.sattar.javid.proqueue.data.remoteDataSource.booking.model.ClientAppointmentDto
import xyz.sattar.javid.proqueue.data.remoteDataSource.booking.model.OtpVerifyResponseDto
import xyz.sattar.javid.proqueue.feature.booking.BOOKING_DAYS_AHEAD
import xyz.sattar.javid.proqueue.feature.booking.BookingMessage
import xyz.sattar.javid.proqueue.feature.booking.BookingStep
import xyz.sattar.javid.proqueue.feature.booking.BookingViewModel
import xyz.sattar.javid.proqueue.feature.booking.normalizeDigits
import xyz.sattar.javid.proqueue.testsupport.FakeBookingApi
import xyz.sattar.javid.proqueue.testsupport.FakeBusinessRepository
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
class BookingViewModelTest {
    private val main = MainDispatcherSetup()
    private val repo = FakeBusinessRepository()
    private val api = FakeBookingApi()
    private val prefs = FakeClientPreferences()

    // A Monday, so offset 4 is Friday (Python weekday 4).
    private val monday = LocalDate(2026, 11, 2)

    @BeforeTest fun setUp() = main.setUp()
    @AfterTest fun tearDown() = main.tearDown()

    private fun vm() = BookingViewModel(repo, api, prefs, main.dispatcher, today = { monday })

    private fun business(services: List<String> = emptyList(), methods: List<String>? = null) =
        businessDto(7).copy(services = services, closedWeekdays = listOf(4), acceptedPaymentMethods = methods, defaultServiceDuration = 30)

    /** Loads the business and picks the first free slot. */
    private fun BookingViewModel.toSlotPicked() {
        load(7)
        selectSlot(state.value.slots.first())
        continueFromSlot()
    }

    @Test
    fun loadBuildsTheDayStripAndFetchesTodaysSlots() = runTest(main.dispatcher) {
        repo.details[7] = ApiResponse.Success(business())
        val vm = vm()
        vm.load(7)
        advanceUntilIdle()
        val s = vm.state.value
        assertEquals(BookingStep.SLOT, s.step)
        assertEquals(BOOKING_DAYS_AHEAD, s.days.size)
        assertTrue(s.days[4].closed, "Friday is the business's day off")
        assertFalse(s.days[0].closed)
        assertEquals(listOf("2026-11-02"), api.slotDates)
        assertEquals(2, s.slots.size)
    }

    @Test
    fun servicesComeFirstWhenTheBusinessListsThem() = runTest(main.dispatcher) {
        repo.details[7] = ApiResponse.Success(business(services = listOf("جرم‌گیری", "ترمیم")))
        val vm = vm()
        vm.load(7)
        assertEquals(BookingStep.SERVICES, vm.state.value.step)
        vm.toggleService("ترمیم")
        vm.toggleService("جرم‌گیری")
        vm.toggleService("ترمیم")
        assertEquals(listOf("جرم‌گیری"), vm.state.value.selectedServices)
        vm.continueFromServices()
        assertEquals(BookingStep.SLOT, vm.state.value.step)
        assertTrue(vm.back())
        assertEquals(BookingStep.SERVICES, vm.state.value.step)
        assertFalse(vm.back(), "the first step leaves the flow")
    }

    @Test
    fun selectingAnotherDayRefetchesSlotsAndClearsThePick() = runTest(main.dispatcher) {
        repo.details[7] = ApiResponse.Success(business())
        val vm = vm()
        vm.load(7)
        vm.selectSlot(vm.state.value.slots.first())
        vm.selectDay(1)
        assertNull(vm.state.value.selectedSlot)
        assertEquals(listOf("2026-11-02", "2026-11-03"), api.slotDates)
    }

    @Test
    fun unavailableSlotCannotBePicked() = runTest(main.dispatcher) {
        repo.details[7] = ApiResponse.Success(business())
        api.slots = ApiResponse.Success(
            xyz.sattar.javid.proqueue.data.remoteDataSource.booking.model.AvailableSlotsDto(
                slots = listOf(xyz.sattar.javid.proqueue.testsupport.slot("09:00", available = false))
            )
        )
        val vm = vm()
        vm.load(7)
        vm.selectSlot(vm.state.value.slots.first())
        assertNull(vm.state.value.selectedSlot)
    }

    @Test
    fun newVisitorSignsInRegistersAndBooksFromTheApp() = runTest(main.dispatcher) {
        repo.details[7] = ApiResponse.Success(business(services = listOf("ترمیم")))
        api.verify = ApiResponse.Success(OtpVerifyResponseDto(isRegistered = false, registerToken = "reg"))
        val vm = vm()
        vm.load(7)
        vm.toggleService("ترمیم")
        vm.continueFromServices()
        vm.selectSlot(vm.state.value.slots.first())
        vm.continueFromSlot()
        assertEquals(BookingStep.PHONE, vm.state.value.step)

        vm.setPhone("۰۹۱۲۳۴۵۶۷۸۹")
        vm.sendOtp()
        assertEquals("09123456789", api.otpSentTo)
        assertEquals(BookingStep.CODE, vm.state.value.step)

        vm.setCode("۱۲۳۴")
        vm.verifyOtp()
        assertEquals(BookingStep.NAME, vm.state.value.step)

        vm.setName(" مریم ")
        vm.register()
        assertEquals("new-tok", prefs.token.value)
        assertEquals(BookingStep.CONFIRM, vm.state.value.step)

        vm.setDescription("لطفاً صبح")
        vm.confirm()
        val (token, request) = api.booked.single()
        assertEquals("new-tok", token)
        assertEquals("APP", request.source)
        assertEquals(listOf("ترمیم"), request.selectedServices)
        assertEquals("لطفاً صبح", request.description)
        assertEquals(30, request.serviceDuration)
        assertEquals(BookingStep.DONE, vm.state.value.step)
    }

    @Test
    fun registeredVisitorSkipsTheNameStep() = runTest(main.dispatcher) {
        repo.details[7] = ApiResponse.Success(business())
        val vm = vm()
        vm.toSlotPicked()
        vm.setPhone("09123456789")
        vm.sendOtp()
        vm.setCode("1234")
        vm.verifyOtp()
        assertEquals("tok", prefs.token.value)
        assertEquals(BookingStep.CONFIRM, vm.state.value.step)
    }

    @Test
    fun invalidPhoneSendsNoCode() = runTest(main.dispatcher) {
        repo.details[7] = ApiResponse.Success(business())
        val vm = vm()
        vm.toSlotPicked()
        vm.setPhone("0912")
        vm.sendOtp()
        assertNull(api.otpSentTo)
        assertEquals(BookingStep.PHONE, vm.state.value.step)
    }

    @Test
    fun signedInVisitorGoesStraightToConfirm() = runTest(main.dispatcher) {
        repo.details[7] = ApiResponse.Success(business())
        prefs.token.value = "tok"
        val vm = vm()
        vm.toSlotPicked()
        assertEquals(BookingStep.CONFIRM, vm.state.value.step)
    }

    @Test
    fun depositShowsTheOwnCardAndRecordsTheTransfer() = runTest(main.dispatcher) {
        repo.details[7] = ApiResponse.Success(business(methods = listOf("CARD", "CASH")))
        prefs.token.value = "tok"
        api.bookResponse = ApiResponse.Success(BookResponseDto(id = 55, requiresPayment = true))
        api.appointments = ApiResponse.Success(
            AppointmentPageDto(
                listOf(ClientAppointmentDto(55, businessDto(7), 0, "LOCKED", cardNumber = "6037991234567890", cardOwnerName = "صاحب کارت"))
            )
        )
        val vm = vm()
        vm.toSlotPicked()
        vm.confirm()
        val s = vm.state.value
        assertEquals(BookingStep.PAYMENT, s.step)
        assertEquals("6037991234567890", s.cardNumber)
        assertFalse(vm.back(), "a held appointment cannot be stepped back out of")

        vm.payByCard("  ")
        assertTrue(api.payments.isEmpty())
        vm.payByCard(" 123456 ")
        val pay = api.payments.single()
        assertEquals("CARD", pay.method)
        assertEquals("123456", pay.reference)
        assertEquals(55, pay.id)
        assertEquals(BookingStep.DONE, vm.state.value.step)
    }

    @Test
    fun onlineDepositOpensTheGateway() = runTest(main.dispatcher) {
        repo.details[7] = ApiResponse.Success(business(methods = listOf("ONLINE")))
        prefs.token.value = "tok"
        api.bookResponse = ApiResponse.Success(BookResponseDto(id = 55, requiresPayment = true))
        val vm = vm()
        vm.messages.test {
            vm.toSlotPicked()
            vm.confirm()
            vm.payOnline()
            assertEquals(BookingMessage.OpenUrl("https://gateway.zibal.ir/start/1"), awaitItem())
        }
        assertEquals(BookingStep.DONE, vm.state.value.step)
        assertTrue(vm.state.value.paidOnline)
    }

    @Test
    fun takenSlotSendsTheVisitorBackToFreshTimes() = runTest(main.dispatcher) {
        repo.details[7] = ApiResponse.Success(business())
        prefs.token.value = "tok"
        api.bookResponse = ApiResponse.Error("این زمان پر شده است", 409)
        val vm = vm()
        vm.messages.test {
            vm.toSlotPicked()
            vm.confirm()
            assertEquals(BookingMessage.Error("این زمان پر شده است"), awaitItem())
        }
        assertEquals(BookingStep.SLOT, vm.state.value.step)
        assertNull(vm.state.value.selectedSlot)
        assertEquals(2, api.slotDates.size, "slots were reloaded")
    }

    @Test
    fun expiredSessionAsksForSignInAgain() = runTest(main.dispatcher) {
        repo.details[7] = ApiResponse.Success(business())
        prefs.token.value = "stale"
        api.bookResponse = ApiResponse.Error("unauthorized", 401)
        val vm = vm()
        vm.toSlotPicked()
        vm.confirm()
        assertNull(prefs.token.value)
        assertEquals(BookingStep.PHONE, vm.state.value.step)
    }

    @Test
    fun loadErrorIsShown() = runTest(main.dispatcher) {
        val vm = vm()
        vm.load(99)
        advanceUntilIdle()
        assertEquals("not found", vm.state.value.loadError)
    }

    @Test
    fun persianDigitsAreNormalised() {
        assertEquals("09123456789", "۰۹۱۲۳۴۵۶۷۸۹".normalizeDigits())
        assertEquals("12a", "١٢a".normalizeDigits())
    }
}
