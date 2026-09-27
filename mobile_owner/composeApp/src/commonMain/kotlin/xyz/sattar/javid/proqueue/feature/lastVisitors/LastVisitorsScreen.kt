package xyz.sattar.javid.proqueue.feature.lastVisitors

import xyz.sattar.javid.proqueue.core.navigation.MainTab
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.flow.Flow
import org.jetbrains.compose.resources.stringResource
import org.jetbrains.compose.ui.tooling.preview.Preview
import org.koin.compose.viewmodel.koinViewModel
import proqueue.composeapp.generated.resources.*
import xyz.sattar.javid.proqueue.core.ui.collectWithLifecycleAware
import xyz.sattar.javid.proqueue.core.ui.components.BottomBarDefaults
import xyz.sattar.javid.proqueue.core.ui.components.BottomBarSpacer
import xyz.sattar.javid.proqueue.core.ui.components.MainTopAppBar
import xyz.sattar.javid.proqueue.core.ui.components.AppButton
import xyz.sattar.javid.proqueue.core.ui.components.EmptyState
import xyz.sattar.javid.proqueue.core.ui.components.ListItemShimmer
import xyz.sattar.javid.proqueue.core.ui.components.CollapsibleReceipt
import xyz.sattar.javid.proqueue.core.ui.components.CollapsingHeader
import xyz.sattar.javid.proqueue.core.ui.components.PullToRefreshBox
import xyz.sattar.javid.proqueue.core.ui.components.rememberCollapsingHeaderState
import xyz.sattar.javid.proqueue.core.ui.components.QueueItemCard
import xyz.sattar.javid.proqueue.core.ui.components.SectionTabs
import xyz.sattar.javid.proqueue.core.ui.components.ToastyHost
import xyz.sattar.javid.proqueue.core.ui.components.showToasty
import xyz.sattar.javid.proqueue.core.utils.DateTimeUtils
import xyz.sattar.javid.proqueue.domain.model.appointment.AppointmentOrdering
import xyz.sattar.javid.proqueue.domain.model.appointment.AppointmentWithDetails
import xyz.sattar.javid.proqueue.feature.home.QueueItem
import xyz.sattar.javid.proqueue.ui.theme.AppTheme
import kotlin.math.abs

@Composable
fun LastVisitorsScreen(
    viewModel: LastVisitorsViewModel = koinViewModel<LastVisitorsViewModel>(),
    initialStatus: String? = null,
    initialTab: Int? = null,
    initialDateFrom: Long? = null,
    initialDateTo: Long? = null,
    onNavigateToCreateAppointment: () -> Unit = {},
    onNavigateToEditAppointment: (Long) -> Unit = {},
    onNavigateToVisitorDetails: (Long) -> Unit = {},
    onNavigateToLogin: () -> Unit = {},
    onChangeBusiness: () -> Unit = {},
) {
    val uiState by viewModel.uiState.collectAsState()

    // Initial load happens once in LastVisitorsViewModel.init (it observes the
    // selected business). Not re-triggered here, so switching back to this tab
    // does not re-request the server.

    // Applies the filter/tab a Home stat card, the queue row, or the 7-day
    // chart navigated in with. Reuses the existing OnFilterChanged/OnTabSelected
    // intents rather than a parallel filtering mechanism. Keyed on the actual
    // arg values (not Unit) so re-navigating here with different args — e.g.
    // tapping a different stat card without leaving the tab stack — re-applies.
    LaunchedEffect(initialStatus, initialTab, initialDateFrom, initialDateTo) {
        if (initialTab != null) {
            viewModel.sendIntent(LastVisitorsIntent.OnTabSelected(initialTab))
        }
        if (initialStatus != null || initialDateFrom != null || initialDateTo != null) {
            viewModel.sendIntent(
                LastVisitorsIntent.OnFilterChanged(
                    AppointmentFilter(
                        status = initialStatus,
                        dateFrom = initialDateFrom,
                        dateTo = initialDateTo
                    )
                )
            )
        }
    }

    HandleEvents(
        events = viewModel.events,
        onNavigateToCreateAppointment = onNavigateToCreateAppointment,
        onNavigateToEditAppointment = onNavigateToEditAppointment,
        onNavigateToVisitorDetails = onNavigateToVisitorDetails
    )

    LastVisitorsScreenContent(
        uiState = uiState,
        onIntent = viewModel::sendIntent,
        onNavigateToLogin = onNavigateToLogin,
        onChangeBusiness = onChangeBusiness,
        onGenerateMessage = viewModel::generateReminderMessage
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LastVisitorsScreenContent(
    modifier: Modifier = Modifier,
    uiState: LastVisitorsState,
    onIntent: (LastVisitorsIntent) -> Unit,
    onNavigateToLogin: () -> Unit = {},
    onChangeBusiness: () -> Unit = {},
    onGenerateMessage: (Long, String, String, String, Long, String, Int?) -> String
) {
    // This screen previously had no toast host at all, so every error and
    // (once added) success confirmation from LastVisitorsState.message — delete,
    // complete, no-show, approve/reject, send-message — vanished silently.
    val snackbarHostState = remember { SnackbarHostState() }
    LaunchedEffect(uiState.message) {
        uiState.message?.let {
            snackbarHostState.showToasty(it)
            onIntent(LastVisitorsIntent.ClearMessage)
        }
    }

    // ── Space budget ──────────────────────────────────────────────────────
    // This screen is worked through, not read: the owner is here to find a
    // booking and act on it. It used to open with the app bar, tabs, two
    // date fields, three presets and a count card stacked above the list —
    // close to half a phone screen before the first row. Now only the tabs
    // are pinned; the app bar and the one-line filter strip slide away as the
    // list scrolls and come straight back on the way up, the date controls
    // live in a drop-down off the strip, and the + button steps aside while
    // the list is moving so it doesn't sit on top of a row's action button.
    val topBarBehavior = TopAppBarDefaults.enterAlwaysScrollBehavior(rememberTopAppBarState())
    val headerState = rememberCollapsingHeaderState()
    var dateExpanded by rememberSaveable { mutableStateOf(false) }

    // Another tab or another window is another list: start it with the
    // header in view, so what the list covers is never off screen.
    LaunchedEffect(uiState.selectedTab, uiState.filter) {
        headerState.expand()
        topBarBehavior.state.heightOffset = 0f
    }
    // Once the strip has scrolled out of sight, fold its date panel away too,
    // so it comes back as one line rather than the whole panel.
    LaunchedEffect(headerState) {
        snapshotFlow { headerState.collapsedFraction >= 1f }
            .collect { hidden -> if (hidden) dateExpanded = false }
    }
    val fabVisible by remember {
        derivedStateOf { topBarBehavior.state.collapsedFraction < 0.5f }
    }

    val isReport = uiState.selectedTab == RANGE_REPORT_TAB
    val isQueue = uiState.selectedTab == 1
    val appointments = remember(uiState.appointments) {
        uiState.appointments.distinctBy { it.appointment.id }
    }
    val waiting = remember(appointments) {
        val now = DateTimeUtils.systemCurrentMilliseconds()
        appointments
            .filter { it.appointment.status == "WAITING" || it.appointment.status == "PENDING_APPROVAL" || it.appointment.status == "PENDING_VERIFICATION" }
            .sortedBy { abs(it.appointment.appointmentDate - now) }
    }

    Scaffold(
        modifier = Modifier.nestedScroll(topBarBehavior.nestedScrollConnection),
        contentWindowInsets = WindowInsets(0),
        snackbarHost = { ToastyHost(hostState = snackbarHostState) },
        topBar = {
            // Refresh is pull-to-refresh (or a double-tap on the tab) and the
            // filter lives on the strip, so the bar keeps nothing the list
            // depends on while it is scrolled away.
            MainTopAppBar(
                onNavigateToLogin = onNavigateToLogin,
                onChangeBusiness = onChangeBusiness,
                scrollBehavior = topBarBehavior
            )
        },
        floatingActionButton = {
            AnimatedVisibility(
                visible = fabVisible,
                enter = scaleIn() + fadeIn(),
                exit = scaleOut() + fadeOut()
            ) {
                FloatingActionButton(
                    onClick = {
                        onIntent(LastVisitorsIntent.OnCreateAppointmentClick)
                    },
                    modifier = Modifier.padding(bottom = BottomBarDefaults.FabClearance),
                    containerColor = MaterialTheme.colorScheme.primary,
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Add,
                        contentDescription = stringResource(Res.string.create_appointment)
                    )
                }
            }
        }
    ) { paddingValues ->
        Box(
            modifier = modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .padding(top = paddingValues.calculateTopPadding())
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .nestedScroll(headerState.connection)
            ) {
                VisitorsTabBar(
                    selectedIndex = uiState.selectedTab,
                    onSelected = { index -> onIntent(LastVisitorsIntent.OnTabSelected(index)) }
                )

                CollapsingHeader(state = headerState) {
                    Column {
                        VisitorsFilterStrip(
                            from = uiState.filter.dateFrom ?: 0L,
                            to = uiState.filter.dateTo ?: 0L,
                            dateExpanded = dateExpanded,
                            onToggleDate = { dateExpanded = !dateExpanded },
                            // The report counts every status, so a status
                            // filter has nothing to say there.
                            status = uiState.filter.status.takeUnless { isReport },
                            onClearStatus = {
                                onIntent(LastVisitorsIntent.OnFilterChanged(uiState.filter.copy(status = null)))
                            },
                            count = when {
                                isReport -> uiState.rangeReport?.customersTotal
                                uiState.isLoading -> null
                                isQueue -> waiting.size
                                else -> uiState.totalCount
                            },
                            countLabel = when {
                                isReport -> "مشتری"
                                isQueue -> "در صف"
                                else -> "نوبت"
                            },
                            filterActive = uiState.filter.status != null ||
                                uiState.filter.ordering != AppointmentFilter().ordering,
                            onOpenFilter = if (isReport) null else {
                                { onIntent(LastVisitorsIntent.ShowFilterSheet(true)) }
                            }
                        )
                        AnimatedVisibility(
                            visible = dateExpanded,
                            enter = expandVertically() + fadeIn(),
                            exit = shrinkVertically() + fadeOut()
                        ) {
                            VisitorsDateBar(
                                from = uiState.filter.dateFrom ?: 0L,
                                to = uiState.filter.dateTo ?: 0L,
                                onRangeChange = { from, to ->
                                    onIntent(LastVisitorsIntent.OnDateRangeChanged(from, to))
                                },
                                onPresetSelected = { dateExpanded = false }
                            )
                        }
                    }
                }

                PullToRefreshBox(
                    isRefreshing = if (isReport) uiState.rangeLoading else uiState.isLoading,
                    onRefresh = { onIntent(LastVisitorsIntent.LoadAppointments) },
                    refreshTab = MainTab.LastVisitors,
                    modifier = Modifier.weight(1f).fillMaxWidth()
                ) {
                    when {
                        // Ahead of the loading and empty checks: this tab carries
                        // its own data, so gating it on the lists' appointments
                        // would hide it exactly when it's most useful — a window
                        // with nothing waiting still has a history to report on.
                        isReport -> {
                            RangeReportSection(
                                uiState = uiState,
                                onIntent = onIntent,
                                onCustomerClick = { visitorId ->
                                    onIntent(LastVisitorsIntent.OnAppointmentClick(visitorId))
                                }
                            )
                        }

                        uiState.isLoading -> {
                            Column(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(horizontal = 16.dp, vertical = 4.dp),
                                verticalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                repeat(7) { ListItemShimmer(height = 72.dp) }
                            }
                        }

                        isQueue && waiting.isEmpty() -> ScrollableEmptyState(
                            title = stringResource(Res.string.empty_appointments_title),
                            subtitle = stringResource(Res.string.empty_appointments_subtitle)
                        )

                        appointments.isEmpty() -> ScrollableEmptyState(
                            title = "در این بازه نوبتی نیست",
                            // Kept to one short line: the + button floats
                            // over this area and a long subtitle ran under it.
                            subtitle = "بازه‌ی دیگری را انتخاب کنید"
                        )

                        isQueue -> QueueList(
                            waiting = waiting,
                            onIntent = onIntent,
                            onGenerateMessage = onGenerateMessage
                        )

                        else -> AppointmentsList(
                            appointments = appointments,
                            onEditClick = { appointmentId ->
                                onIntent(LastVisitorsIntent.OnEditAppointment(appointmentId))
                            },
                            onDeleteClick = { appointmentId ->
                                onIntent(LastVisitorsIntent.OnDeleteAppointment(appointmentId))
                            },
                            onItemClick = { visitorId ->
                                onIntent(LastVisitorsIntent.OnAppointmentClick(visitorId))
                            }
                        )
                    }
                }
            }

            if (uiState.showFilterSheet) {
                FilterBottomSheet(
                    filter = uiState.filter,
                    onDismiss = { onIntent(LastVisitorsIntent.ShowFilterSheet(false)) },
                    onFilterChanged = { onIntent(LastVisitorsIntent.OnFilterChanged(it)) },
                    onClearFilter = { onIntent(LastVisitorsIntent.ClearFilter) }
                )
            }
        }
    }
}

/**
 * An empty list that can still be pulled to refresh: the message sits in a
 * one-item lazy list the height of the viewport, so the pull gesture has
 * something to scroll against.
 */
@Composable
private fun ScrollableEmptyState(title: String, subtitle: String) {
    LazyColumn(modifier = Modifier.fillMaxSize()) {
        item {
            Box(
                modifier = Modifier.fillParentMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                EmptyState(icon = Icons.Rounded.EventNote, title = title, subtitle = subtitle)
            }
        }
    }
}

@Composable
private fun QueueList(
    waiting: List<AppointmentWithDetails>,
    onIntent: (LastVisitorsIntent) -> Unit,
    onGenerateMessage: (Long, String, String, String, Long, String, Int?) -> String
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        items(waiting, key = { it.appointment.id }) { item ->
            val duration = (item.appointment.serviceDuration
                ?: item.business.defaultServiceDuration) * 60 * 1000L
            val queueItem = QueueItem(
                appointment = item.appointment,
                visitorName = item.visitor.fullName,
                visitorPhone = item.visitor.phoneNumber,
                estimatedStartTime = item.appointment.appointmentDate,
                estimatedEndTime = item.appointment.appointmentDate + duration
            )
            Box(modifier = Modifier.animateItem()) {
                QueueItemCard(
                    item = queueItem,
                    onRemove = {
                        onIntent(LastVisitorsIntent.OnDeleteAppointment(queueItem.appointment.id))
                    },
                    onComplete = {
                        when (queueItem.appointment.status) {
                            "PENDING_APPROVAL", "PENDING_VERIFICATION" -> {
                                // Verify/Approve receipt → move to WAITING
                                onIntent(LastVisitorsIntent.OnUpdateStatus(queueItem.appointment.id, "WAITING"))
                            }
                            else -> onIntent(LastVisitorsIntent.OnMarkCompleted(queueItem.appointment.id))
                        }
                    },
                    onNoShow = {
                        when (queueItem.appointment.status) {
                            "PENDING_APPROVAL", "PENDING_VERIFICATION" -> {
                                // Reject receipt → CANCELLED
                                onIntent(LastVisitorsIntent.OnUpdateStatus(queueItem.appointment.id, "CANCELLED"))
                            }
                            else -> onIntent(LastVisitorsIntent.OnMarkNoShow(queueItem.appointment.id))
                        }
                    },
                    onSendMessage = { appointmentId, type, content, businessTitle ->
                        onIntent(
                            LastVisitorsIntent.OnSendMessage(
                                appointmentId = appointmentId,
                                type = type,
                                content = content,
                                businessTitle = businessTitle
                            )
                        )
                    },
                    onItemClick = {
                        onIntent(LastVisitorsIntent.OnEditAppointment(queueItem.appointment.id))
                    },
                    onGenerateMessage = onGenerateMessage
                )
            }
        }
        item { BottomBarSpacer() }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun FilterBottomSheet(
    filter: AppointmentFilter,
    onDismiss: () -> Unit,
    onFilterChanged: (AppointmentFilter) -> Unit,
    onClearFilter: () -> Unit
) {
    var selectedStatus by remember { mutableStateOf(filter.status) }
    var selectedOrdering by remember { mutableStateOf(filter.ordering) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.surface
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
                .padding(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "فیلتر نوبت‌ها",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
                TextButton(onClick = onClearFilter) {
                    Text("پاکسازی")
                }
            }

            // Status Filter
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "وضعیت نوبت",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    appointmentStatusOptions().forEach { (status, label) ->
                        FilterChip(
                            selected = selectedStatus == status,
                            onClick = { selectedStatus = status },
                            label = { Text(label) }
                        )
                    }
                }
            }

            // Ordering Filter
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "مرتب‌سازی",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    AppointmentOrdering.entries.forEach { ordering ->
                        val label = when (ordering) {
                            AppointmentOrdering.DATE_ASC -> stringResource(Res.string.sort_date_nearest_first)
                            AppointmentOrdering.DATE_DESC -> stringResource(Res.string.sort_date_farthest_first)
                            AppointmentOrdering.CREATED_AT_ASC -> stringResource(Res.string.sort_created_oldest_first)
                            AppointmentOrdering.CREATED_AT_DESC -> stringResource(Res.string.sort_created_newest_first)
                        }
                        FilterChip(
                            selected = selectedOrdering == ordering,
                            onClick = { selectedOrdering = ordering },
                            label = { Text(label) }
                        )
                    }
                }
            }

            AppButton(
                text = "اعمال فیلتر",
                onClick = {
                    onFilterChanged(
                        filter.copy(
                            status = selectedStatus,
                            ordering = selectedOrdering
                        )
                    )
                },
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

@Composable
fun AppointmentsList(
    appointments: List<AppointmentWithDetails>,
    onEditClick: (Long) -> Unit,
    onDeleteClick: (Long) -> Unit,
    onItemClick: (Long) -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        items(appointments, key = { it.appointment.id }) { appointment ->
            Box(modifier = Modifier.animateItem()) {
                AppointmentCard(
                    appointmentWithDetails = appointment,
                    onEditClick = { onEditClick(appointment.appointment.id) },
                    onDeleteClick = { onDeleteClick(appointment.appointment.id) },
                    onItemClick = { onItemClick(appointment.appointment.visitorId) }
                )
            }
        }
        item { BottomBarSpacer() }
    }
}

/** Index of the range-report tab, referenced from both layouts. */
const val RANGE_REPORT_TAB = 2

/**
 * The screen's tab bar. Extracted because both the phone and web layouts
 * render it from more than one branch of their content `when`, and three
 * copies drifting apart is how a tab ends up existing on one path and not
 * another.
 */
@Composable
private fun VisitorsTabBar(
    selectedIndex: Int,
    onSelected: (Int) -> Unit
) {
    SectionTabs(
        labels = listOf(
            stringResource(Res.string.visitors_tab),
            stringResource(Res.string.queue_tab),
            stringResource(Res.string.range_report_tab),
        ),
        selectedIndex = selectedIndex,
        onSelected = onSelected,
        modifier = Modifier.fillMaxWidth(),
        tabHeight = 48.dp
    )
}

@Composable
fun AppointmentCard(
    appointmentWithDetails: AppointmentWithDetails,
    onEditClick: () -> Unit,
    onDeleteClick: () -> Unit,
    onItemClick: () -> Unit
) {
    val appointment = appointmentWithDetails.appointment
    val visitor = appointmentWithDetails.visitor

    Card(
        modifier = Modifier.fillMaxWidth().clickable { onItemClick() },
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer
        ),
        border = CardDefaults.outlinedCardBorder().copy(
            brush = androidx.compose.ui.graphics.SolidColor(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp)
        ) {
            val durationMinutes = appointment.serviceDuration
                ?: appointmentWithDetails.business.defaultServiceDuration
            val endTimeMs = appointment.appointmentDate + durationMinutes * 60 * 1000L
            val dateText = DateTimeUtils.formatDate(appointment.appointmentDate)
            val startTimeOnly = DateTimeUtils.formatTime(appointment.appointmentDate)
            val endTimeOnly = DateTimeUtils.formatTime(endTimeMs)
            val overdue =
                DateTimeUtils.systemCurrentMilliseconds() > endTimeMs && appointment.status == "WAITING"

            // Two lines: who and where the booking stands, then when. The
            // phone number, and the separate tinted strip the time/date/
            // duration sat in, made every row ~120dp; the number is one tap
            // away on the visitor's page, which is where tapping the card goes.
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(38.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = visitor.fullName.firstOrNull()?.toString() ?: "?",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                Spacer(modifier = Modifier.width(10.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = visitor.fullName,
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        StatusBadge(status = appointment.status, overdue = overdue)
                    }
                    Spacer(modifier = Modifier.height(3.dp))
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        MetaItem(
                            icon = Icons.Rounded.Schedule,
                            text = "$endTimeOnly ${stringResource(Res.string.to_label)} $startTimeOnly",
                            emphasized = true
                        )
                        MetaItem(icon = Icons.Rounded.CalendarToday, text = dateText)
                        MetaItem(icon = Icons.Rounded.Timer, text = "$durationMinutes دقیقه")
                    }
                }
            }

            // Anything below only exists for some bookings, and each is kept
            // to a single line so a card with all of them stays short.
            val hasNote = !appointment.description.isNullOrBlank()
            val hasDeposit = appointment.depositPaymentMethod == "CARD" || appointment.depositPaymentMethod == "GATEWAY"
            val receipt = appointment.paymentReceipt
            if (hasNote || hasDeposit || receipt != null) {
                Column(
                    modifier = Modifier.padding(start = 48.dp, top = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    if (hasNote) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Rounded.Edit,
                                contentDescription = null,
                                modifier = Modifier.size(13.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = appointment.description.orEmpty(),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }

                    // Deposit summary — a gateway payment has a tracking
                    // reference but no receipt image, so this stands on its
                    // own. An owner meeting the client in person to collect
                    // the remainder needs to see that a deposit was already
                    // taken, and through which channel.
                    if (hasDeposit) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = if (appointment.depositPaymentMethod == "GATEWAY")
                                    Icons.Rounded.CreditCard else Icons.Rounded.Payments,
                                contentDescription = null,
                                modifier = Modifier.size(14.dp),
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = stringResource(Res.string.deposit_paid_label) + " — " + stringResource(
                                    if (appointment.depositPaymentMethod == "GATEWAY")
                                        Res.string.deposit_method_gateway
                                    else Res.string.deposit_method_card
                                ) + (appointment.paymentReference?.takeIf { it.isNotBlank() }
                                    ?.let { " · ${stringResource(Res.string.tracking_number_label)}: $it" } ?: ""),
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.primary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }

                    // Folded to one line; see CollapsibleReceipt.
                    receipt?.let { CollapsibleReceipt(receipt = it) }
                }
            }
        }
    }
}

@Composable
private fun MetaItem(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    text: String,
    emphasized: Boolean = false
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            modifier = Modifier.size(14.dp),
            tint = if (emphasized) MaterialTheme.colorScheme.primary
            else MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.width(4.dp))
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = if (emphasized) FontWeight.Bold else FontWeight.Medium,
            color = if (emphasized) MaterialTheme.colorScheme.primary
            else MaterialTheme.colorScheme.onSurface,
            maxLines = 1
        )
    }
}

@Composable
fun StatusBadge(status: String, overdue: Boolean) {
    val isDark = !MaterialTheme.colorScheme.surface.let { color ->
        (color.red * 0.299 + color.green * 0.587 + color.blue * 0.114) > 0.5
    }
    // Mirrors Appointment.STATUS_CHOICES in backend/appointment/models.py. Note
    // CONFIRMED (owner approved the booking) and COMPLETED (service finished)
    // are distinct: CONFIRMED used to be mislabelled "تکمیل شده", and COMPLETED,
    // LOCKED and IN_PROGRESS fell through to the raw English status text.
    val (text, bgColor, contentColor) = when {
        status == "WAITING" && overdue -> Triple(
            stringResource(Res.string.overdue_time),
            if (isDark) Color(0xFFB71C1C).copy(alpha = 0.4f) else Color(0xFFFFEBEE),
            if (isDark) Color(0xFFEF9A9A) else Color(0xFFC62828)
        )
        status == "LOCKED" -> Triple(
            "در حال پرداخت",
            if (isDark) Color(0xFF37474F).copy(alpha = 0.5f) else Color(0xFFECEFF1),
            if (isDark) Color(0xFFB0BEC5) else Color(0xFF455A64)
        )
        status == "PENDING_VERIFICATION" -> Triple(
            "💳 در انتظار تأیید فیش",
            if (isDark) Color(0xFF4A148C).copy(alpha = 0.4f) else Color(0xFFEDE7F6),
            if (isDark) Color(0xFFCE93D8) else Color(0xFF6A1B9A)
        )
        status == "PENDING_APPROVAL" -> Triple(
            "در انتظار تایید",
            if (isDark) Color(0xFFE65100).copy(alpha = 0.4f) else Color(0xFFFFF3E0),
            if (isDark) Color(0xFFFFCC80) else Color(0xFFE65100)
        )
        status == "CONFIRMED" -> Triple(
            "تأیید شده",
            if (isDark) Color(0xFF0D47A1).copy(alpha = 0.4f) else Color(0xFFE3F2FD),
            if (isDark) Color(0xFF90CAF9) else Color(0xFF1565C0)
        )
        status == "WAITING" -> Triple(
            stringResource(Res.string.status_waiting),
            MaterialTheme.colorScheme.primaryContainer.copy(alpha = if (isDark) 0.4f else 0.7f),
            MaterialTheme.colorScheme.onPrimaryContainer
        )
        status == "IN_PROGRESS" -> Triple(
            "در حال سرویس",
            if (isDark) Color(0xFF006064).copy(alpha = 0.45f) else Color(0xFFE0F7FA),
            if (isDark) Color(0xFF80DEEA) else Color(0xFF00838F)
        )
        status == "COMPLETED" -> Triple(
            stringResource(Res.string.status_completed),
            if (isDark) Color(0xFF1B5E20).copy(alpha = 0.4f) else Color(0xFFE8F5E9),
            if (isDark) Color(0xFFA5D6A7) else Color(0xFF2E7D32)
        )
        status == "NO_SHOW" -> Triple(
            stringResource(Res.string.status_no_show),
            if (isDark) Color(0xFFB71C1C).copy(alpha = 0.4f) else Color(0xFFFFEBEE),
            if (isDark) Color(0xFFEF9A9A) else Color(0xFFC62828)
        )
        status == "CANCELLED" -> Triple(
            "لغو شده",
            if (isDark) Color(0xFF424242).copy(alpha = 0.5f) else Color(0xFFF5F5F5),
            if (isDark) Color(0xFFBDBDBD) else Color(0xFF616161)
        )
        else -> Triple(
            status,
            MaterialTheme.colorScheme.surfaceVariant,
            MaterialTheme.colorScheme.onSurfaceVariant
        )
    }

    Surface(
        shape = RoundedCornerShape(8.dp),
        color = bgColor
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            color = contentColor
        )
    }
}


@Composable
fun HandleEvents(
    events: Flow<LastVisitorsEvent>,
    onNavigateToCreateAppointment: () -> Unit,
    onNavigateToEditAppointment: (Long) -> Unit,
    onNavigateToVisitorDetails: (Long) -> Unit
) {
    events.collectWithLifecycleAware {
        when (it) {
            LastVisitorsEvent.NavigateToCreateAppointment -> {
                onNavigateToCreateAppointment()
            }

            is LastVisitorsEvent.NavigateToEditAppointment -> {
                onNavigateToEditAppointment(it.appointmentId)
            }

            is LastVisitorsEvent.NavigateToVisitorDetails -> {
                onNavigateToVisitorDetails(it.visitorId)
            }
        }
    }
}


@Preview(showBackground = true)
@Composable
fun PreviewLastVisitorsScreen() {
    AppTheme {
    }
}
