package xyz.sattar.javid.proqueue.domain.usecase.user

import xyz.sattar.javid.proqueue.core.billing.CheckoutConfirmation
import xyz.sattar.javid.proqueue.core.billing.OwnedPurchase
import xyz.sattar.javid.proqueue.core.billing.StoreBilling
import xyz.sattar.javid.proqueue.core.billing.StoreBillingProvider
import xyz.sattar.javid.proqueue.core.billing.StorePurchaseResult
import xyz.sattar.javid.proqueue.core.network.ApiResponse
import xyz.sattar.javid.proqueue.data.remoteDataSource.user.model.StoreVerifyRequestDto
import xyz.sattar.javid.proqueue.domain.UserRepository

/** What a purchase screen has to do next. */
sealed interface PurchaseOutcome {
    /** Site build: open this Zibal gateway URL. */
    data class Redirect(val url: String) : PurchaseOutcome
    /** Store build: paid in the store, verified and activated by the server. */
    data class Completed(val message: String) : PurchaseOutcome
    data object Cancelled : PurchaseOutcome
    data class Failed(val message: String) : PurchaseOutcome
}

/**
 * The one entry point for buying a plan or a pack.
 *
 * Site builds keep the Zibal flow unchanged (a gateway URL to open). Store
 * builds (Myket) never see a gateway: the server reserves the order and names
 * the store product, the store takes the money, and the server verifies the
 * purchase token with the store before activating anything.
 */
class PurchaseUseCase(
    private val repository: UserRepository,
    private val createPaymentUseCase: CreatePaymentUseCase,
    private val buyAddonUseCase: BuyAddonUseCase,
) {
    suspend fun plan(planId: Int): PurchaseOutcome {
        val billing = StoreBillingProvider.billing
        if (billing != null && planId == TRIAL_PLAN_ID) return PurchaseOutcome.Failed("سرویس آزمایشی قابل خرید و با تمدید نیست")
        confirmTotal(billing, KIND_PLAN, planId)?.let { return it }
        if (billing == null) return createPaymentUseCase(planId).toRedirect { it.paymentUrl }
        return viaStore(billing, KIND_PLAN, planId)
    }

    suspend fun addon(packId: Int): PurchaseOutcome {
        val billing = StoreBillingProvider.billing
        confirmTotal(billing, KIND_ADDON, packId)?.let { return it }
        if (billing == null) return buyAddonUseCase(packId).toRedirect { it.paymentUrl }
        return viaStore(billing, KIND_ADDON, packId)
    }

    /**
     * Shows the price + VAT + gateway/store cut and waits for the buyer to
     * accept it. Null means go ahead; otherwise the outcome to stop with.
     */
    private suspend fun confirmTotal(billing: StoreBilling?, kind: String, id: Int): PurchaseOutcome? {
        val channel = billing?.storeKey ?: CHANNEL_ZIBAL
        val quote = when (val r = repository.checkoutQuote(kind, id, channel)) {
            is ApiResponse.Success -> r.data
            is ApiResponse.Error -> return PurchaseOutcome.Failed(r.message)
        }
        if (quote.total <= 0) return null
        return if (CheckoutConfirmation.confirm(quote)) null else PurchaseOutcome.Cancelled
    }

    /**
     * Re-sends purchases the store still holds as owned — paid but never
     * verified, e.g. the app was killed right after paying. Safe to call on
     * every start: the server grants each token at most once. Returns the
     * success messages of anything that got activated now.
     */
    suspend fun recoverPending(): List<String> {
        val billing = StoreBillingProvider.billing ?: return emptyList()
        return runCatching { billing.ownedPurchases() }.getOrDefault(emptyList())
            .mapNotNull { owned -> (verify(billing, owned) as? PurchaseOutcome.Completed)?.message }
    }

    private suspend fun viaStore(billing: StoreBilling, kind: String, id: Int): PurchaseOutcome {
        val intent = when (val r = repository.storeIntent(billing.storeKey, kind, id)) {
            is ApiResponse.Success -> r.data
            is ApiResponse.Error -> return PurchaseOutcome.Failed(r.message)
        }
        return when (val result = billing.purchase(intent.sku, intent.payload)) {
            is StorePurchaseResult.Success -> verify(billing, result.purchase.copy(
                // Myket echoes the payload back, but ours is the source of truth.
                payload = result.purchase.payload.ifBlank { intent.payload }
            ))
            StorePurchaseResult.Cancelled -> PurchaseOutcome.Cancelled
            is StorePurchaseResult.Failed -> PurchaseOutcome.Failed(result.message)
        }
    }

    private suspend fun verify(billing: StoreBilling, purchase: OwnedPurchase): PurchaseOutcome =
        when (val r = repository.storeVerify(
            billing.storeKey,
            StoreVerifyRequestDto(purchase.payload, purchase.sku, purchase.token, purchase.orderId)
        )) {
            is ApiResponse.Success -> PurchaseOutcome.Completed(r.data.message.ifBlank { "خرید با موفقیت فعال شد" })
            // Paid in the store but not activated yet: the purchase stays owned
            // in the store, so recoverPending() retries it on the next start.
            is ApiResponse.Error -> PurchaseOutcome.Failed(
                r.message + "\nپرداخت شما محفوظ است و با باز کردن دوباره‌ی برنامه ثبت می‌شود."
            )
        }

    private inline fun <T> ApiResponse<T>.toRedirect(url: (T) -> String): PurchaseOutcome = when (this) {
        is ApiResponse.Success -> PurchaseOutcome.Redirect(url(data))
        is ApiResponse.Error -> PurchaseOutcome.Failed(message)
    }

    private companion object {
        const val KIND_PLAN = "plan"
        const val KIND_ADDON = "addon"
        const val TRIAL_PLAN_ID = 1
        const val CHANNEL_ZIBAL = "zibal"
    }
}
