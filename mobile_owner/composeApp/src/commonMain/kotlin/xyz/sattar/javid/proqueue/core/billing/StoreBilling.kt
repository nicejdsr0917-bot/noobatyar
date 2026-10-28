package xyz.sattar.javid.proqueue.core.billing

/**
 * In-app billing of the Android store this build was made for.
 *
 * The site build (APK on noobatyar.ir, and iOS) has none — it pays through the
 * Zibal gateway. The Myket build must use Myket's own billing: Myket treats
 * our plans and packs as digital products and rejects builds that send users
 * to a bank gateway. [StoreBillingProvider.billing] is set by the Android
 * entry point only in store builds, so "is there a store?" is simply
 * `billing != null`.
 *
 * The store only takes the money. Nothing is granted on the device: every
 * purchase token goes to our server, which verifies it with the store and
 * then activates the plan/pack (backend accounting/payment/myket.py).
 */
interface StoreBilling {
    /** Server-side store key, e.g. "myket". */
    val storeKey: String

    /** Runs the store's purchase UI for [sku], binding [payload] to the purchase. */
    suspend fun purchase(sku: String, payload: String): StorePurchaseResult

    /**
     * Purchases the store still holds as owned — paid, but not yet consumed by
     * our server (e.g. the app died between paying and verifying). They are
     * sent for verification again on app start so nobody pays for nothing.
     */
    suspend fun ownedPurchases(): List<OwnedPurchase>
}

sealed interface StorePurchaseResult {
    data class Success(val purchase: OwnedPurchase) : StorePurchaseResult
    data object Cancelled : StorePurchaseResult
    data class Failed(val message: String) : StorePurchaseResult
}

data class OwnedPurchase(
    val sku: String,
    val token: String,
    val orderId: String,
    val payload: String,
)

object StoreBillingProvider {
    var billing: StoreBilling? = null
}
