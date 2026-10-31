package xyz.sattar.javid.proqueue.feature.booking

import xyz.sattar.javid.proqueue.data.remoteDataSource.booking.model.TimeSlotDto
import xyz.sattar.javid.proqueue.domain.model.business.Business
import xyz.sattar.javid.proqueue.feature.businessDetail.DEPOSIT_MANDATORY
import xyz.sattar.javid.proqueue.feature.businessDetail.DEPOSIT_OPTIONAL

/**
 * What the step indicator shows. Coarser than [BookingStep]: the one-time
 * sign-in screens belong to «تایید», so a returning visitor and a new one see
 * the same few stages.
 */
enum class BookingPhase(val label: String) {
    SERVICE("خدمت"),
    TIME("زمان"),
    CONFIRM("تایید"),
    PAY("پرداخت"),
}

fun BookingStep.phase(): BookingPhase? = when (this) {
    BookingStep.SERVICES -> BookingPhase.SERVICE
    BookingStep.SLOT -> BookingPhase.TIME
    BookingStep.PHONE, BookingStep.CODE, BookingStep.NAME, BookingStep.CONFIRM -> BookingPhase.CONFIRM
    BookingStep.PAYMENT -> BookingPhase.PAY
    BookingStep.DONE -> null
}

/** A deposit is due before the slot is final, so the flow ends in a payment step. */
fun Business.expectsDeposit(): Boolean =
    depositMode == DEPOSIT_MANDATORY && (depositAmount ?: 0) > 0

/**
 * The stages this visitor will go through. Payment shows up front when the
 * business takes a mandatory deposit, and always once the server asked for one
 * ([paymentRequested] keeps it there after the visitor has paid and moved on).
 */
fun bookingPhases(business: Business?, step: BookingStep, paymentRequested: Boolean = false): List<BookingPhase> = buildList {
    if (business?.offersServiceStep() == true) add(BookingPhase.SERVICE)
    add(BookingPhase.TIME)
    add(BookingPhase.CONFIRM)
    if (business?.expectsDeposit() == true || paymentRequested || step == BookingStep.PAYMENT) add(BookingPhase.PAY)
}

/** Index of the current stage in [phases]; past the end once booked. */
fun currentPhaseIndex(phases: List<BookingPhase>, step: BookingStep): Int =
    step.phase()?.let(phases::indexOf)?.takeIf { it >= 0 } ?: phases.size

/**
 * The enum is declared in flow order, so a later step is a move forward. The
 * server can send the visitor back (a taken slot, an expired session); those
 * read as backward and slide the other way.
 */
fun isForward(from: BookingStep, to: BookingStep): Boolean = to.ordinal > from.ordinal

enum class DayPart(val label: String) {
    MORNING("صبح"),
    AFTERNOON("بعدازظهر"),
    EVENING("عصر و شب"),
}

internal fun TimeSlotDto.hour(): Int = time.substringBefore(':').trim().toIntOrNull() ?: 0

fun dayPartOf(hour: Int): DayPart = when {
    hour < 12 -> DayPart.MORNING
    hour < 17 -> DayPart.AFTERNOON
    else -> DayPart.EVENING
}

/** Slots in their server order, split into the parts of the day that have any. */
fun groupByDayPart(slots: List<TimeSlotDto>): List<Pair<DayPart, List<TimeSlotDto>>> =
    slots.groupBy { dayPartOf(it.hour()) }
        .toList()
        .sortedBy { it.first.ordinal }

enum class PaymentOption { ONLINE, CARD, CASH }

/**
 * The deposit options to offer, in the order shown; the web checkout's rules.
 * Online needs a real gateway or the owner's own payment link. Card is the
 * fallback when the business never said (the number may still be missing:
 * the step then says so). Cash is never offered against a mandatory deposit,
 * and always against an optional one.
 */
fun paymentOptions(
    methods: List<String>,
    depositMode: String?,
    onlineGateway: Boolean,
    paymentLink: String,
): List<PaymentOption> = buildList {
    if ("ONLINE" in methods && (onlineGateway || paymentLink.isNotBlank())) add(PaymentOption.ONLINE)
    if (methods.isEmpty() || "CARD" in methods) add(PaymentOption.CARD)
    if (depositMode != DEPOSIT_MANDATORY && ("CASH" in methods || depositMode == DEPOSIT_OPTIONAL)) add(PaymentOption.CASH)
}

/** The owner's manual payment page, made absolute like the web does. */
fun paymentLinkUrl(link: String): String? = link.trim().takeIf { it.isNotEmpty() }
    ?.let { if (it.startsWith("http")) it else "https://$it" }

/** Services come first when there is a menu, or the visitor may type their own. */
fun Business.offersServiceStep(): Boolean = services.isNotEmpty() || allowClientAddService

const val CUSTOM_SERVICE_MAX = 100

/**
 * A typed service as it is stored: the server splits services on commas, so
 * one inside a name becomes a space. Null when nothing usable is left.
 */
fun customServiceName(raw: String): String? =
    raw.replace(',', ' ').replace('،', ' ').trim().take(CUSTOM_SERVICE_MAX).takeIf { it.isNotEmpty() }

/**
 * A card transfer needs proof: a tracking number, a photo of the receipt, or
 * both (the server applies the same rule).
 */
fun canSubmitCardPayment(reference: String, hasReceipt: Boolean): Boolean =
    hasReceipt || reference.isNotBlank()
