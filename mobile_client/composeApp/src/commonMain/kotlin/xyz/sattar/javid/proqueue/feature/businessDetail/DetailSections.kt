package xyz.sattar.javid.proqueue.feature.businessDetail

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import xyz.sattar.javid.proqueue.domain.model.business.Business
import xyz.sattar.javid.proqueue.feature.discovery.IRANIAN_WEEK_ORDER
import xyz.sattar.javid.proqueue.feature.discovery.Radius
import xyz.sattar.javid.proqueue.feature.discovery.Space
import xyz.sattar.javid.proqueue.feature.discovery.WEEKDAY_NAMES
import xyz.sattar.javid.proqueue.feature.discovery.formatClock
import xyz.sattar.javid.proqueue.feature.discovery.tehranNow
import xyz.sattar.javid.proqueue.feature.discovery.toPersianDigits

@Composable
internal fun RequestOnlineSection(count: Int, requested: Boolean, loading: Boolean, onRequest: () -> Unit) {
    Column {
        Text("این مرکز هنوز نوبت آنلاین ندارد", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(Space.xs))
        Text(
            "درخواست بدهید تا به مرکز اطلاع دهیم مراجعانش منتظر نوبت‌دهی آنلاین هستند.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(Space.md))
        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedButton(onClick = onRequest, enabled = !requested && !loading, shape = Radius.md) {
                if (loading) {
                    CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                    Spacer(Modifier.width(Space.sm))
                }
                Text(if (requested) "درخواست شما ثبت شد" else "درخواست نوبت آنلاین")
            }
            if (count > 0) {
                Spacer(Modifier.width(Space.md))
                Text(
                    "${count.toPersianDigits()} نفر درخواست داده‌اند",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

/** Saturday-first week; today in bold accent, days off in secondary text. */
@Composable
internal fun WeeklyHours(business: Business) {
    val today = remember { tehranNow().dayOfWeek.ordinal }
    val hours = "${formatClock(business.workStartHour, business.workStartMinute)} – " +
        formatClock(business.workEndHour, business.workEndMinute)
    Column {
        IRANIAN_WEEK_ORDER.forEach { day ->
            val isToday = day == today
            val closed = day in business.closedWeekdays
            val color = when {
                isToday -> MaterialTheme.colorScheme.primary
                closed -> MaterialTheme.colorScheme.onSurfaceVariant
                else -> MaterialTheme.colorScheme.onSurface
            }
            Row(
                Modifier
                    .fillMaxWidth()
                    .height(38.dp)
                    .then(
                        if (isToday) Modifier.background(MaterialTheme.colorScheme.primary.copy(alpha = 0.08f), Radius.sm)
                        else Modifier
                    )
                    .padding(horizontal = Space.sm),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    WEEKDAY_NAMES[day],
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = if (isToday) FontWeight.Bold else FontWeight.Normal,
                    color = color,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    if (closed) "تعطیل" else hours,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = if (isToday) FontWeight.Bold else FontWeight.Normal,
                    color = color
                )
            }
        }
    }
}
