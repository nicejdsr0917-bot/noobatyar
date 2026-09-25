package xyz.sattar.javid.proqueue.core.billing

import androidx.activity.ComponentActivity
import ir.myket.billingclient.IabHelper
import ir.myket.billingclient.util.Purchase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import xyz.sattar.javid.proqueue.BuildConfig
import kotlin.coroutines.resume

/**
 * Myket in-app billing (https://myket.ir/kb/pages/java/), wrapped as
 * suspend calls. All our products are *consumable*: the server consumes each
 * one after granting it, so the same plan/pack can be bought again.
 */
class MyketStoreBilling(private val activity: ComponentActivity) : AndroidStoreBilling {
    override val storeKey = "myket"

    private val helper = IabHelper(activity.applicationContext, BuildConfig.MYKET_IAB_PUBLIC_KEY)
    private val setupLock = Mutex()
    private var setupDone = false

    private suspend fun ensureSetup(): Boolean = setupLock.withLock {
        if (setupDone) return true
        setupDone = withContext(Dispatchers.Main) {
            suspendCancellableCoroutine { cont ->
                helper.startSetup { result -> if (cont.isActive) cont.resume(result.isSuccess) }
            }
        }
        setupDone
    }

    override suspend fun purchase(sku: String, payload: String): StorePurchaseResult =
        withContext(Dispatchers.Main) {
            suspendCancellableCoroutine { cont ->
                // Sets itself up if needed, and shows Myket's own
                // "install Myket" dialog when the store isn't on the phone.
                helper.launchPurchaseFlow(activity, sku, { result, purchase ->
                    if (!cont.isActive) return@launchPurchaseFlow
                    cont.resume(
                        when {
                            result.isSuccess && purchase != null -> StorePurchaseResult.Success(purchase.toOwned())
                            result.response == IabHelper.IABHELPER_USER_CANCELLED ||
                                result.response == IabHelper.BILLING_RESPONSE_RESULT_USER_CANCELED ->
                                StorePurchaseResult.Cancelled
                            result.response == IabHelper.BILLING_RESPONSE_RESULT_BILLING_UNAVAILABLE ->
                                StorePurchaseResult.Failed("برای خرید، اپلیکیشن مایکت باید روی گوشی نصب و به‌روز باشد.")
                            result.response == IabHelper.BILLING_RESPONSE_RESULT_ITEM_ALREADY_OWNED ->
                                StorePurchaseResult.Failed("خرید قبلی شما هنوز در حال ثبت است؛ چند لحظه بعد دوباره برنامه را باز کنید.")
                            else -> StorePurchaseResult.Failed("خرید در مایکت انجام نشد (${result.response}).")
                        }
                    )
                }, payload)
            }
        }

    override suspend fun ownedPurchases(): List<OwnedPurchase> {
        if (!ensureSetup()) return emptyList()
        return withContext(Dispatchers.Main) {
            suspendCancellableCoroutine { cont ->
                helper.queryInventoryAsync(false) { result, inventory ->
                    if (!cont.isActive) return@queryInventoryAsync
                    cont.resume(
                        if (result.isSuccess && inventory != null) inventory.allPurchases.map { it.toOwned() }
                        else emptyList()
                    )
                }
            }
        }
    }

    override fun dispose() {
        runCatching { helper.dispose() }
    }

    private fun Purchase.toOwned() = OwnedPurchase(
        sku = sku.orEmpty(),
        token = token.orEmpty(),
        orderId = orderId.orEmpty(),
        payload = developerPayload.orEmpty(),
    )
}
