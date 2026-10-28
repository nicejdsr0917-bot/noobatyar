package xyz.sattar.javid.proqueue.feature.lastVisitors

import xyz.sattar.javid.proqueue.core.i18n.Str
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
import androidx.compose.material.icons.automirrored.rounded.Sort
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
import xyz.sattar.javid.proqueue.domain.model.CustomerOrdering
import xyz.sattar.javid.proqueue.domain.model.RangeCustomer
import xyz.sattar.javid.proqueue.domain.usecase.GetRangeReportUseCase

/**
 * "مشتری‌ها" — the third tab on the نوبت‌ها screen: who came in the window
 * chosen in the shared date bar ([VisitorsDateBar]), one row per person with
 * how often they came and when they were last in.
 *
 * It began as "گزارش بازه‌ای", with the window's totals (appointments,
 * customers, completed, no-shows) above the list. Those numbers are the home
 * dashboard's job, and the نوبت‌ها tab now shows its own count on the filter
 * strip, so the tab was cut down to the part nothing else in the app has —
 * the customer roll-up — and given a way to ask the two questions it's good
 * for: who are the regulars, and who was in most recently.
 *
 * The server caps the window at [GetRangeReportUseCase.MAX_RANGE_DAYS]; a
 * wider window from the bar is clamped on the way out and the note says so.
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
                        title = Str.couldnt_load_customer_list,
                        subtitle = Str.check_your_connection_and_try
                    )
                }
            }

            report != null -> {
                CustomerList(
                    header = {
                        CustomerOrderingRow(
                            selected = uiState.customerOrdering,
                            onSelected = { onIntent(LastVisitorsIntent.OnCustomerOrderingChanged(it)) }
                        )
                        if (report.clamped) {
                            Text(
                                text = Str.only_last_days_of_this(GetRangeReportUseCase.MAX_RANGE_DAYS),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.tertiary,
                                modifier = Modifier.padding(top = 6.dp)
                            )
                        }
                    },
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

/** The two orders the server offers, as a pair of chips at the top of the list. */
@Composable
private fun CustomerOrderingRow(
    selected: CustomerOrdering,
    onSelected: (CustomerOrdering) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            Icons.AutoMirrored.Rounded.Sort,
            contentDescription = Str.sort,
            modifier = Modifier.size(18.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
        CustomerOrdering.entries.forEach { ordering ->
            FilterChip(
                selected = ordering == selected,
                onClick = { onSelected(ordering) },
                label = { Text(ordering.label, style = MaterialTheme.typography.labelLarge) },
                modifier = Modifier.height(32.dp)
            )
        }
    }
}

@Composable
private fun CustomerList(
    header: @Composable () -> Unit,
    customers: List<RangeCustomer>,
    hasMore: Boolean,
    isLoadingMore: Boolean,
    onLoadMore: () -> Unit,
    onCustomerClick: (Long) -> Unit
) {
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
        item(span = { GridItemSpan(maxLineSpan) }) {
            Column { header() }
        }
        if (customers.isEmpty()) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                EmptyState(
                    icon = Icons.Rounded.Groups,
                    title = Str.no_customers_in_this_range,
                    subtitle = Str.try_different_range,
                    modifier = Modifier.padding(top = 32.dp)
                )
            }
        }
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
                        TextButton(onClick = onLoadMore) { Text(Str.show_more) }
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
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = customer.fullName.trim().firstOrNull()?.uppercaseChar()?.toString() ?: Str.text_2,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
            }

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = customer.fullName.ifBlank { Str.no_name },
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = customer.phoneNumber +
                        (customer.lastVisit?.let { Str.last(DateTimeUtils.getJalaliDate(it)) } ?: ""),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Surface(
                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                shape = RoundedCornerShape(50)
            ) {
                Text(
                    text = Str.appointments(customer.appointmentCount),
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                )
            }
        }
    }
}
