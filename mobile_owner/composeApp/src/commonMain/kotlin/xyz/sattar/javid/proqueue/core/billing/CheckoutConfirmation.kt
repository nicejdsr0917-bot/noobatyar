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
    private class Request(val quote: CheckoutQuoteDto, val answer: CompletableDeferred<Boolean>)

    private val pending = MutableStateFlow<Request?>(null)

    suspend fun confirm(quote: CheckoutQuoteDto): Boolean {
        val request = Request(quote, CompletableDeferred())
        pending.value?.answer?.complete(false)
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
        CheckoutConfirmDialog(
            quote = current.quote,
            onConfirm = { current.answer.complete(true) },
            onDismiss = { current.answer.complete(false) },
        )
    }
}

@Composable
fun CheckoutConfirmHost() = CheckoutConfirmation.Host()

@Composable
private fun CheckoutConfirmDialog(
    quote: CheckoutQuoteDto,
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
                if (isMyket) {
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
            Button(onClick = onConfirm) {
                Text(if (isMyket) Str.pay_in_myket else Str.go_to_payment_gateway)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(Str.cancel) }
        },
    )
}

@Composable
private fun PriceRow(label: String, amount: Long, bold: Boolean = false) {
    val weight = if (bold) FontWeight.Bold else FontWeight.Normal
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, style = MaterialTheme.typography.bodyMedium, fontWeight = weight)
        Text(Str.toman(amount.groupThousands()).toPersianDigits(), style = MaterialTheme.typography.bodyMedium, fontWeight = weight)
    }
}

private fun Long.groupThousands(): String =
    toString().reversed().chunked(3).joinToString(",").reversed()
