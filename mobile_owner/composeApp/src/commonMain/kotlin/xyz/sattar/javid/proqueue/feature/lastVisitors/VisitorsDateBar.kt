package xyz.sattar.javid.proqueue.feature.lastVisitors

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import org.jetbrains.compose.resources.stringResource
import proqueue.composeapp.generated.resources.Res
import proqueue.composeapp.generated.resources.status_completed
import proqueue.composeapp.generated.resources.status_no_show
import proqueue.composeapp.generated.resources.status_waiting
import xyz.sattar.javid.proqueue.core.ui.components.DateBottomSheet
import xyz.sattar.javid.proqueue.core.utils.DateTimeUtils
import xyz.sattar.javid.proqueue.domain.usecase.GetRangeReportUseCase

/**
 * The one date window the whole نوبت‌ها screen works in.
 *
 * صف, مراجعین and گزارش used to answer "when?" three different ways: the
 * first two were silently pinned to the next seven days with no control to
 * change it, and the report had its own separate picker. An owner who looked
 * up last week in the report and switched to مراجعین was suddenly looking at
 * next week, with nothing on screen saying so. Now one bar, pinned under the
 * tabs, sets the window for all three, and it is always visible — so the
 * dates a list covers are never a guess.
 *
 * The three presets are the three questions an owner actually asks of this
 * screen: what's on today, what's coming up, and how did the last month go.
 */

internal object DateRanges {
    private const val DAY = GetRangeReportUseCase.DAY_MILLIS

    /** Last millisecond of the day that starts at [dayStart]. */
    fun endOfDay(dayStart: Long): Long = dayStart + DAY - 1

    fun today(): Pair<Long, Long> {
        val t = DateTimeUtils.startOfTodayMillis()
        return t to endOfDay(t)
    }

    /** Today plus the six days after it. The screen's default. */
    fun nextWeek(): Pair<Long, Long> {
        val t = DateTimeUtils.startOfTodayMillis()
        return t to endOfDay(t + 6 * DAY)
    }

    /** The thirty days ending today — the report's natural window. */
    fun lastMonth(): Pair<Long, Long> {
        val t = DateTimeUtils.startOfTodayMillis()
        return (t - 29 * DAY) to endOfDay(t)
    }
}

private enum class DatePreset(val label: String, val range: () -> Pair<Long, Long>) {
    TODAY("امروز", DateRanges::today),
    NEXT_WEEK("۷ روز آینده", DateRanges::nextWeek),
    LAST_MONTH("۳۰ روز گذشته", DateRanges::lastMonth),
}

/**
 * @param from start of the first day in the window.
 * @param to last millisecond of the last day in the window.
 * @param onRangeChange receives the same shape: day start, day end.
 */
@Composable
internal fun VisitorsDateBar(
    from: Long,
    to: Long,
    onRangeChange: (from: Long, to: Long) -> Unit,
    modifier: Modifier = Modifier,
    /** Called after a preset is tapped, so a host showing this bar as a
     *  drop-down panel can fold it away again — a preset is a complete
     *  answer, unlike one end of a custom range. */
    onPresetSelected: () -> Unit = {},
) {
    var pickingFrom by remember { mutableStateOf(false) }
    var pickingTo by remember { mutableStateOf(false) }

    if (pickingFrom) {
        DateBottomSheet(
            initialDate = from,
            onDateSelected = { picked ->
                pickingFrom = false
                val start = DateTimeUtils.startOfDayMillis(picked)
                // Moving the start past the end drags the end with it, rather
                // than producing an inverted window.
                val end = maxOf(to, DateRanges.endOfDay(start))
                onRangeChange(start, end)
            },
            onDismiss = { pickingFrom = false }
        )
    }
    if (pickingTo) {
        DateBottomSheet(
            initialDate = to,
            onDateSelected = { picked ->
                pickingTo = false
                val end = DateRanges.endOfDay(DateTimeUtils.startOfDayMillis(picked))
                val start = minOf(from, DateTimeUtils.startOfDayMillis(picked))
                onRangeChange(start, end)
            },
            onDismiss = { pickingTo = false }
        )
    }

    val active = DatePreset.entries.firstOrNull { it.range() == (from to to) }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(start = 16.dp, end = 16.dp, top = 2.dp, bottom = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            DateField("از", from, { pickingFrom = true }, Modifier.weight(1f))
            DateField("تا", to, { pickingTo = true }, Modifier.weight(1f))
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            DatePreset.entries.forEach { preset ->
                PresetChip(
                    label = preset.label,
                    selected = preset == active,
                    onClick = {
                        val (f, t) = preset.range()
                        onRangeChange(f, t)
                        onPresetSelected()
                    },
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

/** Same set as Appointment.STATUS_CHOICES on the backend. */
@Composable
internal fun appointmentStatusOptions(): List<Pair<String?, String>> = listOf(
    null to "همه",
    "LOCKED" to "در حال پرداخت",
    "PENDING_VERIFICATION" to "💳 در انتظار تأیید فیش",
    "PENDING_APPROVAL" to "در انتظار تایید",
    "CONFIRMED" to "تأیید شده",
    "WAITING" to stringResource(Res.string.status_waiting),
    "IN_PROGRESS" to "در حال سرویس",
    "COMPLETED" to stringResource(Res.string.status_completed),
    "NO_SHOW" to stringResource(Res.string.status_no_show),
    "CANCELLED" to "لغو شده"
)

@Composable
private fun DateField(
    label: String,
    value: Long,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier
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
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    softWrap = false
                )
            }
        }
    }
}

@Composable
private fun PresetChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier
            .height(40.dp)
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(12.dp),
        color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface,
        border = if (selected) null else BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Box(contentAlignment = Alignment.Center, modifier = Modifier.padding(horizontal = 6.dp)) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                color = if (selected) MaterialTheme.colorScheme.onPrimary
                else MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                softWrap = false,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

/**
 * The نوبت‌ها screen's filters folded into a single line under the tabs.
 *
 * The date bar used to sit open above every list — two date fields, a row of
 * presets and a count card: close to half the screen before the first
 * appointment, spent on settings an owner changes a couple of times a day
 * rather than on the list they came to work through. Everything that was
 * there is still one tap away; what stays on screen is only what the owner
 * needs to read the list correctly — which window it covers, which status (if
 * any) it's narrowed to, and how many rows that came to.
 *
 * @param count rows in the list, or null to leave the count out (the report
 *   tab carries its own totals).
 * @param onOpenFilter null hides the status/ordering button (the report
 *   ignores both).
 */
@Composable
internal fun VisitorsFilterStrip(
    from: Long,
    to: Long,
    dateExpanded: Boolean,
    onToggleDate: () -> Unit,
    status: String?,
    onClearStatus: () -> Unit,
    count: Int?,
    countLabel: String,
    filterActive: Boolean,
    onOpenFilter: (() -> Unit)?,
    modifier: Modifier = Modifier,
) {
    val active = DatePreset.entries.firstOrNull { it.range() == (from to to) }
    val rangeLabel = active?.label ?: compactRangeLabel(from, to)
    val chevronRotation by animateFloatAsState(if (dateExpanded) 180f else 0f)

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // The window, as a drop-down: tapping it opens the full date bar.
        Surface(
            shape = RoundedCornerShape(50),
            color = if (dateExpanded) MaterialTheme.colorScheme.primary
            else MaterialTheme.colorScheme.primaryContainer,
            contentColor = if (dateExpanded) MaterialTheme.colorScheme.onPrimary
            else MaterialTheme.colorScheme.onPrimaryContainer,
            modifier = Modifier.height(34.dp).clip(RoundedCornerShape(50)).clickable(onClick = onToggleDate)
        ) {
            Row(
                modifier = Modifier.padding(start = 12.dp, end = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Icon(Icons.Rounded.CalendarMonth, contentDescription = null, modifier = Modifier.size(16.dp))
                Text(
                    text = rangeLabel,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    softWrap = false
                )
                Icon(
                    Icons.Rounded.ExpandMore,
                    contentDescription = if (dateExpanded) "بستن بازه" else "تغییر بازه",
                    modifier = Modifier.size(18.dp).rotate(chevronRotation)
                )
            }
        }

        // Takes whatever width is left, so a long status label ellipsises
        // instead of pushing the count off the line.
        Box(modifier = Modifier.weight(1f)) {
            if (status != null) {
                CompactStatusChip(status = status, onClear = onClearStatus)
            }
        }

        if (count != null) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = count.toString(),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Black,
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = countLabel,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1
                )
            }
        }

        if (onOpenFilter != null) {
            Box {
                Box(
                    modifier = Modifier
                        .size(34.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                        .clickable(onClick = onOpenFilter),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Rounded.Tune,
                        contentDescription = "فیلتر",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(18.dp)
                    )
                }
                // A status or a non-default order is in force.
                if (filterActive) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .size(9.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primary)
                    )
                }
            }
        }
    }
}

/** "04/01 – 06/07" within one year, full dates otherwise. */
private fun compactRangeLabel(from: Long, to: Long): String {
    if (from == 0L || to == 0L) return "بازه"
    val f = DateTimeUtils.getJalaliDate(from)
    val t = DateTimeUtils.getJalaliDate(to)
    if (f == t) return f
    val sameYear = f.substringBefore('/') == t.substringBefore('/')
    return if (sameYear) "${f.substringAfter('/')} – ${t.substringAfter('/')}" else "$f – $t"
}

@Composable
private fun CompactStatusChip(status: String, onClear: () -> Unit) {
    val label = appointmentStatusOptions().firstOrNull { it.first == status }?.second ?: status
    Surface(
        shape = RoundedCornerShape(50),
        color = MaterialTheme.colorScheme.secondaryContainer,
        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
        modifier = Modifier.height(30.dp).clip(RoundedCornerShape(50)).clickable(onClick = onClear)
    ) {
        Row(
            modifier = Modifier.padding(start = 10.dp, end = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = false)
            )
            Icon(Icons.Rounded.Close, contentDescription = "حذف فیلتر وضعیت", modifier = Modifier.size(14.dp))
        }
    }
}
