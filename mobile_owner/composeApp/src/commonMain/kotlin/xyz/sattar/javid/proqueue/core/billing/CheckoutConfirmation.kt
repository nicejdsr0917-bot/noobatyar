package xyz.sattar.javid.proqueue.core.billing

import xyz.sattar.javid.proqueue.core.i18n.Str
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.material3.Switch
import androidx.compose.ui.Alignment
import kotlinx.coroutines.launch
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import xyz.sattar.javid.proqueue.core.utils.toPersianDigits
import xyz.sattar.javid.proqueue.data.remoteDataSource.user.model.CheckoutQuoteDto

/**
 * The "this is what you will pay" step before the Zibal gateway or the store
 * opens. Prices in the app are what Noobatyar keeps; VAT and the gateway's or
 * the store's cut are added on top (backend accounting/pricing.py), so the
 * buyer sees and accepts the full amount first.
 *
 * [PurchaseUseCase][xyz.sattar.javid.proqueue.domain.usecase.user.PurchaseUseCase]
 * suspends on [confirm]; [CheckoutConfirmHost], mounted once in App, shows
 * the dialog and resumes it. That keeps every buy button (home, add-ons,
 * create business, notifications) on the same dialog without each screen
 * carrying its own dialog state.
 */
object CheckoutConfirmation {
    /** The buyer accepted; [useWallet] when the toman wallet pays its part. */
    data class Choice(val useWallet: Boolean)

    private class Request(
        val quote: CheckoutQuoteDto,
        val requote: suspend (useWallet: Boolean) -> CheckoutQuoteDto?,
        val answer: CompletableDeferred<Choice?>,
    )

    private val pending = MutableStateFlow<Request?>(null)

    /**
     * Null when the buyer cancels. [requote] fetches the quote again with or
     * without the wallet when the buyer flips its switch (null keeps the old one).
     */
    suspend fun confirm(
        quote: CheckoutQuoteDto,
        requote: suspend (useWallet: Boolean) -> CheckoutQuoteDto? = { null },
    ): Choice? {
        val request = Request(quote, requote, CompletableDeferred())
        pending.value?.answer?.complete(null)
        pending.value = request
        return try {
            request.answer.await()
        } finally {
            if (pending.value === request) pending.value = null
        }
    }

    @Composable
    internal fun Host() {
        val request by pending.asStateFlow().collectAsState()
        val current = request ?: return
        var quote by remember(current) { mutableStateOf(current.quote) }
        var loading by remember(current) { mutableStateOf(false) }
        val scope = rememberCoroutineScope()
        CheckoutConfirmDialog(
            quote = quote,
            loading = loading,
            onToggleWallet = { on ->
                scope.launch {
                    loading = true
                    current.requote(on)?.let { quote = it }
                    loading = false
                }
            },
            onConfirm = { current.answer.complete(Choice(useWallet = quote.wallet > 0)) },
            onDismiss = { current.answer.complete(null) },
        )
    }
}

@Composable
fun CheckoutConfirmHost() = CheckoutConfirmation.Host()

@Composable
private fun CheckoutConfirmDialog(
    quote: CheckoutQuoteDto,
    loading: Boolean,
    onToggleWallet: (Boolean) -> Unit,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    // Myket's review only accepts "item price + VAT" on the invoice — naming
    // Myket's cut is not allowed — so on that channel the cut is shown as part
    // of the price. Same total either way: the backend derives Myket's VAT
    // from the store price (accounting/pricing.py), so total − VAT is exactly
    // the pre-VAT price the store lists.
    val isMyket = quote.channel == "myket"
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(Str.payment_details) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (quote.title.isNotBlank()) {
                    Text(quote.title, style = MaterialTheme.typography.titleSmall)
                }
                val usesWallet = quote.wallet > 0
                if (quote.walletAvailable > 0 || usesWallet) {
                    WalletSwitch(quote, checked = usesWallet, enabled = !loading, onToggle = onToggleWallet)
                }
                if (usesWallet) {
                    // Wallet first, then tax and fee on what is left (accounting/toman_wallet.py).
                    PriceRow(Str.price, quote.base)
                    PriceRow(Str.wallet_deduction, -quote.wallet)
                    if (quote.total > 0) {
                        PriceRow(Str.vat_10, quote.vat)
                        PriceRow(Str.payment_gateway_fee, quote.fee)
                    }
                } else if (isMyket) {
                    PriceRow(Str.price, quote.total - quote.vat)
                    PriceRow(Str.vat_10, quote.vat)
                } else {
                    PriceRow(Str.price, quote.base)
                    PriceRow(Str.vat_10, quote.vat)
                    PriceRow(Str.payment_gateway_fee, quote.fee)
                }
                HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                PriceRow(Str.amount_due, quote.total, bold = true)
            }
        },
        confirmButton = {
            Button(onClick = onConfirm, enabled = !loading) {
                Text(
                    when {
                        quote.wallet > 0 && quote.total <= 0 -> Str.pay_with_wallet
                        isMyket -> Str.pay_in_myket
                        else -> Str.go_to_payment_gateway
                    }
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(Str.cancel) }
        },
    )
}

@Composable
private fun WalletSwitch(quote: CheckoutQuoteDto, checked: Boolean, enabled: Boolean, onToggle: (Boolean) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(Str.pay_with_wallet, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
            Text(
                Str.wallet_balance_toman(formatToman(quote.walletBalance)).toPersianDigits(),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Switch(checked = checked, onCheckedChange = onToggle, enabled = enabled)
    }
}

@Composable
private fun PriceRow(label: String, amount: Long, bold: Boolean = false) {
    val weight = if (bold) FontWeight.Bold else FontWeight.Normal
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, style = MaterialTheme.typography.bodyMedium, fontWeight = weight)
        // A deduction keeps its minus in front of the number in RTL too.
        val text = if (amount < 0) Str.toman("\u2066−" + formatToman(-amount) + "\u2069") else Str.toman(formatToman(amount))
        Text(text.toPersianDigits(), style = MaterialTheme.typography.bodyMedium, fontWeight = weight)
    }
}

/** "12,500" — thousands grouped; callers localise the digits. */
fun formatToman(amount: Long): String =
    amount.toString().reversed().chunked(3).joinToString(",").reversed()
