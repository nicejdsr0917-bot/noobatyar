package xyz.sattar.javid.proqueue.feature.wallet

import androidx.compose.foundation.background
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
                Text(
                    text = signedDelta(entry.delta).toPersianDigits(),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = accent
                )
                Text(
                    text = Str.wallet_balance_after(entry.balanceAfter.fa()),
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

internal fun walletMetricLabel(metric: String): String? = when (metric) {
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
    Debit("wallet_debit");

    val label: String
        get() = when (this) {
            AddonPurchase -> Str.wallet_reason_addon_purchase
            Booking -> Str.wallet_reason_booking
            CancellationRefund -> Str.wallet_reason_cancellation_refund
            SmsSend -> Str.wallet_reason_sms_send
            SmsRefund -> Str.wallet_reason_sms_refund
            Credit -> Str.wallet_reason_credit
            Debit -> Str.wallet_reason_debit
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
