package xyz.sattar.javid.proqueue.feature.lastVisitors

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items as gridItems
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.EventNote
import androidx.compose.material.icons.rounded.Groups
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import xyz.sattar.javid.proqueue.core.ui.LocalWindowSize
import xyz.sattar.javid.proqueue.core.ui.WindowSize
import xyz.sattar.javid.proqueue.core.ui.components.BottomBarSpacer
import xyz.sattar.javid.proqueue.core.ui.components.DateBottomSheet
import xyz.sattar.javid.proqueue.core.ui.components.EmptyState
import xyz.sattar.javid.proqueue.core.ui.components.SectionTabs
import xyz.sattar.javid.proqueue.core.utils.DateTimeUtils
import xyz.sattar.javid.proqueue.domain.model.RangeCustomer
import xyz.sattar.javid.proqueue.domain.model.appointment.AppointmentWithDetails
import xyz.sattar.javid.proqueue.domain.usecase.GetRangeReportUseCase

/**
 * "گزارش بازه‌ای" — the third tab on the مراجعین screen: pick a window of up
 * to a month and see what happened in it, both as totals and as the two lists
 * behind them (who came, and every appointment).
 *
 * One composable for phone and web alike. The only piece that differs is the
 * date picker, and [DateBottomSheet] already resolves that itself (bottom
 * sheet at Compact, dialog above it), so nothing here needs a width branch.
 *
 * The window is capped at [GetRangeReportUseCase.MAX_RANGE_DAYS] on the way
 * out — see [onRangeChanged]'s callers — and again on the server, which is
 * the layer that actually holds, since the panel runs in a browser where the
 * query string is whatever the caller types.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun RangeReportSection(
    uiState: LastVisitorsState,
    onIntent: (LastVisitorsIntent) -> Unit,
    onAppointmentClick: (Long) -> Unit,
    onEditAppointment: (Long) -> Unit,
    onAppointmentOptions: (Long) -> Unit,
    modifier: Modifier = Modifier
) {
    var showFromPicker by remember { mutableStateOf(false) }
    var showToPicker by remember { mutableStateOf(false) }

    val report = uiState.rangeReport

    if (showFromPicker) {
        DateBottomSheet(
            initialDate = uiState.rangeFrom,
            onDateSelected = { picked ->
                showFromPicker = false
                // Moving the start past the end drags the end with it, rather
                // than producing an inverted range the server would reject.
                val end = maxOf(uiState.rangeTo, picked)
                onIntent(LastVisitorsIntent.OnRangeChanged(picked, end))
            },
            onDismiss = { showFromPicker = false }
        )
    }

    if (showToPicker) {
        DateBottomSheet(
            initialDate = uiState.rangeTo,
            onDateSelected = { picked ->
                showToPicker = false
                val start = minOf(uiState.rangeFrom, picked)
                onIntent(LastVisitorsIntent.OnRangeChanged(start, picked))
            },
            onDismiss = { showToPicker = false }
        )
    }

    Column(modifier = modifier.fillMaxSize()) {
        RangePickerRow(
            from = uiState.rangeFrom,
            to = uiState.rangeTo,
            onFromClick = { showFromPicker = true },
            onToClick = { showToPicker = true },
            onPreset = { days ->
                val today = DateTimeUtils.startOfTodayMillis()
                onIntent(
                    LastVisitorsIntent.OnRangeChanged(
                        today - (days - 1) * GetRangeReportUseCase.DAY_MILLIS,
                        today
                    )
                )
            }
        )

        if (report?.clamped == true) {
            Text(
                text = "بازه به حداکثر ${GetRangeReportUseCase.MAX_RANGE_DAYS} روز محدود شد",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.tertiary,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
            )
        }

        when {
            uiState.rangeLoading && report == null -> {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            }

            uiState.rangeFailed && report == null -> {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    EmptyState(
                        icon = Icons.Rounded.EventNote,
                        title = "گزارش بارگذاری نشد",
                        subtitle = "اتصال خود را بررسی کنید و دوباره تلاش کنید"
                    )
                }
            }

            report != null -> {
                RangeSummaryCard(
                    totalAppointments = report.totalAppointments,
                    uniqueVisitors = report.uniqueVisitors,
                    completed = report.completedAppointments,
                    noShow = report.noShowAppointments
                )

                Spacer(modifier = Modifier.height(12.dp))

                SectionTabs(
                    labels = listOf(
                        "مشتری‌ها (${report.customersTotal})",
                        "نوبت‌ها (${report.totalAppointments})"
                    ),
                    selectedIndex = uiState.rangeSubTab,
                    onSelected = { onIntent(LastVisitorsIntent.OnRangeSubTabSelected(it)) },
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(12.dp))

                if (uiState.rangeSubTab == 0) {
                    CustomerList(
                        customers = report.customers,
                        hasMore = report.hasMoreCustomers,
                        isLoadingMore = uiState.rangeLoading,
                        onLoadMore = { onIntent(LastVisitorsIntent.LoadMoreRangeCustomers) },
                        onCustomerClick = onAppointmentClick
                    )
                } else {
                    RangeAppointmentList(
                        appointments = uiState.rangeAppointments,
                        onAppointmentClick = onAppointmentClick,
                        onEditAppointment = onEditAppointment,
                        onAppointmentOptions = onAppointmentOptions
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun RangePickerRow(
    from: Long,
    to: Long,
    onFromClick: () -> Unit,
    onToClick: () -> Unit,
    onPreset: (Int) -> Unit
) {
    // One wrapping row rather than a full-width pair plus a separate chip row:
    // stretched across a desktop panel the two date fields were mostly empty
    // box, and the presets sat orphaned under them. FlowRow keeps them all on
    // one line where there's room and wraps on a phone.
    FlowRow(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        DateField(label = "از", value = from, onClick = onFromClick)
        DateField(label = "تا", value = to, onClick = onToClick)

        // 31 rather than 30 so the widest preset lands exactly on the cap
        // instead of one day short of it.
        listOf(7 to "۷ روز", 30 to "۳۰ روز", GetRangeReportUseCase.MAX_RANGE_DAYS to "یک ماه")
            .forEach { (days, label) ->
                AssistChip(onClick = { onPreset(days) }, label = { Text(label) })
            }
    }
}

@Composable
private fun DateField(
    label: String,
    value: Long,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier
            .widthIn(min = 150.dp)
            .clip(RoundedCornerShape(14.dp))
            .clickable(onClick = onClick),
        color = MaterialTheme.colorScheme.surfaceContainer,
        shape = RoundedCornerShape(14.dp)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(
                Icons.Rounded.CalendarMonth,
                contentDescription = null,
                modifier = Modifier.size(18.dp),
                tint = MaterialTheme.colorScheme.primary
            )
            Column {
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = if (value == 0L) "—" else DateTimeUtils.getJalaliDate(value),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

@Composable
private fun RangeSummaryCard(
    totalAppointments: Int,
    uniqueVisitors: Int,
    completed: Int,
    noShow: Int
) {
    // Four separate cards rather than four numbers spread across one wide
    // one: on a desktop panel that single card put ~200dp of empty surface
    // between each figure and its neighbour, which read as a gap rather than
    // a row of stats. Mirrors the home dashboard's own stat strip.
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        SummaryStat("نوبت‌ها", totalAppointments, MaterialTheme.colorScheme.primary, Modifier.weight(1f))
        SummaryStat("مشتری‌ها", uniqueVisitors, MaterialTheme.colorScheme.tertiary, Modifier.weight(1f))
        SummaryStat("تکمیل‌شده", completed, MaterialTheme.colorScheme.primary, Modifier.weight(1f))
        SummaryStat("عدم حضور", noShow, MaterialTheme.colorScheme.error, Modifier.weight(1f))
    }
}

@Composable
private fun SummaryStat(
    label: String,
    value: Int,
    color: androidx.compose.ui.graphics.Color,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "$value",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.ExtraBold,
                color = color
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun CustomerList(
    customers: List<RangeCustomer>,
    hasMore: Boolean,
    isLoadingMore: Boolean,
    onLoadMore: () -> Unit,
    onCustomerClick: (Long) -> Unit
) {
    if (customers.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            EmptyState(
                icon = Icons.Rounded.Groups,
                title = "مشتری‌ای در این بازه نبوده",
                subtitle = "بازه‌ی دیگری را امتحان کنید"
            )
        }
        return
    }

    // Same grid the appointment tabs use on a wide window — one column of
    // full-width rows across a desktop panel left each card mostly empty
    // space between the name and its badge.
    val columns = if (LocalWindowSize.current == WindowSize.Expanded) 2 else 1

    LazyVerticalGrid(
        columns = GridCells.Fixed(columns),
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        gridItems(customers, key = { it.visitorId }) { customer ->
            CustomerRow(customer = customer, onClick = { onCustomerClick(customer.visitorId) })
        }
        if (hasMore) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                Box(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
                    contentAlignment = Alignment.Center
                ) {
                    if (isLoadingMore) {
                        CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
                    } else {
                        TextButton(onClick = onLoadMore) { Text("نمایش بیشتر") }
                    }
                }
            }
        }
        item(span = { GridItemSpan(maxLineSpan) }) { BottomBarSpacer() }
    }
}

@Composable
private fun CustomerRow(customer: RangeCustomer, onClick: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = customer.fullName.trim().firstOrNull()?.uppercaseChar()?.toString() ?: "؟",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
            }

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = customer.fullName.ifBlank { "بدون نام" },
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = customer.phoneNumber,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                customer.lastVisit?.let {
                    Text(
                        text = "آخرین مراجعه: ${DateTimeUtils.getJalaliDate(it)}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
                    )
                }
            }

            Surface(
                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                shape = RoundedCornerShape(50)
            ) {
                Text(
                    text = "${customer.appointmentCount} نوبت",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                )
            }
        }
    }
}

@Composable
private fun RangeAppointmentList(
    appointments: List<AppointmentWithDetails>,
    onAppointmentClick: (Long) -> Unit,
    onEditAppointment: (Long) -> Unit,
    onAppointmentOptions: (Long) -> Unit
) {
    if (appointments.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            EmptyState(
                icon = Icons.Rounded.EventNote,
                title = "نوبتی در این بازه نبوده",
                subtitle = "بازه‌ی دیگری را امتحان کنید"
            )
        }
        return
    }

    val columns = if (LocalWindowSize.current == WindowSize.Expanded) 2 else 1

    LazyVerticalGrid(
        columns = GridCells.Fixed(columns),
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        gridItems(appointments, key = { it.appointment.id }) { item ->
            // The same card the other two tabs use, not a copy — status
            // colours and the receipt preview stay in one place.
            AppointmentCard(
                appointmentWithDetails = item,
                onEditClick = { onEditAppointment(item.appointment.id) },
                onDeleteClick = { onAppointmentOptions(item.appointment.id) },
                onItemClick = { onAppointmentClick(item.visitor.id) }
            )
        }
        item(span = { GridItemSpan(maxLineSpan) }) { BottomBarSpacer() }
    }
}
