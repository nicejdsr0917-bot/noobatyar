package xyz.sattar.javid.proqueue.feature.createBusiness

import xyz.sattar.javid.proqueue.core.i18n.Str
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import xyz.sattar.javid.proqueue.core.ui.components.AppTextField
import xyz.sattar.javid.proqueue.core.ui.components.TimeBottomSheet
import xyz.sattar.javid.proqueue.core.utils.toPersianDigits

/** "HH:mm" from the hour/minute pair the form holds as separate strings. */
private fun timeText(hour: String, minute: String): String {
    val h = hour.trim().toIntOrNull() ?: 0
    val m = minute.trim().toIntOrNull() ?: 0
    return "${h.toString().padStart(2, '0')}:${m.toString().padStart(2, '0')}"
}

/**
 * Opening and closing time, each picked from the shared [TimeBottomSheet]
 * wheel rather than typed.
 *
 * Replaces four cramped side-by-side text inputs (hour and minute for each
 * end). At that width the end-hour's own placeholder wrapped onto two lines
 * and overflowed its box, and nothing about four adjacent number boxes said
 * which two of them formed one time. This is the same tap-to-open-a-sheet
 * treatment the service-duration field on this form already uses, and reuses
 * TimeBottomSheet — which existed in the project unused until now.
 */
@Composable
fun WorkHoursField(
    workStartHour: String,
    workStartMinute: String,
    workEndHour: String,
    workEndMinute: String,
    onWorkStart: (hour: String, minute: String) -> Unit,
    onWorkEnd: (hour: String, minute: String) -> Unit,
    isError: Boolean,
    errorMessage: String?,
    enabled: Boolean,
) {
    var editing by remember { mutableStateOf<WorkHourEnd?>(null) }

    Column {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            TimeDisplayField(
                label = Str.work_start_hour,
                value = timeText(workStartHour, workStartMinute),
                isError = isError,
                enabled = enabled,
                onClick = { editing = WorkHourEnd.START },
                modifier = Modifier.weight(1f),
            )
            TimeDisplayField(
                label = Str.work_end_hour,
                value = timeText(workEndHour, workEndMinute),
                isError = isError,
                enabled = enabled,
                onClick = { editing = WorkHourEnd.END },
                modifier = Modifier.weight(1f),
            )
        }

        if (isError && errorMessage != null) {
            Text(
                text = errorMessage,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(top = 6.dp, start = 4.dp)
            )
        }
    }

    editing?.let { end ->
        val current = when (end) {
            WorkHourEnd.START -> timeText(workStartHour, workStartMinute)
            WorkHourEnd.END -> timeText(workEndHour, workEndMinute)
        }
        TimeBottomSheet(
            initialTime = current,
            onTimeSelected = { picked ->
                val (hour, minute) = picked.split(":").let { it[0] to it[1] }
                // Stored as plain ints, not the zero-padded display strings —
                // the form parses these straight back to Int on save.
                val h = hour.toInt().toString()
                val m = minute.toInt().toString()
                when (end) {
                    WorkHourEnd.START -> onWorkStart(h, m)
                    WorkHourEnd.END -> onWorkEnd(h, m)
                }
            },
            onDismiss = { editing = null }
        )
    }
}

private enum class WorkHourEnd { START, END }

/**
 * A read-only field that acts as a button. `enabled = false` on the inner
 * AppTextField with the click on the wrapping Box is how the rest of this
 * form does it (see the service-duration picker) — it renders as a field but
 * takes no keyboard.
 */
@Composable
private fun TimeDisplayField(
    label: String,
    value: String,
    isError: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(modifier = modifier.clickable(enabled = enabled, onClick = onClick)) {
        AppTextField(
            value = value.toPersianDigits(),
            onValueChange = {},
            label = label,
            isError = isError,
            leadingIcon = {
                Icon(
                    imageVector = Icons.Rounded.Schedule,
                    contentDescription = null,
                    modifier = Modifier.size(24.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            },
            modifier = Modifier.fillMaxWidth(),
            enabled = false, // display-only; the Box above owns the tap
        )
    }
}
