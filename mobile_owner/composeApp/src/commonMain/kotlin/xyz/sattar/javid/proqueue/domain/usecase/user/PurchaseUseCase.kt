package xyz.sattar.javid.proqueue.domain.usecase.user

import xyz.sattar.javid.proqueue.core.i18n.Str
import xyz.sattar.javid.proqueue.core.billing.CheckoutConfirmation
import xyz.sattar.javid.proqueue.core.billing.CreditsChanged
import xyz.sattar.javid.proqueue.core.billing.OwnedPurchase
import xyz.sattar.javid.proqueue.core.billing.StoreBilling
import xyz.sattar.javid.proqueue.core.billing.StoreBillingProvider
import xyz.sattar.javid.proqueue.core.billing.StorePurchaseResult
import xyz.sattar.javid.proqueue.core.network.ApiResponse
import xyz.sattar.javid.proqueue.data.remoteDataSource.user.model.PaymentResponseDto
import xyz.sattar.javid.proqueue.data.remoteDataSource.user.model.StoreVerifyRequestDto
import xyz.sattar.javid.proqueue.domain.UserRepository

/** What a purchase screen has to do next. */
sealed interface PurchaseOutcome {
    /** Site build: open this Zibal gateway URL. */
    data class Redirect(val url: String) : PurchaseOutcome
    /** Paid in the store (verified by the server) or entirely from the toman wallet. */
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
        if (billing != null && planId == TRIAL_PLAN_ID) return PurchaseOutcome.Failed(Str.trial_plan_cant_be_purchased)
        val useWallet = when (val c = confirmTotal(billing, KIND_PLAN, planId)) {
            is Confirm.Stop -> return c.outcome
            is Confirm.Go -> c.useWallet
        }
        // The wallet is paid through the server's own endpoint; in a store build
        // it is only offered when it covers everything, so no gateway opens.
        if (billing == null || useWallet) {
            return createPaymentUseCase(planId, useWallet, walletOnly = billing != null).toOutcome(useWallet)
        }
        return viaStore(billing, KIND_PLAN, planId)
    }

    suspend fun addon(packId: Int): PurchaseOutcome {
        val billing = StoreBillingProvider.billing
        val useWallet = when (val c = confirmTotal(billing, KIND_ADDON, packId)) {
            is Confirm.Stop -> return c.outcome
            is Confirm.Go -> c.useWallet
        }
        if (billing == null || useWallet) {
            return buyAddonUseCase(packId, useWallet, walletOnly = billing != null).toOutcome(useWallet)
        }
        return viaStore(billing, KIND_ADDON, packId)
    }

    private sealed interface Confirm {
        data class Go(val useWallet: Boolean) : Confirm
        data class Stop(val outcome: PurchaseOutcome) : Confirm
    }

    /**
     * Shows the price + VAT + gateway/store cut and waits for the buyer to
     * accept it, offering the toman wallet when it can pay part or all of it.
     */
    private suspend fun confirmTotal(billing: StoreBilling?, kind: String, id: Int): Confirm {
        val channel = billing?.storeKey ?: CHANNEL_ZIBAL
        val quote = when (val r = repository.checkoutQuote(kind, id, channel)) {
            is ApiResponse.Success -> r.data
            is ApiResponse.Error -> return Confirm.Stop(PurchaseOutcome.Failed(r.message))
        }
        if (quote.total <= 0) return Confirm.Go(useWallet = false)
        val choice = CheckoutConfirmation.confirm(quote) { useWallet ->
            (repository.checkoutQuote(kind, id, channel, useWallet) as? ApiResponse.Success)?.data
        } ?: return Confirm.Stop(PurchaseOutcome.Cancelled)
        return Confirm.Go(choice.useWallet)
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
            is ApiResponse.Success -> {
                CreditsChanged.notifyChanged()
                PurchaseOutcome.Completed(r.data.message.ifBlank { Str.purchase_activated_successfully })
            }
            // Paid in the store but not activated yet: the purchase stays owned
            // in the store, so recoverPending() retries it on the next start.
            is ApiResponse.Error -> PurchaseOutcome.Failed(
                r.message + Str.nyour_payment_is_safe_and
            )
        }

    /** [usedWallet]: part of the wallet is now held for this payment, so balances changed. */
    private fun ApiResponse<PaymentResponseDto>.toOutcome(usedWallet: Boolean): PurchaseOutcome = when (this) {
        is ApiResponse.Success -> {
            if (data.paidWithWallet || usedWallet) CreditsChanged.notifyChanged()
            if (data.paidWithWallet) PurchaseOutcome.Completed(Str.purchase_activated_successfully)
            else PurchaseOutcome.Redirect(data.paymentUrl)
        }
        is ApiResponse.Error -> PurchaseOutcome.Failed(message)
    }

    private companion object {
        const val KIND_PLAN = "plan"
        const val KIND_ADDON = "addon"
        const val TRIAL_PLAN_ID = 1
        const val CHANNEL_ZIBAL = "zibal"
    }
}
