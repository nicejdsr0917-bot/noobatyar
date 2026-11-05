package xyz.sattar.javid.proqueue.feature.home

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ChevronLeft
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Today
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import xyz.sattar.javid.proqueue.core.i18n.AppLocale
import xyz.sattar.javid.proqueue.core.i18n.Str
import xyz.sattar.javid.proqueue.core.utils.toPersianDigits

/** Status colours for today's numbers; muted enough to sit on either theme. */
private val CompletedColor = Color(0xFF2E9E6A)
private val NoShowColor = Color(0xFFE5484D)
private val CancelledColor = Color(0xFF8B8D98)
private val QueueColor = Color(0xFF0B8BD9)

/**
 * Today at a glance, in one card: how many bookings today, how many people
 * are waiting right now, and the four outcomes.
 *
 * The count and the outcomes sit side by side, with a bar under them showing
 * how the day's bookings split. Stacked (count over a 2×2 grid) they left
 * half the card empty beside the number and in every grid cell.
 */
@Composable
fun HomeTodayCard(
    stats: DashboardStats,
    queueCount: Int,
    onQueueClick: () -> Unit,
    onStatClick: (status: String?) -> Unit,
) {
    HomeCard {
        HomeCardHeader(
            icon = Icons.Rounded.Today,
            title = Str.sms_report_date_filter_today,
            trailing = { QueueChip(count = queueCount, onClick = onQueueClick) },
        )

        Spacer(Modifier.height(12.dp))

        val cells = listOf(
            StatSpec(Str.complete_action, stats.completedAppointments, CompletedColor, "COMPLETED"),
            StatSpec(Str.status_no_show, stats.noShowAppointments, NoShowColor, "NO_SHOW"),
            StatSpec(Str.status_cancelled, stats.cancelledAppointments, CancelledColor, "CANCELLED"),
            StatSpec(Str.total_clients, stats.totalVisitors, MaterialTheme.colorScheme.primary, null),
        )
        Row(
            modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Min),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(
                modifier = Modifier
                    .weight(0.4f)
                    .clip(RoundedCornerShape(12.dp))
                    .clickable { onStatClick(null) }
                    .padding(vertical = 4.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                AnimatedCount(value = stats.totalAppointments, style = MaterialTheme.typography.displayMedium)
                Text(
                    text = Str.home_today_appointments,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                )
            }
            Box(
                Modifier
                    .padding(horizontal = 8.dp)
                    .width(1.dp)
                    .fillMaxHeight(0.8f)
                    .background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
            )
            Column(Modifier.weight(0.6f)) {
                cells.forEach { cell -> StatLine(cell) { onStatClick(cell.status) } }
            }
        }

        DaySplitBar(stats, Modifier.padding(top = 12.dp))
    }
}

private data class StatSpec(val label: String, val value: Int, val accent: Color, val status: String?)

/** Dot, label, and the number at the far end: one row per outcome. */
@Composable
private fun StatLine(cell: StatSpec, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 6.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(7.dp).clip(CircleShape).background(cell.accent))
        Spacer(Modifier.width(8.dp))
        Text(
            text = cell.label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        AnimatedCount(value = cell.value, style = MaterialTheme.typography.titleMedium)
    }
}

/**
 * Today's bookings as one bar: done, no-show, cancelled, and what is still to
 * come. Hidden on a day with no bookings, where it would be an empty track.
 */
@Composable
private fun DaySplitBar(stats: DashboardStats, modifier: Modifier = Modifier) {
    val total = stats.totalAppointments
    if (total <= 0) return
    val done = stats.completedAppointments
    val noShow = stats.noShowAppointments
    val cancelled = stats.cancelledAppointments
    val ahead = (total - done - noShow - cancelled).coerceAtLeast(0)
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(6.dp)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        listOf(
            done to CompletedColor,
            noShow to NoShowColor,
            cancelled to CancelledColor,
            ahead to MaterialTheme.colorScheme.primary.copy(alpha = 0.35f),
        ).filter { it.first > 0 }.forEach { (count, color) ->
            Box(Modifier.weight(count.toFloat()).fillMaxHeight().background(color))
        }
    }
}

/** "۳ نفر در صف" with a live dot; opens today's queue. */
@Composable
private fun QueueChip(count: Int, onClick: () -> Unit) {
    val active = count > 0
    val tint = if (active) QueueColor else MaterialTheme.colorScheme.onSurfaceVariant
    Row(
        modifier = Modifier
            .clip(CircleShape)
            .background(tint.copy(alpha = 0.10f))
            .clickable(onClick = onClick)
            .padding(start = 6.dp, end = 8.dp, top = 4.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (active) {
            LiveDot(color = tint, size = 6.dp)
        } else {
            Box(Modifier.padding(3.dp).size(6.dp).clip(CircleShape).background(tint.copy(alpha = 0.6f)))
        }
        Spacer(Modifier.width(4.dp))
        Text(
            text = Str.home_in_queue(count.toString()).toPersianDigits(),
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
            color = tint,
        )
        Icon(
            imageVector = if (AppLocale.isRtl) Icons.Rounded.ChevronLeft else Icons.Rounded.ChevronRight,
            contentDescription = null,
            tint = tint,
            modifier = Modifier.size(16.dp),
        )
    }
}
