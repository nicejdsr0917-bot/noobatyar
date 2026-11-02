package xyz.sattar.javid.proqueue.feature.home

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
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
 * are waiting right now, and the four outcomes. It replaces a separate queue
 * banner and a 2×2 grid of differently tinted tiles, which put five colours
 * and three shapes on the first screen.
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

        Spacer(Modifier.height(14.dp))

        Row(verticalAlignment = Alignment.Bottom) {
            AnimatedCount(
                value = stats.totalAppointments,
                style = MaterialTheme.typography.displaySmall,
                modifier = Modifier.alignByBaseline(),
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text = Str.home_today_appointments,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.alignByBaseline(),
            )
        }

        Spacer(Modifier.height(12.dp))
        Box(
            Modifier
                .fillMaxWidth()
                .height(1.dp)
                .background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
        )
        Spacer(Modifier.height(8.dp))

        val cells = listOf(
            StatSpec(Str.complete_action, stats.completedAppointments, CompletedColor, "COMPLETED"),
            StatSpec(Str.status_no_show, stats.noShowAppointments, NoShowColor, "NO_SHOW"),
            StatSpec(Str.status_cancelled, stats.cancelledAppointments, CancelledColor, "CANCELLED"),
            StatSpec(Str.total_clients, stats.totalVisitors, MaterialTheme.colorScheme.primary, null),
        )
        // Four equal cells in a row, with hairline dividers so the numbers line
        // up. A narrow card (a phone, or a column of the wide layout) gets two
        // rows of two, so no label is cut short.
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            val rows = if (maxWidth < 380.dp) cells.chunked(2) else listOf(cells)
            Column {
                rows.forEach { row ->
                    StatRow(row, onStatClick)
                }
            }
        }
    }
}

private data class StatSpec(val label: String, val value: Int, val accent: Color, val status: String?)

@Composable
private fun StatRow(cells: List<StatSpec>, onStatClick: (String?) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(IntrinsicSize.Min),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        cells.forEachIndexed { i, cell ->
            if (i > 0) {
                Box(
                    Modifier
                        .width(1.dp)
                        .fillMaxHeight(0.6f)
                        .background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                )
            }
            StatCell(
                label = cell.label,
                value = cell.value,
                accent = cell.accent,
                onClick = { onStatClick(cell.status) },
                modifier = Modifier.weight(1f),
            )
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
