package xyz.sattar.javid.proqueue.feature.calendar

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.Insights
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import xyz.sattar.javid.proqueue.core.state.BusinessStateHolder
import xyz.sattar.javid.proqueue.core.ui.components.BlockCard
import xyz.sattar.javid.proqueue.core.utils.DateTimeUtils
import xyz.sattar.javid.proqueue.core.utils.toPersianDigits
import xyz.sattar.javid.proqueue.domain.model.appointment.AppointmentWithDetails

private val JalaliMonths = listOf(
    "فروردین", "اردیبهشت", "خرداد", "تیر", "مرداد", "شهریور",
    "مهر", "آبان", "آذر", "دی", "بهمن", "اسفند"
)

/** Saturday-first, the Iranian week. */
private val WeekdayInitials = listOf("ش", "ی", "د", "س", "چ", "پ", "ج")

/**
 * The desktop calendar's side panel: a whole month as a grid instead of the
 * phone's horizontally scrolling day strip, which a mouse can only drag.
 * Covers the same three-month window the strip does, so every day either
 * layout can reach, the other can too.
 */
@Composable
fun MonthGridBlock(
    selectedDate: Long,
    onDateSelected: (Long) -> Unit
) {
    val window = remember { DateTimeUtils.getDayWindow(monthsBefore = 1, monthsAfter = 1) }
    val selected = DateTimeUtils.getJalaliDateParts(selectedDate)
    val monthDays = remember(window, selected.year, selected.month) {
        window.filter {
            val p = DateTimeUtils.getJalaliDateParts(it)
            p.year == selected.year && p.month == selected.month
        }
    }
    val todayKey = DateTimeUtils.formatDate(DateTimeUtils.systemCurrentMilliseconds())
    val selectedKey = DateTimeUtils.formatDate(selectedDate)
    val closedWeekdays = BusinessStateHolder.selectedBusiness.value?.closedWeekdays.orEmpty()

    // First day of the neighbouring month inside the window, or null at its edge.
    val previousMonthDay = monthDays.firstOrNull()?.let { first -> window.lastOrNull { it < first } }
    val nextMonthDay = monthDays.lastOrNull()?.let { last -> window.firstOrNull { it > last } }

    BlockCard(
        title = "${JalaliMonths.getOrElse(selected.month - 1) { "" }} ${selected.year.toString().toPersianDigits()}",
        icon = Icons.Rounded.CalendarMonth,
        trailing = {
            // Under RTL "previous" sits on the right, so the arrow points right.
            IconButton(onClick = { previousMonthDay?.let(onDateSelected) }, enabled = previousMonthDay != null) {
                Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, contentDescription = "ماه قبل")
            }
            IconButton(onClick = { nextMonthDay?.let(onDateSelected) }, enabled = nextMonthDay != null) {
                Icon(Icons.AutoMirrored.Rounded.KeyboardArrowLeft, contentDescription = "ماه بعد")
            }
        }
    ) {
        Row(Modifier.fillMaxWidth()) {
            WeekdayInitials.forEach {
                Text(
                    text = it,
                    modifier = Modifier.weight(1f),
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        Spacer(Modifier.padding(top = 6.dp))

        // Mon=0..Sun=6 from getWeekday, shifted so Saturday is column 0.
        val leading = monthDays.firstOrNull()?.let { (DateTimeUtils.getWeekday(it) + 2) % 7 } ?: 0
        val cells: List<Long?> = List(leading) { null } + monthDays
        cells.chunked(7).forEach { week ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                week.forEach { day ->
                    Box(Modifier.weight(1f).aspectRatio(1f).padding(2.dp)) {
                        if (day != null) {
                            val key = DateTimeUtils.formatDate(day)
                            DayCell(
                                label = DateTimeUtils.getJalaliDateParts(day).dayOfMonth.toString().toPersianDigits(),
                                isSelected = key == selectedKey,
                                isToday = key == todayKey,
                                isClosed = DateTimeUtils.getWeekday(day) in closedWeekdays,
                                onClick = { onDateSelected(day) }
                            )
                        }
                    }
                }
                repeat(7 - week.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}

@Composable
private fun DayCell(
    label: String,
    isSelected: Boolean,
    isToday: Boolean,
    isClosed: Boolean,
    onClick: () -> Unit
) {
    val background = when {
        isSelected -> MaterialTheme.colorScheme.primary
        isClosed -> MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.35f)
        else -> Color.Transparent
    }
    val content = when {
        isSelected -> MaterialTheme.colorScheme.onPrimary
        isClosed -> MaterialTheme.colorScheme.onErrorContainer
        else -> MaterialTheme.colorScheme.onSurface
    }
    Box(
        modifier = Modifier
            .clip(CircleShape)
            .background(background)
            .then(
                if (isToday && !isSelected) Modifier.border(1.5.dp, MaterialTheme.colorScheme.primary, CircleShape)
                else Modifier
            )
            .clickable(onClick = onClick)
            .fillMaxWidth()
            .aspectRatio(1f),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = if (isSelected || isToday) FontWeight.Bold else FontWeight.Normal,
            color = content
        )
    }
}

/**
 * What the selected day holds, at a glance: the numbers an owner otherwise
 * has to count off the slot list by eye.
 */
@Composable
fun DaySummaryBlock(
    selectedDate: Long,
    appointments: List<AppointmentWithDetails>
) {
    val active = appointments.filter { it.appointment.status !in setOf("CANCELLED", "NO_SHOW") }
    val pending = appointments.count { it.appointment.status in setOf("PENDING_APPROVAL", "PENDING_VERIFICATION", "LOCKED") }
    val done = appointments.count { it.appointment.status == "COMPLETED" }
    val bookedMinutes = active.sumOf { it.appointment.serviceDuration ?: 30 }

    BlockCard(
        title = "خلاصه‌ی ${DateTimeUtils.getDayOfWeekName(selectedDate)}",
        icon = Icons.Rounded.Insights
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SummaryTile("نوبت", active.size, MaterialTheme.colorScheme.primary, Modifier.weight(1f))
            SummaryTile("در انتظار", pending, Color(0xFFF59E0B), Modifier.weight(1f))
            SummaryTile("انجام‌شده", done, Color(0xFF22C55E), Modifier.weight(1f))
        }
        Spacer(Modifier.padding(top = 10.dp))
        val hours = bookedMinutes / 60
        val minutes = bookedMinutes % 60
        Text(
            text = if (bookedMinutes == 0) "هنوز زمانی رزرو نشده"
            else "زمان رزروشده: " + buildString {
                if (hours > 0) append("${hours.toString().toPersianDigits()} ساعت")
                if (hours > 0 && minutes > 0) append(" و ")
                if (minutes > 0) append("${minutes.toString().toPersianDigits()} دقیقه")
            },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun SummaryTile(label: String, value: Int, tint: Color, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .background(tint.copy(alpha = 0.12f))
            .padding(vertical = 10.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = value.toString().toPersianDigits(),
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.ExtraBold,
            color = tint
        )
        Text(text = label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
