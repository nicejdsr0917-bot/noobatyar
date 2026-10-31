package xyz.sattar.javid.proqueue.feature.explore

import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.Check
import com.composables.icons.lucide.ChevronDown
import com.composables.icons.lucide.ShieldPlus
import com.composables.icons.lucide.Zap
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import xyz.sattar.javid.proqueue.domain.model.business.Insurance
import xyz.sattar.javid.proqueue.feature.discovery.FilterChip
import xyz.sattar.javid.proqueue.feature.discovery.Radius
import xyz.sattar.javid.proqueue.feature.discovery.Space
import xyz.sattar.javid.proqueue.feature.discovery.toPersianDigits

// ── Sheets ───────────────────────────────────────────────────────────────────


/**
 * Everything beyond the category row. Each choice applies at once — the list and
 * the pins behind the sheet update live — so the button only closes it.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun FilterSheet(
    state: ExploreState,
    onDismiss: () -> Unit,
    onSelectRadius: (Double) -> Unit,
    onSelectSort: (SortMode) -> Unit,
    onToggleOnline: () -> Unit,
    onOpenInsurance: () -> Unit,
    onClear: () -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.surface
    ) {
        Column(Modifier.navigationBarsPadding().padding(bottom = Space.lg)) {
            Row(Modifier.padding(start = Space.page, end = Space.sm), verticalAlignment = Alignment.CenterVertically) {
                Text("فیلترها", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                TextButton(onClick = onClear) { Text("پاک کردن") }
            }

            FilterSection("نوبت‌دهی")
            Row(Modifier.padding(horizontal = Space.page), horizontalArrangement = Arrangement.spacedBy(Space.sm)) {
                FilterChip(label = "فقط نوبت آنلاین", icon = Lucide.Zap, selected = state.onlyOnline, onClick = onToggleOnline)
            }

            if (state.category.supportsInsurance) {
                FilterSection("بیمه")
                Row(Modifier.padding(horizontal = Space.page)) {
                    FilterChip(
                        label = state.selectedInsurance?.name ?: "همه بیمه‌ها",
                        icon = Lucide.ShieldPlus,
                        trailingIcon = Lucide.ChevronDown,
                        selected = state.insuranceSlug != null,
                        onClick = onOpenInsurance
                    )
                }
            }

            if (state.hasLocation) {
                FilterSection(if (state.area is SearchArea.NearMe) "فاصله تا من" else "فاصله تا مرکز ${state.area.label}")
                Row(Modifier.padding(horizontal = Space.page), horizontalArrangement = Arrangement.spacedBy(Space.sm)) {
                    radiusOptionsKm.forEach { km ->
                        FilterChip(
                            label = "${km.toInt().toPersianDigits()} کیلومتر",
                            selected = state.radiusKm == km,
                            onClick = { onSelectRadius(km) }
                        )
                    }
                }

                FilterSection("مرتب‌سازی")
                Row(Modifier.padding(horizontal = Space.page), horizontalArrangement = Arrangement.spacedBy(Space.sm)) {
                    SortMode.entries.forEach { mode ->
                        FilterChip(label = mode.label, selected = state.sort == mode, onClick = { onSelectSort(mode) })
                    }
                }
            }

            Button(
                onClick = onDismiss,
                shape = Radius.md,
                modifier = Modifier.fillMaxWidth().padding(start = Space.page, end = Space.page, top = Space.xl).height(52.dp)
            ) {
                Text(
                    if (state.isLoading) "نمایش نتایج" else "نمایش ${state.totalCount.toPersianDigits()} نتیجه",
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

@Composable
internal fun FilterSection(title: String) {
    Text(
        title,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(start = Space.page, top = Space.lg, bottom = Space.sm)
    )
}

@Composable
internal fun InsuranceSheet(
    insurances: List<Insurance>,
    selectedSlug: String?,
    onDismiss: () -> Unit,
    onSelect: (String?) -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.surface
    ) {
        LazyColumn(contentPadding = PaddingValues(bottom = Space.xl), modifier = Modifier.navigationBarsPadding()) {
            item {
                Column(Modifier.padding(horizontal = Space.page, vertical = Space.sm)) {
                    Text("بیمه", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    Text(
                        "مراکزی که این بیمه را اعلام کرده‌اند؛ قبل از مراجعه هماهنگ کنید.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                OptionRow("همه بیمه‌ها", selectedSlug == null) { onSelect(null) }
            }
            val groups = insurances.groupBy { it.insuranceType }
            listOf("SUPPLEMENTARY" to "بیمه‌های تکمیلی", "BASIC" to "بیمه‌های پایه").forEach { (type, title) ->
                val list = groups[type].orEmpty()
                if (list.isNotEmpty()) {
                    item(key = "h-$type") {
                        Text(
                            title,
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(start = Space.page, top = Space.lg, bottom = Space.xs)
                        )
                    }
                    items(list, key = { it.slug }) { ins -> OptionRow(ins.name, ins.slug == selectedSlug) { onSelect(ins.slug) } }
                }
            }
        }
    }
}

@Composable
internal fun OptionRow(label: String, selected: Boolean, onClick: () -> Unit) {
    Surface(onClick = onClick, color = Color.Transparent, modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.height(52.dp).padding(horizontal = Space.page), verticalAlignment = Alignment.CenterVertically) {
            Text(
                label,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f)
            )
            if (selected) Icon(Lucide.Check, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
        }
    }
}
