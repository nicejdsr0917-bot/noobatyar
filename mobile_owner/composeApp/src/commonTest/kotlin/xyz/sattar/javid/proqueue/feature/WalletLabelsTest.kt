package xyz.sattar.javid.proqueue.feature

import xyz.sattar.javid.proqueue.data.remoteDataSource.user.model.EntitlementKeys
import xyz.sattar.javid.proqueue.data.remoteDataSource.user.model.WalletLedgerEntryDto
import xyz.sattar.javid.proqueue.data.remoteDataSource.user.model.WalletMetric
import xyz.sattar.javid.proqueue.feature.home.showsAppointmentWalletNote
import xyz.sattar.javid.proqueue.feature.wallet.WalletReason
import xyz.sattar.javid.proqueue.feature.wallet.affordableCount
import xyz.sattar.javid.proqueue.core.billing.formatToman
import xyz.sattar.javid.proqueue.feature.wallet.signedDelta
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

// Str/AppLocale need platform preferences, so these cover the logic behind the
// labels, not the localised text itself.
class WalletLabelsTest {
    private fun row(reason: String, label: String = "", delta: Int = 1) = WalletLedgerEntryDto(
        id = 1, metric = WalletMetric.SMS, delta = delta, balanceAfter = 0, reason = reason, reasonLabel = label
    )

    @Test
    fun appointmentNoteShowsOnlyWithCreditOnALimitedPlan() {
        assertTrue(showsAppointmentWalletNote(wallet = 49, quota = 30))
        assertFalse(showsAppointmentWalletNote(wallet = 0, quota = 30))
        assertFalse(showsAppointmentWalletNote(wallet = 49, quota = EntitlementKeys.UNLIMITED))
    }

    @Test
    fun deltaAlwaysCarriesItsSign() {
        assertEquals("+10", signedDelta(10))
        assertEquals("−1", signedDelta(-1))
        assertEquals("+0", signedDelta(0))
    }

    @Test
    fun knownReasonCodesMapToTheirOwnReason() {
        assertEquals(WalletReason.AddonPurchase, WalletReason.of(row("addon_purchase", label = "x")))
        assertEquals(WalletReason.Booking, WalletReason.of(row("booking", delta = -1)))
        assertEquals(WalletReason.SmsRefund, WalletReason.of(row("sms_refund")))
    }

    @Test
    fun unknownReasonUsesTheServerLabelThenTheSign() {
        assertNull(WalletReason.of(row("gift", label = "هدیه")))
        assertEquals(WalletReason.Credit, WalletReason.of(row("gift", delta = 5)))
        assertEquals(WalletReason.Debit, WalletReason.of(row("gift", delta = -5)))
    }

    @Test
    fun tomanAmountsAreGroupedByThousands() {
        assertEquals("10,000", formatToman(10_000))
        assertEquals("950", formatToman(950))
        assertEquals("1,234,567", formatToman(1_234_567))
    }

    @Test
    fun conversionOffersOnlyWhatTheBalanceBuys() {
        assertEquals(50, affordableCount(10_000, 200))
        assertEquals(1, affordableCount(999, 500))
        assertEquals(0, affordableCount(100, 200))
        assertEquals(0, affordableCount(10_000, 0))
    }

    @Test
    fun walletReasonsMapToTheirOwnLabels() {
        assertEquals(WalletReason.DiscountReimbursement, WalletReason.of(row("discount_reimbursement")))
        assertEquals(WalletReason.Convert, WalletReason.of(row("convert", delta = -1000)))
        assertEquals(WalletReason.ConvertedFromToman, WalletReason.of(row("toman_conversion")))
        assertEquals(WalletReason.PurchaseRefund, WalletReason.of(row("purchase_refund")))
    }
}
