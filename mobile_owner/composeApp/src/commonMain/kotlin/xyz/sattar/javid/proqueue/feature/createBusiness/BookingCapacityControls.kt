package xyz.sattar.javid.proqueue.feature.createBusiness

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import org.koin.compose.koinInject
import xyz.sattar.javid.proqueue.core.i18n.Str
import xyz.sattar.javid.proqueue.core.network.ApiResponse
import xyz.sattar.javid.proqueue.core.utils.toPersianDigits
import xyz.sattar.javid.proqueue.data.remoteDataSource.showcase.ShowcaseApi
import xyz.sattar.javid.proqueue.data.remoteDataSource.showcase.model.BookingCapacityDto
import kotlin.math.roundToInt

/*
 * How a time slot is booked, saved on its own endpoint
 * (`/api/business/<id>/booking-capacity/`) rather than with the whole business
 * form: how many clients one slot takes, and how long a client's booking is held
 * while they pay the deposit. Both show only once the business exists.
 */

/** Loads the business's booking capacity once; null until loaded (or offline). */
@Composable
private fun rememberBookingCapacity(api: ShowcaseApi, businessId: Long): BookingCapacityDto? {
    var loaded by remember(businessId) { mutableStateOf<BookingCapacityDto?>(null) }
    LaunchedEffect(businessId) {
        loaded = (runCatching { api.getBookingCapacity(businessId) }.getOrNull() as? ApiResponse.Success)?.data
    }
    return loaded
}

/** «ظرفیت هم‌زمان هر نوبت»: − / + and a typed number, 1..500; saved with its own button. */
@Composable
fun SlotCapacityControl(businessId: Long?) {
    if (businessId == null || businessId == 0L) {
        Text(Str.capacity_after_create, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        return
    }
    val api = koinInject<ShowcaseApi>()
    val scope = rememberCoroutineScope()
    val loaded = rememberBookingCapacity(api, businessId) ?: return
    var saved by remember(loaded) { mutableStateOf(loaded.slotCapacity) }
    var text by remember(loaded) { mutableStateOf(loaded.slotCapacity.toString()) }
    var status by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    val value = text.filter { it.isDigit() }.toIntOrNull()
    val valid = value != null && value in loaded.minSlotCapacity..loaded.maxSlotCapacity

    fun set(n: Int) {
        text = n.coerceIn(loaded.minSlotCapacity, loaded.maxSlotCapacity).toString()
        status = null
    }

    Column {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center, modifier = Modifier.fillMaxWidth()) {
            FilledTonalIconButton(onClick = { set((value ?: 1) - 1) }, enabled = !busy && (value ?: 1) > loaded.minSlotCapacity) {
                Icon(Icons.Rounded.Remove, contentDescription = Str.decrease)
            }
            Spacer(Modifier.width(12.dp))
            OutlinedTextField(
                value = text,
                onValueChange = { v -> text = v.filter { it.isDigit() }.take(3); status = null },
                singleLine = true,
                enabled = !busy,
                isError = text.isNotEmpty() && !valid,
                textStyle = TextStyle(textAlign = TextAlign.Center, fontWeight = FontWeight.Bold),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.width(110.dp),
            )
            Spacer(Modifier.width(12.dp))
            FilledTonalIconButton(onClick = { set((value ?: 0) + 1) }, enabled = !busy && (value ?: 0) < loaded.maxSlotCapacity) {
                Icon(Icons.Rounded.Add, contentDescription = Str.increase)
            }
        }
        Spacer(Modifier.height(6.dp))
        Text(
            if (valid) Str.slot_capacity_hint(value!!).toPersianDigits() else Str.slot_capacity_range.toPersianDigits(),
            style = MaterialTheme.typography.bodySmall,
            color = if (valid) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.error,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
        SaveRow(status, enabled = valid && value != saved && !busy) {
            val target = value ?: return@SaveRow
            busy = true
            scope.launch {
                val result = runCatching { api.setBookingCapacity(businessId, mapOf("slot_capacity" to target)) }.getOrNull()
                if (result is ApiResponse.Success) {
                    saved = result.data.slotCapacity
                    status = Str.capacity_saved
                } else {
                    status = (result as? ApiResponse.Error)?.message ?: Str.capacity_save_failed
                    text = saved.toString()
                }
                busy = false
            }
        }
    }
}

/** «زمان نگه‌داشتن نوبت تا پرداخت»: a 2..15 minute slider; saved with its own button. */
@Composable
fun HoldMinutesControl(businessId: Long?) {
    if (businessId == null || businessId == 0L) {
        Text(Str.capacity_after_create, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        return
    }
    val api = koinInject<ShowcaseApi>()
    val scope = rememberCoroutineScope()
    val loaded = rememberBookingCapacity(api, businessId) ?: return
    var saved by remember(loaded) { mutableStateOf(loaded.holdMinutes) }
    var minutes by remember(loaded) { mutableStateOf(loaded.holdMinutes.toFloat()) }
    var status by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    val value = minutes.roundToInt()

    Column {
        Text(
            Str.hold_minutes_value(value).toPersianDigits(),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
        Slider(
            value = minutes,
            onValueChange = { minutes = it; status = null },
            valueRange = loaded.minHoldMinutes.toFloat()..loaded.maxHoldMinutes.toFloat(),
            steps = loaded.maxHoldMinutes - loaded.minHoldMinutes - 1,
            enabled = !busy,
        )
        SaveRow(status, enabled = value != saved && !busy) {
            busy = true
            scope.launch {
                val result = runCatching { api.setBookingCapacity(businessId, mapOf("hold_minutes" to value)) }.getOrNull()
                if (result is ApiResponse.Success) {
                    saved = result.data.holdMinutes
                    status = Str.capacity_saved
                } else {
                    status = (result as? ApiResponse.Error)?.message ?: Str.capacity_save_failed
                    minutes = saved.toFloat()
                }
                busy = false
            }
        }
    }
}

@Composable
private fun SaveRow(status: String?, enabled: Boolean, onSave: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(
            status.orEmpty(),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f),
        )
        TextButton(onClick = onSave, enabled = enabled) { Text(Str.capacity_save, fontWeight = FontWeight.Bold) }
    }
}
