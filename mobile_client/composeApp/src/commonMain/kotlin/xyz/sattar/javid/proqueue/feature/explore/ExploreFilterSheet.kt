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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.Dp
import coil3.compose.AsyncImage
import xyz.sattar.javid.proqueue.core.utils.AppConfig
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
    onSaveMyInsurance: () -> Unit,
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
                if (state.insuranceSlug != null) {
                    if (state.insuranceSlug == state.myInsuranceSlug) {
                        Text(
                            "بیمه من",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(start = Space.page, top = Space.xs)
                        )
                    } else {
                        TextButton(onClick = onSaveMyInsurance, modifier = Modifier.padding(start = Space.sm)) {
                            Text("ذخیره به‌عنوان بیمه من")
                        }
                    }
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
    title: String = "بیمه",
    subtitle: String = "مراکزی که این بیمه را اعلام کرده‌اند؛ قبل از مراجعه هماهنگ کنید.",
    noneLabel: String = "همه بیمه‌ها",
) {
    var query by remember { mutableStateOf("") }
    // Arabic ي/ك typed on some keyboards should still match the Persian names.
    val q = query.trim().replace('ي', 'ی').replace('ك', 'ک')
    val shown = if (q.isEmpty()) insurances else insurances.filter { it.name.contains(q) }
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.surface
    ) {
        Column(Modifier.padding(horizontal = Space.page, vertical = Space.sm)) {
            Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Text(
                subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        SheetSearchField(
            query = query,
            onQueryChange = { query = it },
            placeholder = "جستجوی بیمه",
            modifier = Modifier.padding(horizontal = Space.page, vertical = Space.sm),
        )
        LazyColumn(contentPadding = PaddingValues(bottom = Space.xl), modifier = Modifier.navigationBarsPadding()) {
            if (q.isEmpty()) {
                item { OptionRow(noneLabel, selectedSlug == null, leading = { InsuranceLogo(null) }) { onSelect(null) } }
            } else if (shown.isEmpty()) {
                item {
                    Text(
                        "بیمه‌ای با این نام پیدا نشد",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = Space.page, vertical = Space.lg)
                    )
                }
            }
            val groups = shown.groupBy { it.insuranceType }
            listOf("BASIC" to "بیمه‌های پایه", "SUPPLEMENTARY" to "بیمه‌های تکمیلی").forEach { (type, title) ->
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
                    items(list, key = { it.slug }) { ins ->
                        OptionRow(ins.name, ins.slug == selectedSlug, leading = { InsuranceLogo(ins.logoPath) }) { onSelect(ins.slug) }
                    }
                }
            }
        }
    }
}

@Composable
internal fun OptionRow(
    label: String,
    selected: Boolean,
    leading: (@Composable () -> Unit)? = null,
    onClick: () -> Unit,
) {
    Surface(onClick = onClick, color = Color.Transparent, modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.height(56.dp).padding(horizontal = Space.page), verticalAlignment = Alignment.CenterVertically) {
            if (leading != null) {
                leading()
                Spacer(Modifier.width(Space.md))
            }
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

/**
 * An insurer's logo on a white tile (logos are drawn for white, in either
 * theme), or a shield while the admin hasn't uploaded one / for "all".
 */
@Composable
internal fun InsuranceLogo(logoPath: String?, size: Dp = 36.dp) {
    val url = logoPath?.takeIf { it.isNotBlank() }?.let { if (it.startsWith("http")) it else "${AppConfig.baseUrl}$it" }
    val shape = RoundedCornerShape(10.dp)
    if (url != null) {
        Box(
            Modifier.size(size).clip(shape).background(Color.White)
                .border(1.dp, MaterialTheme.colorScheme.outlineVariant, shape)
                .padding(4.dp),
            contentAlignment = Alignment.Center
        ) {
            AsyncImage(model = url, contentDescription = null, contentScale = ContentScale.Fit, modifier = Modifier.fillMaxSize())
        }
    } else {
        Box(
            Modifier.size(size).clip(shape).background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center
        ) {
            Icon(Lucide.ShieldPlus, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(size * 0.5f))
        }
    }
}
