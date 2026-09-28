package xyz.sattar.javid.proqueue.feature.smsReport

import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.verticalScroll
import xyz.sattar.javid.proqueue.core.ui.components.adaptivePagePadding
import xyz.sattar.javid.proqueue.core.ui.components.isWideLayout
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.AccountBalanceWallet
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Sms
import androidx.compose.material.icons.rounded.Storefront
import androidx.compose.material.icons.rounded.WarningAmber
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel
import proqueue.composeapp.generated.resources.*
import xyz.sattar.javid.proqueue.core.ui.components.EmptyState
import xyz.sattar.javid.proqueue.core.ui.components.ListItemShimmer
import xyz.sattar.javid.proqueue.core.ui.components.ToastyHost
import xyz.sattar.javid.proqueue.core.utils.DateTimeUtils
import xyz.sattar.javid.proqueue.core.utils.toPersianDigits
import xyz.sattar.javid.proqueue.data.remoteDataSource.business.model.PushLogSummaryDto
import xyz.sattar.javid.proqueue.data.remoteDataSource.business.model.SmsLogStatus
import xyz.sattar.javid.proqueue.data.remoteDataSource.business.model.SmsLogSummaryDto
import xyz.sattar.javid.proqueue.data.remoteDataSource.business.model.SmsLogVisitorDto

private val Amber = Color(0xFFF59E0B)
private val Green = Color(0xFF10B981)
private val HeroViolets = listOf(Color(0xFF6D28D9), Color(0xFF8B5CF6), Color(0xFFA855F7))

/** Longest preview shown in a row before the message is cut with an ellipsis. */
private const val MESSAGE_PREVIEW_LENGTH = 110

private fun Int.fa() = toString().toPersianDigits()

/**
 * Everything the platform sent to customers on this business's behalf, per
 * channel (SMS — billed — and push — free).
 *
 * Layout: search lives in the top bar (where every list screen keeps it), the
 * channel switch sits right under it, and the month summary + filters scroll
 * away with the list instead of permanently eating half the screen.
 */
@Composable
fun SmsReportScreen(
    viewModel: SmsReportViewModel = koinViewModel(),
    onNavigateBack: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    val listState = rememberLazyListState()

    LaunchedEffect(Unit) { viewModel.sendIntent(SmsReportIntent.Load) }

    LaunchedEffect(uiState.message) {
        uiState.message?.let { snackbarHostState.showSnackbar(it) }
    }

    // Fire once the tail comes into view; per channel, since the two ledgers
    // paginate independently.
    val shouldLoadMore = remember {
        derivedStateOf {
            val total = listState.layoutInfo.totalItemsCount
            val lastVisible = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
            val canLoadMore = if (uiState.channel == ReportChannel.SMS) uiState.canLoadMore else uiState.pushCanLoadMore
            canLoadMore && !uiState.isLoading && !uiState.isPaginating &&
                total > 0 && lastVisible >= total - 3
        }
    }
    LaunchedEffect(shouldLoadMore.value) {
        if (shouldLoadMore.value) viewModel.sendIntent(SmsReportIntent.LoadMore)
    }

    ReportContent(
        uiState = uiState,
        snackbarHostState = snackbarHostState,
        listState = listState,
        onNavigateBack = onNavigateBack,
        onIntent = viewModel::sendIntent
    )
}

@Composable
private fun ReportContent(
    uiState: SmsReportState,
    snackbarHostState: SnackbarHostState,
    listState: LazyListState,
    onNavigateBack: () -> Unit,
    onIntent: (SmsReportIntent) -> Unit
) {
    var searching by rememberSaveable { mutableStateOf(uiState.searchQuery.isNotEmpty()) }
    val smsEmptySubtitle = stringResource(Res.string.sms_report_empty_subtitle)
    val errorTitle = stringResource(Res.string.sms_report_error_title)
    val retryLabel = stringResource(Res.string.sms_report_retry)

    Scaffold(
        snackbarHost = { ToastyHost(hostState = snackbarHostState) },
        topBar = {
            Column(Modifier.background(MaterialTheme.colorScheme.background)) {
                TopAppBar(
                    title = {
                        AnimatedContent(
                            targetState = searching,
                            transitionSpec = { fadeIn() togetherWith fadeOut() },
                            label = "search-toggle"
                        ) { isSearching ->
                            if (isSearching) {
                                SearchBarField(
                                    query = uiState.searchQuery,
                                    onQueryChange = { onIntent(SmsReportIntent.SetSearchQuery(it)) }
                                )
                            } else {
                                Text(
                                    stringResource(Res.string.sms_report_title),
                                    style = MaterialTheme.typography.titleLarge,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    },
                    navigationIcon = {
                        IconButton(onClick = onNavigateBack) {
                            Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = null)
                        }
                    },
                    actions = {
                        IconButton(onClick = {
                            if (searching && uiState.searchQuery.isNotEmpty()) {
                                onIntent(SmsReportIntent.SetSearchQuery(""))
                            }
                            searching = !searching
                        }) {
                            Icon(
                                if (searching) Icons.Rounded.Close else Icons.Rounded.Search,
                                contentDescription = stringResource(
                                    if (searching) Res.string.sms_report_search_clear
                                    else Res.string.sms_report_search_placeholder
                                )
                            )
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.background
                    )
                )
                ChannelSwitch(
                    selected = uiState.channel,
                    onSelect = { onIntent(SmsReportIntent.SelectChannel(it)) },
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp).padding(bottom = 8.dp)
                )
            }
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { paddingValues ->
        val wide = isWideLayout()
        val summaryBlock: @Composable () -> Unit = {
            if (uiState.channel == ReportChannel.SMS) {
                uiState.summary?.let { SmsSummaryHero(it) }
            } else {
                uiState.pushSummary?.let { PushSummaryHero(it) }
            }
        }
        val filterBlock: @Composable () -> Unit = {
            FilterBar(
                channel = uiState.channel,
                status = uiState.statusFilter,
                range = uiState.dateRangeFilter,
                onStatus = { onIntent(SmsReportIntent.SetStatusFilter(it)) },
                onRange = { onIntent(SmsReportIntent.SetDateRangeFilter(it)) }
            )
        }
        Row(modifier = Modifier.fillMaxSize().padding(paddingValues)) {
        // Wide: the totals and filters hold still in a side column while the
        // log scrolls beside them, so changing a filter never scrolls the
        // control you just used out of view.
        if (wide) {
            Column(
                modifier = Modifier
                    .width(340.dp)
                    .fillMaxHeight()
                    .verticalScroll(rememberScrollState())
                    .padding(start = 20.dp, top = 4.dp, bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                summaryBlock()
                filterBlock()
            }
        }
        LazyColumn(
            state = listState,
            modifier = Modifier.weight(1f).fillMaxHeight(),
            contentPadding = PaddingValues(
                start = adaptivePagePadding(),
                end = adaptivePagePadding(),
                top = 4.dp,
                bottom = 24.dp
            ),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            if (!wide) {
                item(key = "summary") { summaryBlock() }
                item(key = "filters") { filterBlock() }
            }
            if (uiState.channel == ReportChannel.SMS) {
                listBody(
                    uiState = uiState,
                    isEmpty = uiState.logs.isEmpty(),
                    emptyIcon = Icons.Rounded.Sms,
                    emptyTitle = "پیامکی پیدا نشد",
                    emptySubtitle = smsEmptySubtitle,
                    errorTitle = errorTitle,
                    retryLabel = retryLabel,
                    onRetry = { onIntent(SmsReportIntent.Load) }
                ) {
                    items(uiState.logs, key = { "sms-${it.id}" }) { log ->
                        LogRow(
                            visitor = log.visitor,
                            title = null,
                            body = log.messageText,
                            status = log.status,
                            errorDetail = log.errorDetail,
                            sentAt = log.sentAt,
                            channel = ReportChannel.SMS
                        )
                    }
                }
            } else {
                listBody(
                    uiState = uiState,
                    isEmpty = uiState.pushLogs.isEmpty(),
                    emptyIcon = Icons.Rounded.NotificationsActive,
                    emptyTitle = "اعلانی پیدا نشد",
                    emptySubtitle = "اعلان‌های یادآوری که برای گوشی مشتریان فرستاده می‌شود اینجا ثبت می‌شود.",
                    errorTitle = errorTitle,
                    retryLabel = retryLabel,
                    onRetry = { onIntent(SmsReportIntent.Load) }
                ) {
                    items(uiState.pushLogs, key = { "push-${it.id}" }) { log ->
                        LogRow(
                            visitor = log.visitor,
                            title = log.title,
                            body = log.body,
                            status = log.status,
                            errorDetail = log.errorDetail,
                            sentAt = log.sentAt,
                            channel = ReportChannel.PUSH
                        )
                    }
                }
            }
            val shownEmpty = if (uiState.channel == ReportChannel.SMS) uiState.logs.isEmpty() else uiState.pushLogs.isEmpty()
            if (uiState.isPaginating && !shownEmpty) {
                item(key = "paging") {
                    Box(Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.dp)
                    }
                }
            }
        }
        }
    }
}

/** Loading / error / empty / rows — one switch shared by both channels. */
private fun LazyListScope.listBody(
    uiState: SmsReportState,
    isEmpty: Boolean,
    emptyIcon: ImageVector,
    emptyTitle: String,
    emptySubtitle: String,
    errorTitle: String,
    retryLabel: String,
    onRetry: () -> Unit,
    rows: LazyListScope.() -> Unit
) {
    when {
        // isPaginating also covers the first page of a freshly selected
        // channel/filter; without it the empty state flashed under a spinner.
        (uiState.isLoading || uiState.isPaginating) && isEmpty -> items(5) { ListItemShimmer(height = 92.dp) }
        uiState.errorMessage != null && isEmpty -> item(key = "error") {
            Column(
                Modifier.fillMaxWidth().padding(vertical = 40.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Icon(Icons.Rounded.ErrorOutline, null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(36.dp))
                Spacer(Modifier.height(8.dp))
                Text(errorTitle, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(4.dp))
                Text(uiState.errorMessage.orEmpty(), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(12.dp))
                Button(onClick = onRetry) { Text(retryLabel) }
            }
        }
        isEmpty -> item(key = "empty") {
            EmptyState(
                modifier = Modifier.fillMaxWidth().padding(vertical = 24.dp),
                icon = emptyIcon,
                title = emptyTitle,
                subtitle = emptySubtitle
            )
        }
        else -> rows()
    }
}

/** Search input that lives inside the top bar. */
@Composable
private fun SearchBarField(query: String, onQueryChange: (String) -> Unit) {
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(end = 4.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(Icons.Rounded.Search, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(8.dp))
        Box(Modifier.weight(1f)) {
            if (query.isEmpty()) {
                Text(
                    stringResource(Res.string.sms_report_search_placeholder),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            BasicTextField(
                value = query,
                onValueChange = onQueryChange,
                singleLine = true,
                textStyle = MaterialTheme.typography.bodyMedium.copy(color = MaterialTheme.colorScheme.onSurface),
                cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                modifier = Modifier.fillMaxWidth().focusRequester(focus)
            )
        }
    }
}

/** Pill segmented switch between the two ledgers. */
@Composable
private fun ChannelSwitch(selected: ReportChannel, onSelect: (ReportChannel) -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        listOf(
            Triple(ReportChannel.SMS, Icons.Rounded.Sms, "پیامک‌ها"),
            Triple(ReportChannel.PUSH, Icons.Rounded.NotificationsActive, "اعلان‌ها"),
        ).forEach { (channel, icon, label) ->
            val on = channel == selected
            Row(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(12.dp))
                    .background(if (on) MaterialTheme.colorScheme.primary else Color.Transparent)
                    .clickable { onSelect(channel) }
                    .padding(vertical = 10.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    icon, null,
                    tint = if (on) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    label,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = if (on) FontWeight.Bold else FontWeight.Medium,
                    color = if (on) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

/**
 * The month's SMS picture. `monthly_quota == -1` means unlimited, in which case
 * "remaining" is meaningless and the progress bar is dropped.
 */
@Composable
private fun SmsSummaryHero(summary: SmsLogSummaryDto) {
    val unlimited = summary.monthlyQuota < 0
    val remaining = (summary.monthlyQuota - summary.monthlyUsed).coerceAtLeast(0)
    HeroBox {
        Row(verticalAlignment = Alignment.Bottom) {
            Column(Modifier.weight(1f)) {
                Text(stringResource(Res.string.sms_report_summary_title), color = Color.White.copy(alpha = 0.85f),
                    style = MaterialTheme.typography.labelMedium)
                Spacer(Modifier.height(2.dp))
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(
                        if (unlimited) stringResource(Res.string.sms_report_summary_unlimited) else remaining.fa(),
                        color = Color.White, fontWeight = FontWeight.ExtraBold,
                        style = MaterialTheme.typography.headlineMedium
                    )
                    if (!unlimited) {
                        Spacer(Modifier.width(6.dp))
                        Text("از ${summary.monthlyQuota.fa()} باقی‌مانده", color = Color.White.copy(alpha = 0.8f),
                            style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(bottom = 6.dp))
                    }
                }
            }
            Row(
                modifier = Modifier.clip(RoundedCornerShape(50)).background(Color.White.copy(alpha = 0.18f))
                    .padding(horizontal = 10.dp, vertical = 5.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Rounded.AccountBalanceWallet, null, tint = Color.White, modifier = Modifier.size(14.dp))
                Spacer(Modifier.width(4.dp))
                Text("کیف پول ${summary.walletBalance.fa()}", color = Color.White, style = MaterialTheme.typography.labelSmall)
            }
        }
        if (!unlimited && summary.monthlyQuota > 0) {
            Spacer(Modifier.height(12.dp))
            Box(Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)).background(Color.White.copy(alpha = 0.22f))) {
                Box(
                    Modifier.fillMaxHeight()
                        .fillMaxWidth((summary.monthlyUsed.toFloat() / summary.monthlyQuota).coerceIn(0f, 1f))
                        .clip(RoundedCornerShape(3.dp)).background(Color.White)
                )
            }
        }
        Spacer(Modifier.height(14.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            HeroStat(stringResource(Res.string.sms_report_summary_used), summary.monthlyUsed.fa(), Modifier.weight(1f))
            HeroStat(stringResource(Res.string.sms_report_summary_sent), summary.sentThisMonth.fa(), Modifier.weight(1f))
            HeroStat(stringResource(Res.string.sms_report_summary_failed), summary.failedThisMonth.fa(), Modifier.weight(1f))
        }
    }
}

@Composable
private fun PushSummaryHero(summary: PushLogSummaryDto) {
    HeroBox {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Rounded.NotificationsActive, null, tint = Color.White)
            Spacer(Modifier.width(8.dp))
            Column(Modifier.weight(1f)) {
                Text("اعلان‌های این ماه", color = Color.White, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleSmall)
                Text("اعلان رایگان است و از اعتبار پیامک کم نمی‌کند", color = Color.White.copy(alpha = 0.8f),
                    style = MaterialTheme.typography.labelSmall)
            }
        }
        Spacer(Modifier.height(14.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            HeroStat("ارسال‌شده", summary.sentThisMonth.fa(), Modifier.weight(1f))
            HeroStat("ناموفق", summary.failedThisMonth.fa(), Modifier.weight(1f))
        }
    }
}

@Composable
private fun HeroBox(content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(Brush.linearGradient(HeroViolets))
            .padding(18.dp),
        content = content
    )
}

@Composable
private fun HeroStat(label: String, value: String, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.clip(RoundedCornerShape(14.dp)).background(Color.White.copy(alpha = 0.16f))
            .padding(vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(value, color = Color.White, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleSmall)
        Text(label, color = Color.White.copy(alpha = 0.8f), style = MaterialTheme.typography.labelSmall)
    }
}

/** Status chips and the date range, on one line. */
@Composable
private fun FilterBar(
    channel: ReportChannel,
    status: String?,
    range: SmsReportDateRange,
    onStatus: (String?) -> Unit,
    onRange: (SmsReportDateRange) -> Unit
) {
    val ranges = listOf(
        SmsReportDateRange.ALL to Res.string.sms_report_date_filter_all,
        SmsReportDateRange.TODAY to Res.string.sms_report_date_filter_today,
        SmsReportDateRange.THIS_WEEK to Res.string.sms_report_date_filter_week,
        SmsReportDateRange.THIS_MONTH to Res.string.sms_report_date_filter_month
    )
    var menuOpen by remember { mutableStateOf(false) }

    val statusChips: @Composable () -> Unit = {
        StatusChip(status == null, stringResource(Res.string.sms_report_filter_all)) { onStatus(null) }
        StatusChip(status == SmsLogStatus.SENT, stringResource(Res.string.sms_report_filter_sent)) { onStatus(SmsLogStatus.SENT) }
        StatusChip(status == SmsLogStatus.FAILED, stringResource(Res.string.sms_report_filter_failed)) { onStatus(SmsLogStatus.FAILED) }
        // Push is free, so it has no quota-skipped state.
        if (channel == ReportChannel.SMS) {
            StatusChip(status == SmsLogStatus.SKIPPED_QUOTA, stringResource(Res.string.sms_report_filter_skipped)) {
                onStatus(SmsLogStatus.SKIPPED_QUOTA)
            }
        }
    }
    val dateChip: @Composable () -> Unit = {
        Box {
            FilterChip(
                selected = range != SmsReportDateRange.ALL,
                onClick = { menuOpen = true },
                leadingIcon = { Icon(Icons.Rounded.CalendarMonth, null, Modifier.size(16.dp)) },
                trailingIcon = { Icon(Icons.Rounded.KeyboardArrowDown, null, Modifier.size(16.dp)) },
                label = { Text(stringResource(ranges.first { it.first == range }.second)) },
                shape = RoundedCornerShape(12.dp)
            )
            DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                ranges.forEach { (r, label) ->
                    DropdownMenuItem(
                        text = { Text(stringResource(label), fontWeight = if (r == range) FontWeight.Bold else null) },
                        onClick = { menuOpen = false; onRange(r) }
                    )
                }
            }
        }
    }

    if (isWideLayout()) {
        // The side column is narrow and a mouse can't scroll a row sideways,
        // so the chips wrap onto as many lines as they need.
        Column(
            modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            dateChip()
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) { statusChips() }
        }
        return
    }

    Row(
        modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        dateChip()
        Spacer(Modifier.width(8.dp))
        VerticalDivider(Modifier.height(24.dp))
        Spacer(Modifier.width(8.dp))
        Row(
            modifier = Modifier.weight(1f).horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) { statusChips() }
    }
}

@Composable
private fun StatusChip(selected: Boolean, label: String, onClick: () -> Unit) {
    FilterChip(selected = selected, onClick = onClick, label = { Text(label) }, shape = RoundedCornerShape(12.dp))
}

/** One sent message: status badge, recipient and time on top, the text under it. */
@Composable
private fun LogRow(
    visitor: SmsLogVisitorDto?,
    title: String?,
    body: String,
    status: String,
    errorDetail: String?,
    sentAt: String?,
    channel: ReportChannel
) {
    val failed = status == SmsLogStatus.FAILED
    val skipped = status == SmsLogStatus.SKIPPED_QUOTA
    val (statusIcon, statusColor, statusLabel) = when {
        failed -> Triple(Icons.Rounded.ErrorOutline, MaterialTheme.colorScheme.error, stringResource(Res.string.sms_report_status_failed))
        skipped -> Triple(Icons.Rounded.WarningAmber, Amber, stringResource(Res.string.sms_report_status_skipped))
        else -> Triple(Icons.Rounded.CheckCircle, Green, stringResource(Res.string.sms_report_status_sent))
    }
    // A null visitor means the message went to the owner (new-booking notice).
    val toOwner = visitor == null
    val recipient = visitor?.let { it.fullName.ifBlank { it.phoneNumber } }
        ?: stringResource(Res.string.sms_report_owner_recipient)
    val time = sentAt?.takeIf { it.isNotBlank() }?.let {
        runCatching { DateTimeUtils.formatDateTime(DateTimeUtils.parseIsoToEpochMillis(it)) }.getOrNull()
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
    ) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.Top) {
            Box(
                Modifier.size(38.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primary.copy(alpha = 0.10f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    if (toOwner) Icons.Rounded.Storefront else Icons.Rounded.Person, null,
                    tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp)
                )
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        recipient, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold,
                        maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f)
                    )
                    Row(
                        Modifier.clip(RoundedCornerShape(50)).background(statusColor.copy(alpha = 0.12f))
                            .padding(horizontal = 8.dp, vertical = 3.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(statusIcon, null, tint = statusColor, modifier = Modifier.size(12.dp))
                        Spacer(Modifier.width(3.dp))
                        Text(statusLabel, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = statusColor)
                    }
                }
                val sub = listOfNotNull(visitor?.phoneNumber?.takeIf { it.isNotBlank() }?.toPersianDigits(), time)
                if (sub.isNotEmpty()) {
                    Text(
                        sub.joinToString("  ·  "), style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Spacer(Modifier.height(8.dp))
                if (channel == ReportChannel.PUSH && !title.isNullOrBlank()) {
                    Text(title, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(2.dp))
                }
                Text(
                    body.withoutFooter().previewText(), style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    lineHeight = MaterialTheme.typography.bodySmall.lineHeight * 1.25f
                )
                if ((failed || skipped) && !errorDetail.isNullOrBlank()) {
                    Spacer(Modifier.height(6.dp))
                    Text(errorDetail.humanError(), style = MaterialTheme.typography.labelSmall, color = statusColor)
                }
            }
        }
    }
}

private fun String.previewText(): String =
    if (length <= MESSAGE_PREVIEW_LENGTH) this else take(MESSAGE_PREVIEW_LENGTH).trimEnd() + "…"

/**
 * Every SMS ends with the same footer and لغو11 (api/sms.py signed()); in a
 * list of dozens it is pure noise, so rows show only what differs.
 */
private fun String.withoutFooter(): String =
    lines()
        .filterNot { it.trim() == "نوبت‌یار" || it.trim() == "لغو11" }
        .joinToString("\n")
        .replace(Regex("\n{2,}"), "\n")
        .trim()

/**
 * Provider rejections are stored raw (e.g. `{'status': 'ارسال نشده'}`); show
 * the provider's own message instead of the Python dict around it.
 */
private fun String.humanError(): String {
    Regex("""['"]status['"]\s*:\s*['"]([^'"]+)['"]""").find(this)?.let {
        return "پاسخ سامانه‌ی پیامک: ${it.groupValues[1]}"
    }
    return if (trimStart().startsWith("{")) "سامانه‌ی پیامک ارسال را نپذیرفت" else this
}
