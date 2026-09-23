package xyz.sattar.javid.proqueue.feature.lastVisitors

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items as gridItems
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.EventNote
import androidx.compose.material.icons.rounded.Groups
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import xyz.sattar.javid.proqueue.core.ui.LocalWindowSize
import xyz.sattar.javid.proqueue.core.ui.WindowSize
import xyz.sattar.javid.proqueue.core.ui.components.BottomBarSpacer
import xyz.sattar.javid.proqueue.core.ui.components.EmptyState
import xyz.sattar.javid.proqueue.core.utils.DateTimeUtils
import xyz.sattar.javid.proqueue.domain.model.RangeCustomer
import xyz.sattar.javid.proqueue.domain.usecase.GetRangeReportUseCase

/**
 * "گزارش بازه‌ای" — the third tab on the نوبت‌ها screen: the window chosen in
 * the shared date bar ([VisitorsDateBar]), summarised — totals, then who came.
 *
 * It used to carry its own date picker and its own appointment list. The
 * picker is now the screen-wide bar, so every tab answers for the same dates;
 * and with the dates shared, its appointment list was the مراجعین tab over
 * again. What only this tab has is the numbers and the customer roll-up, so
 * that is all it shows.
 *
 * The server caps a report at [GetRangeReportUseCase.MAX_RANGE_DAYS]; a wider
 * window from the bar is clamped on the way out and the note below says so.
 */
@Composable
fun RangeReportSection(
    uiState: LastVisitorsState,
    onIntent: (LastVisitorsIntent) -> Unit,
    onCustomerClick: (Long) -> Unit,
    modifier: Modifier = Modifier
) {
    val report = uiState.rangeReport

    Column(modifier = modifier.fillMaxSize()) {
        if (report?.clamped == true) {
            Text(
                text = "گزارش فقط ${GetRangeReportUseCase.MAX_RANGE_DAYS} روز آخرِ این بازه را پوشش می‌دهد",
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

                Text(
                    text = "مشتری‌های این بازه (${report.customersTotal})",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 8.dp)
                )

                CustomerList(
                    customers = report.customers,
                    hasMore = report.hasMoreCustomers,
                    isLoadingMore = uiState.rangeLoading,
                    onLoadMore = { onIntent(LastVisitorsIntent.LoadMoreRangeCustomers) },
                    onCustomerClick = onCustomerClick
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
