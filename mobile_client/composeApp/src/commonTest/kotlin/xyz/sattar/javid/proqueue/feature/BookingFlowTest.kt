package xyz.sattar.javid.proqueue.feature

import xyz.sattar.javid.proqueue.data.remoteDataSource.booking.model.TimeSlotDto
import xyz.sattar.javid.proqueue.data.remoteDataSource.business.toDomain
import xyz.sattar.javid.proqueue.feature.booking.BookingPhase
import xyz.sattar.javid.proqueue.feature.booking.BookingStep
import xyz.sattar.javid.proqueue.feature.booking.DayPart
import xyz.sattar.javid.proqueue.feature.booking.PaymentOption
import xyz.sattar.javid.proqueue.feature.booking.bookingPhases
import xyz.sattar.javid.proqueue.feature.booking.canSubmitCardPayment
import xyz.sattar.javid.proqueue.feature.booking.currentPhaseIndex
import xyz.sattar.javid.proqueue.feature.booking.dayPartOf
import xyz.sattar.javid.proqueue.feature.booking.expectsDeposit
import xyz.sattar.javid.proqueue.feature.booking.groupByDayPart
import xyz.sattar.javid.proqueue.feature.booking.isForward
import xyz.sattar.javid.proqueue.feature.booking.paymentOptions
import xyz.sattar.javid.proqueue.feature.businessDetail.DEPOSIT_MANDATORY
import xyz.sattar.javid.proqueue.feature.businessDetail.DEPOSIT_OPTIONAL
import xyz.sattar.javid.proqueue.testsupport.businessDto
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class BookingFlowTest {
    private fun business(services: List<String> = emptyList(), mode: String? = null, amount: Int? = null) =
        businessDto(1).copy(services = services, depositMode = mode, depositAmount = amount).toDomain()

    @Test
    fun phasesFollowWhatTheBusinessAsksFor() {
        assertEquals(
            listOf(BookingPhase.TIME, BookingPhase.CONFIRM),
            bookingPhases(business(), BookingStep.SLOT),
        )
        assertEquals(
            listOf(BookingPhase.SERVICE, BookingPhase.TIME, BookingPhase.CONFIRM, BookingPhase.PAY),
            bookingPhases(business(listOf("ترمیم"), DEPOSIT_MANDATORY, 50000), BookingStep.SERVICES),
        )
    }

    @Test
    fun paymentPhaseAppearsWhenTheServerAsksEvenWithoutAMandatoryDeposit() {
        val optional = business(mode = DEPOSIT_OPTIONAL, amount = 50000)
        assertFalse(BookingPhase.PAY in bookingPhases(optional, BookingStep.CONFIRM))
        assertTrue(BookingPhase.PAY in bookingPhases(optional, BookingStep.PAYMENT))
        // Paid and done: the stage stays, so the indicator does not shrink while fading out.
        assertTrue(BookingPhase.PAY in bookingPhases(optional, BookingStep.DONE, paymentRequested = true))
    }

    @Test
    fun aZeroDepositIsNoDeposit() {
        assertFalse(business(mode = DEPOSIT_MANDATORY, amount = 0).expectsDeposit())
        assertFalse(business(mode = DEPOSIT_MANDATORY, amount = null).expectsDeposit())
        assertTrue(business(mode = DEPOSIT_MANDATORY, amount = 1000).expectsDeposit())
    }

    @Test
    fun signInStepsSitUnderConfirm() {
        val phases = bookingPhases(business(listOf("ترمیم")), BookingStep.PHONE)
        listOf(BookingStep.PHONE, BookingStep.CODE, BookingStep.NAME, BookingStep.CONFIRM).forEach {
            assertEquals(2, currentPhaseIndex(phases, it), "$it")
        }
        assertEquals(0, currentPhaseIndex(phases, BookingStep.SERVICES))
        assertEquals(phases.size, currentPhaseIndex(phases, BookingStep.DONE), "booked: every stage done")
    }

    @Test
    fun serverSendBacksSlideBackward() {
        assertTrue(isForward(BookingStep.SLOT, BookingStep.CONFIRM))
        assertTrue(isForward(BookingStep.CONFIRM, BookingStep.PAYMENT))
        // 409: the slot was taken; 401: the session expired.
        assertFalse(isForward(BookingStep.CONFIRM, BookingStep.SLOT))
        assertFalse(isForward(BookingStep.CONFIRM, BookingStep.PHONE))
    }

    private fun slot(time: String, available: Boolean = true) = TimeSlotDto(time, 0, available)

    @Test
    fun slotsSplitIntoPartsOfTheDayInOrder() {
        val groups = groupByDayPart(listOf(slot("17:00"), slot("09:00"), slot("12:30"), slot("09:30", false), slot("11:59")))
        assertEquals(listOf(DayPart.MORNING, DayPart.AFTERNOON, DayPart.EVENING), groups.map { it.first })
        assertEquals(listOf("09:00", "09:30", "11:59"), groups[0].second.map { it.time })
        assertEquals(DayPart.MORNING, dayPartOf(0))
        assertEquals(DayPart.AFTERNOON, dayPartOf(16))
        assertEquals(DayPart.EVENING, dayPartOf(17))
    }

    @Test
    fun emptyPartsAreLeftOut() {
        assertEquals(listOf(DayPart.EVENING), groupByDayPart(listOf(slot("18:00"))).map { it.first })
        assertTrue(groupByDayPart(emptyList()).isEmpty())
    }

    @Test
    fun cardToCardNeedsTheCardNumber() {
        val all = listOf("CASH", "CARD", "ONLINE")
        assertEquals(listOf(PaymentOption.ONLINE, PaymentOption.CARD, PaymentOption.CASH), paymentOptions(all, "6037991234567890"))
        assertEquals(listOf(PaymentOption.ONLINE, PaymentOption.CASH), paymentOptions(all, " "))
        assertTrue(paymentOptions(listOf("GATEWAY"), "").isEmpty())
    }

    @Test
    fun cardPaymentNeedsAReferenceOrAReceipt() {
        assertFalse(canSubmitCardPayment("  ", hasReceipt = false))
        assertTrue(canSubmitCardPayment("123", hasReceipt = false))
        assertTrue(canSubmitCardPayment("", hasReceipt = true))
    }
}
