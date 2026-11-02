package xyz.sattar.javid.proqueue.feature.wallet

import androidx.compose.foundation.background
import xyz.sattar.javid.proqueue.data.remoteDataSource.user.model.WalletConvertKind
import xyz.sattar.javid.proqueue.core.billing.formatToman
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Button
import androidx.compose.material3.AlertDialog
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.AccountBalanceWallet
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Event
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material.icons.rounded.Sms
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import org.koin.compose.viewmodel.koinViewModel
import xyz.sattar.javid.proqueue.core.i18n.Str
import xyz.sattar.javid.proqueue.core.ui.components.ContentWidth
import xyz.sattar.javid.proqueue.core.ui.components.EmptyState
import xyz.sattar.javid.proqueue.core.ui.components.ToastyHost
import xyz.sattar.javid.proqueue.core.ui.components.adaptivePagePadding
import xyz.sattar.javid.proqueue.core.ui.components.showToasty
import xyz.sattar.javid.proqueue.core.utils.DateTimeUtils
import xyz.sattar.javid.proqueue.core.utils.toPersianDigits
import xyz.sattar.javid.proqueue.data.remoteDataSource.user.model.WalletLedgerEntryDto
import xyz.sattar.javid.proqueue.data.remoteDataSource.user.model.WalletMetric

private val Green = Color(0xFF10B981)

private fun Int.fa() = toString().toPersianDigits()

/**
 * Add-on credit: what is left of each wallet and every purchase/spend that got
 * it there. Credit belongs to the user (shared by all their businesses) and is
 * spent only after the plan's monthly allowance runs out.
 */
@Composable
fun WalletScreen(
    viewModel: WalletViewModel = koinViewModel(),
    onNavigateBack: () -> Unit,
    onNavigateToAddons: () -> Unit = {}
) {
    val uiState by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(Unit) { viewModel.sendIntent(WalletIntent.Load) }

    LaunchedEffect(uiState.message) {
        uiState.message?.let {
            snackbarHostState.showToasty(it)
            viewModel.sendIntent(WalletIntent.ClearMessage)
        }
    }

    WalletContent(
        uiState = uiState,
        snackbarHostState = snackbarHostState,
        onNavigateBack = onNavigateBack,
        onNavigateToAddons = onNavigateToAddons,
        onIntent = viewModel::sendIntent
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WalletContent(
    uiState: WalletState,
    snackbarHostState: SnackbarHostState,
    onNavigateBack: () -> Unit,
    onNavigateToAddons: () -> Unit,
    onIntent: (WalletIntent) -> Unit
) {
    val listState = rememberLazyListState()
    val shouldLoadMore = remember {
        derivedStateOf {
            val total = listState.layoutInfo.totalItemsCount
            val lastVisible = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
            uiState.canLoadMore && !uiState.isLoading && !uiState.isPaginating &&
                total > 0 && lastVisible >= total - 3
        }
    }
    LaunchedEffect(shouldLoadMore.value) {
        if (shouldLoadMore.value) onIntent(WalletIntent.LoadMore)
    }

    var showConvert by remember { mutableStateOf(false) }
    // Closed by a successful conversion, left open on an error so the owner can retry.
    LaunchedEffect(uiState.convertedCount) { showConvert = false }
    val balances = uiState.balances
    if (showConvert && balances != null) {
        ConvertDialog(
            balance = balances.tomanWallet,
            smsUnitPrice = balances.smsUnitPrice,
            appointmentUnitPrice = balances.appointmentUnitPrice,
            busy = uiState.isConverting,
            onConvert = { kind, count -> onIntent(WalletIntent.Convert(kind, count)) },
            onDismiss = { showConvert = false }
        )
    }

    Scaffold(
        snackbarHost = { ToastyHost(hostState = snackbarHostState) },
        topBar = {
            TopAppBar(
                title = {
                    Text(Str.wallet_title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = null)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { paddingValues ->
        Box(
            modifier = Modifier.fillMaxSize().padding(paddingValues),
            contentAlignment = Alignment.TopCenter
        ) {
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize().widthIn(max = ContentWidth.List),
                contentPadding = PaddingValues(horizontal = adaptivePagePadding(), vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                item(key = "toman") {
                    TomanCard(
                        balance = uiState.balances?.tomanWallet,
                        canConvert = (uiState.balances?.tomanWallet ?: 0) > 0,
                        onConvert = { showConvert = true }
                    )
                }

                item(key = "explainer") {
                    Text(
                        text = Str.wallet_explainer,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                item(key = "balances") {
                    val balances = uiState.balances
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        BalanceCard(
                            modifier = Modifier.weight(1f),
                            icon = Icons.Rounded.Event,
                            label = Str.appointments_tab,
                            value = balances?.appointmentWallet,
                            idle = uiState.appointmentCreditIdle
                        )
                        BalanceCard(
                            modifier = Modifier.weight(1f),
                            icon = Icons.Rounded.Sms,
                            label = Str.sms,
                            value = balances?.smsWallet,
                            idle = false
                        )
                    }
                }

                if (uiState.appointmentCreditIdle && (uiState.balances?.appointmentWallet ?: 0) > 0) {
                    item(key = "unlimited-hint") {
                        Text(
                            text = Str.wallet_unlimited_hint,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                item(key = "buy") {
                    TextButton(onClick = onNavigateToAddons, modifier = Modifier.fillMaxWidth()) {
                        Icon(Icons.Rounded.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(Str.buy_add_on_pack, style = MaterialTheme.typography.labelLarge)
                    }
                }

                item(key = "history-title") {
                    Text(
                        text = Str.wallet_history,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                }

                when {
                    uiState.isLoading && uiState.entries.isEmpty() -> item(key = "loading") {
                        Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator()
                        }
                    }
                    uiState.loadFailed && uiState.entries.isEmpty() -> item(key = "error") {
                        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                            EmptyState(icon = Icons.Rounded.AccountBalanceWallet, title = Str.wallet_load_error)
                            TextButton(onClick = { onIntent(WalletIntent.Load) }) { Text(Str.sms_report_retry) }
                        }
                    }
                    uiState.entries.isEmpty() -> item(key = "empty") {
                        EmptyState(
                            modifier = Modifier.fillMaxWidth(),
                            icon = Icons.Rounded.AccountBalanceWallet,
                            title = Str.wallet_history_empty
                        )
                    }
                    else -> items(uiState.entries, key = { it.id }) { entry -> LedgerRow(entry) }
                }

                if (uiState.isPaginating) {
                    item(key = "paginating") {
                        Box(Modifier.fillMaxWidth().padding(12.dp), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun BalanceCard(
    modifier: Modifier,
    icon: ImageVector,
    label: String,
    value: Int?,
    idle: Boolean
) {
    // On an unlimited plan the appointment credit is kept, not spendable — dim it.
    val valueColor = if (idle) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.primary
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh)
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Spacer(Modifier.height(8.dp))
            Text(
                text = value?.fa() ?: "—",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Black,
                color = valueColor
            )
        }
    }
}

@Composable
private fun LedgerRow(entry: WalletLedgerEntryDto) {
    val gained = entry.delta >= 0
    val accent = if (gained) Green else MaterialTheme.colorScheme.error
    val time = entry.createdAt.takeIf { it.isNotBlank() }?.let {
        runCatching { DateTimeUtils.formatDateTime(DateTimeUtils.parseIsoToEpochMillis(it)) }.getOrNull()
    }
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier.size(36.dp).background(accent.copy(alpha = 0.12f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    if (gained) Icons.Rounded.Add else Icons.Rounded.Remove,
                    contentDescription = null,
                    tint = accent,
                    modifier = Modifier.size(18.dp)
                )
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(walletReasonLabel(entry), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                Text(
                    text = listOfNotNull(walletMetricLabel(entry.metric), time?.toPersianDigits()).joinToString(" · "),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Column(horizontalAlignment = Alignment.End) {
                val toman = entry.metric == WalletMetric.TOMAN
                Text(
                    text = if (toman) signedToman(entry.delta.toLong()) else signedAmount(entry.delta),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = accent
                )
                Text(
                    text = Str.wallet_balance_after(
                        if (toman) Str.toman(formatToman(entry.balanceAfter.toLong())).toPersianDigits()
                        else entry.balanceAfter.fa()
                    ),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

/** "+10" / "−1" — the sign always shown; localise digits at display. */
internal fun signedDelta(delta: Int): String =
    (if (delta >= 0) "+" else "−") + kotlin.math.abs(delta)

/**
 * [signedDelta] in local digits, inside a left-to-right isolate: in RTL text a
 * leading sign is otherwise laid out after the number ("۱۹+" instead of "+۱۹").
 */
fun signedAmount(delta: Int): String = "\u2066" + signedDelta(delta).toPersianDigits() + "\u2069"

internal fun walletMetricLabel(metric: String): String? = when (metric) {
    WalletMetric.TOMAN -> Str.toman_wallet_title
    WalletMetric.APPOINTMENTS -> Str.appointments_tab
    WalletMetric.SMS -> Str.sms
    else -> null
}

/** Ledger reason codes this app version can localise (backend `accounting/usage.py`). */
internal enum class WalletReason(val code: String) {
    AddonPurchase("addon_purchase"),
    Booking("booking"),
    CancellationRefund("cancellation_refund"),
    SmsSend("sms_send"),
    SmsRefund("sms_refund"),
    Credit("wallet_credit"),
    Debit("wallet_debit"),
    DiscountReimbursement("discount_reimbursement"),
    AdminCredit("admin_credit"),
    AdminDebit("admin_debit"),
    PlanPurchase("plan_purchase"),
    PurchaseRefund("purchase_refund"),
    Convert("convert"),
    ConvertedFromToman("toman_conversion");

    val label: String
        get() = when (this) {
            AddonPurchase -> Str.wallet_reason_addon_purchase
            Booking -> Str.wallet_reason_booking
            CancellationRefund -> Str.wallet_reason_cancellation_refund
            SmsSend -> Str.wallet_reason_sms_send
            SmsRefund -> Str.wallet_reason_sms_refund
            Credit, AdminCredit -> Str.wallet_reason_credit
            Debit, AdminDebit -> Str.wallet_reason_debit
            DiscountReimbursement -> Str.wallet_reason_discount
            PlanPurchase -> Str.wallet_reason_plan_purchase
            PurchaseRefund -> Str.wallet_reason_purchase_refund
            Convert -> Str.wallet_reason_convert
            ConvertedFromToman -> Str.wallet_reason_converted
        }

    companion object {
        /**
         * The reason to show for [entry], or null when the server's own
         * [WalletLedgerEntryDto.reasonLabel] should be shown instead (a code this
         * version doesn't know). Unknown code without a label → added/deducted by sign.
         */
        fun of(entry: WalletLedgerEntryDto): WalletReason? =
            entries.firstOrNull { it.code == entry.reason }
                ?: if (entry.reasonLabel.isNotBlank()) null
                else if (entry.delta >= 0) Credit else Debit
    }
}

internal fun walletReasonLabel(entry: WalletLedgerEntryDto): String =
    WalletReason.of(entry)?.label ?: entry.reasonLabel

/** "+10,000 تومان" with the sign kept in front of the number in RTL. */
internal fun signedToman(delta: Long): String =
    Str.toman("\u2066" + (if (delta >= 0) "+" else "−") + formatToman(kotlin.math.abs(delta)) + "\u2069").toPersianDigits()

/** How many credits [balance] toman buys at [unitPrice] each (0 when it is not for sale). */
internal fun affordableCount(balance: Long, unitPrice: Long): Int =
    if (unitPrice <= 0) 0 else (balance / unitPrice).coerceAtMost(Int.MAX_VALUE.toLong()).toInt()

@Composable
private fun TomanCard(balance: Long?, canConvert: Boolean, onConvert: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Rounded.AccountBalanceWallet, contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimaryContainer, modifier = Modifier.size(18.dp)
                )
                Spacer(Modifier.width(6.dp))
                Text(Str.toman_wallet_title, style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onPrimaryContainer)
            }
            Spacer(Modifier.height(8.dp))
            Text(
                text = balance?.let { Str.toman(formatToman(it)).toPersianDigits() } ?: "—",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Black,
                color = MaterialTheme.colorScheme.onPrimaryContainer
            )
            Spacer(Modifier.height(6.dp))
            Text(
                Str.toman_wallet_explainer,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
            )
            if (canConvert) {
                Spacer(Modifier.height(8.dp))
                Button(onClick = onConvert, modifier = Modifier.fillMaxWidth()) {
                    Text(Str.wallet_convert_button)
                }
            }
        }
    }
}

@Composable
private fun ConvertDialog(
    balance: Long,
    smsUnitPrice: Long,
    appointmentUnitPrice: Long,
    busy: Boolean,
    onConvert: (kind: String, count: Int) -> Unit,
    onDismiss: () -> Unit
) {
    var kind by remember { mutableStateOf(WalletConvertKind.SMS) }
    val unitPrice = if (kind == WalletConvertKind.SMS) smsUnitPrice else appointmentUnitPrice
    val max = affordableCount(balance, unitPrice)
    var count by remember(kind) { mutableStateOf(if (max > 0) 1 else 0) }
    val cost = unitPrice * count

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(Str.wallet_convert_title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = kind == WalletConvertKind.SMS,
                        onClick = { kind = WalletConvertKind.SMS },
                        label = { Text(Str.sms) }
                    )
                    FilterChip(
                        selected = kind == WalletConvertKind.APPOINTMENT,
                        onClick = { kind = WalletConvertKind.APPOINTMENT },
                        label = { Text(Str.appointments_tab) }
                    )
                }
                Text(
                    Str.wallet_convert_unit_price(Str.toman(formatToman(unitPrice))).toPersianDigits(),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center,
                    modifier = Modifier.fillMaxWidth()) {
                    IconButton(onClick = { count = (count - 1).coerceAtLeast(if (max > 0) 1 else 0) }, enabled = count > 1) {
                        Icon(Icons.Rounded.Remove, contentDescription = null)
                    }
                    Text(count.fa(), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 16.dp))
                    IconButton(onClick = { count = (count + 1).coerceAtMost(max) }, enabled = count < max) {
                        Icon(Icons.Rounded.Add, contentDescription = null)
                    }
                    TextButton(onClick = { count = max }, enabled = max > 0 && count < max) { Text(Str.wallet_convert_max) }
                }
                Text(
                    if (max == 0) Str.wallet_convert_not_enough
                    else Str.wallet_convert_cost(Str.toman(formatToman(cost))).toPersianDigits(),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold
                )
            }
        },
        confirmButton = {
            Button(onClick = { onConvert(kind, count) }, enabled = !busy && count in 1..max) {
                Text(Str.wallet_convert_confirm)
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(Str.cancel) } }
    )
}
