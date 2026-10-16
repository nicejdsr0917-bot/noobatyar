package xyz.sattar.javid.proqueue.feature.createBusiness

import xyz.sattar.javid.proqueue.core.i18n.Str
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Checkbox
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/** One weekday, in the order an Iranian week is normally read. */
data class WeekdayOption(val isoWeekday: Int, val label: String)

/** Mon=0..Sun=6 (Kotlin's/Python's convention — matches the backend's
 *  date.weekday() 1:1), ordered Saturday-first the way the week reads here. */
val WEEKDAY_OPTIONS get() = listOf(
    WeekdayOption(5, Str.saturday),
    WeekdayOption(6, Str.sunday),
    WeekdayOption(0, Str.monday),
    WeekdayOption(1, Str.tuesday),
    WeekdayOption(2, Str.wednesday),
    WeekdayOption(3, Str.thursday_2),
    WeekdayOption(4, Str.friday),
)

/**
 * Weekly recurring days off — e.g. every Friday. Not specific calendar dates:
 * this only ever affects future availability (backend/appointment's slots
 * endpoint), and never touches an appointment already booked on a day later
 * marked closed.
 *
 * Lives in the business create/edit form, next to work hours — this is basic
 * scheduling info every business sets once, not a paid capability, so it used
 * to be easy to miss sitting inside "تنظیمات پیشرفته".
 */
@Composable
fun ClosedWeekdaysField(
    closedWeekdays: Set<Int>,
    onClosedWeekdays: (Set<Int>) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(
            Str.days_of_week,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            Str.choose_days_your_business_is,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        // A single toggle that clears the set. Not a separate stored flag: an
        // empty set already means "open every day" on its own, and having two
        // ways to say the same thing is how they drift apart later.
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()
        ) {
            Checkbox(
                checked = closedWeekdays.isEmpty(),
                onCheckedChange = { checked -> if (checked) onClosedWeekdays(emptySet()) }
            )
            Text(Str.no_closed_days_booking_is, style = MaterialTheme.typography.bodyMedium)
        }

        HorizontalDivider()

        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            WEEKDAY_OPTIONS.forEach { option ->
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Checkbox(
                        checked = option.isoWeekday in closedWeekdays,
                        onCheckedChange = { checked ->
                            val updated = closedWeekdays.toMutableSet()
                            if (checked) updated.add(option.isoWeekday) else updated.remove(option.isoWeekday)
                            onClosedWeekdays(updated)
                        }
                    )
                    Text(option.label, style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
    }
}
