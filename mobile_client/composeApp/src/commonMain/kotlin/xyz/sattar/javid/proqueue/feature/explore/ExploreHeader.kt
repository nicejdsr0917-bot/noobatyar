package xyz.sattar.javid.proqueue.feature.explore

import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.ChevronDown
import com.composables.icons.lucide.LocateFixed
import com.composables.icons.lucide.Search
import com.composables.icons.lucide.SlidersHorizontal
import com.composables.icons.lucide.X
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.runtime.getValue
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import xyz.sattar.javid.proqueue.feature.discovery.CategoryGroup
import xyz.sattar.javid.proqueue.feature.discovery.FilterChip
import xyz.sattar.javid.proqueue.feature.discovery.Hairline
import xyz.sattar.javid.proqueue.feature.discovery.Radius
import xyz.sattar.javid.proqueue.feature.discovery.Space
import xyz.sattar.javid.proqueue.feature.stories.StoryRow
import xyz.sattar.javid.proqueue.feature.discovery.toPersianDigits

// ── Header ───────────────────────────────────────────────────────────────────


@Composable
internal fun Header(
    state: ExploreState,
    /** 0..1: how far the results sheet has merged into this toolbar. */
    merged: Float,
    showLocation: Boolean,
    onLocationClick: () -> Unit,
    onQueryChange: (String) -> Unit,
    onSubmit: (String) -> Unit,
    onFocusChange: (Boolean) -> Unit,
    onOpenFilters: () -> Unit,
    onSelectCategory: (CategoryGroup) -> Unit,
) {
    Column(
        Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.background)
            .windowInsetsPadding(WindowInsets.statusBars)
    ) {
        // City picker on the start side, the story row filling the rest.
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (showLocation) Surface(
                onClick = onLocationClick,
                color = Color.Transparent,
                modifier = Modifier.padding(start = Space.sm, top = Space.sm)
            ) {
                Column(Modifier.padding(horizontal = Space.sm, vertical = Space.xs)) {
                    Text("جستجو در", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        val area = state.area
                        val label = when {
                            state.locationStatus == LocationStatus.Locating -> "در حال یافتن موقعیت…"
                            area is SearchArea.NearMe -> "نزدیک من · ${state.radiusKm.toInt().toPersianDigits()} کیلومتر"
                            area is SearchArea.Picked -> "روی نقشه · ${state.radiusKm.toInt().toPersianDigits()} کیلومتر"
                            else -> area.label
                        }
                        Text(label, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        Icon(Lucide.ChevronDown, contentDescription = "تغییر موقعیت", modifier = Modifier.size(20.dp))
                    }
                }
            }
            StoryRow(
                cityCode = (state.area as? SearchArea.City)?.code,
                modifier = Modifier.weight(1f).padding(top = Space.sm),
            )
        }
        Row(
            Modifier.padding(horizontal = Space.page, vertical = Space.sm),
            verticalAlignment = Alignment.CenterVertically
        ) {
            SearchField(
                query = state.query,
                onQueryChange = onQueryChange,
                onSubmit = onSubmit,
                onFocusChange = onFocusChange,
                modifier = Modifier.weight(1f)
            )
            Spacer(Modifier.width(Space.sm))
            FilterButton(count = state.extraFilterCount(), onClick = onOpenFilters)
        }
        CategoryBar(selected = state.category, onSelect = onSelectCategory)
        // Takes over from the sheet's shadow once the two have merged.
        Hairline(Modifier.alpha(merged))
    }
}

/**
 * Every category group in one row of equal cells: the whole choice reads at a
 * glance, with nothing hidden past the screen edge.
 */
@Composable
internal fun CategoryBar(selected: CategoryGroup, onSelect: (CategoryGroup) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(start = Space.sm, end = Space.sm, bottom = Space.sm)) {
        CategoryGroup.entries.forEach { group ->
            CategoryCell(
                group = group,
                selected = group == selected,
                onClick = { onSelect(group) },
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
private fun CategoryCell(group: CategoryGroup, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val tile by animateColorAsState(
        if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant
    )
    val glyph by animateColorAsState(
        if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
    )
    Column(
        modifier
            .clip(Radius.md)
            .clickable(role = Role.Tab, onClick = onClick)
            .semantics { this.selected = selected }
            .padding(vertical = Space.xs),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            Modifier.size(44.dp).background(tile, Radius.md),
            contentAlignment = Alignment.Center
        ) {
            Icon(group.icon, contentDescription = null, tint = glyph, modifier = Modifier.size(22.dp))
        }
        Spacer(Modifier.height(Space.xs))
        Text(
            group.shortLabel,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
            color = if (selected) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            softWrap = false,
            overflow = TextOverflow.Clip
        )
    }
}

/** Square accent button beside the search field, with a count of filters that are on. */
@Composable
internal fun FilterButton(count: Int, onClick: () -> Unit) {
    Box {
        Surface(
            onClick = onClick,
            shape = Radius.md,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(48.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(Lucide.SlidersHorizontal, contentDescription = "فیلترها", tint = MaterialTheme.colorScheme.onPrimary)
            }
        }
        if (count > 0) {
            Box(
                Modifier
                    .align(Alignment.TopEnd)
                    .offset(x = 4.dp, y = (-4).dp)
                    .size(18.dp)
                    .background(MaterialTheme.colorScheme.error, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Text(count.toPersianDigits(), color = MaterialTheme.colorScheme.onError, style = MaterialTheme.typography.labelSmall)
            }
        }
    }
}

/** Drag handle that fades out as the sheet merges into the toolbar. */
@Composable
internal fun SheetHandle(alpha: Float) {
    Box(Modifier.fillMaxWidth().height(22.dp * alpha.coerceAtLeast(0.4f)), contentAlignment = Alignment.Center) {
        Box(
            Modifier
                .size(width = 36.dp, height = 4.dp)
                .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f * alpha), CircleShape)
        )
    }
}

@Composable
internal fun SearchField(
    query: String,
    onQueryChange: (String) -> Unit,
    onSubmit: (String) -> Unit,
    onFocusChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier
            .fillMaxWidth()
            .height(48.dp)
            .background(MaterialTheme.colorScheme.surfaceVariant, Radius.md)
            .padding(horizontal = Space.md),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(Lucide.Search, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.width(Space.sm))
        Box(Modifier.weight(1f)) {
            if (query.isEmpty()) {
                Text(
                    "پزشک، سالن، تخصص یا محله",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodyLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            BasicTextField(
                value = query,
                onValueChange = onQueryChange,
                singleLine = true,
                textStyle = MaterialTheme.typography.bodyLarge.copy(color = MaterialTheme.colorScheme.onSurface),
                cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { onSubmit(query) }),
                modifier = Modifier.fillMaxWidth().onFocusChanged { onFocusChange(it.isFocused) }
            )
        }
        AnimatedVisibility(visible = query.isNotEmpty()) {
            Surface(onClick = { onQueryChange("") }, shape = CircleShape, color = Color.Transparent) {
                Icon(
                    Lucide.X,
                    contentDescription = "پاک کردن",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(Space.xs).size(18.dp)
                )
            }
        }
    }
}

// ── Inline states ────────────────────────────────────────────────────────────


@Composable
internal fun LocationNotice(status: LocationStatus, onLocate: () -> Unit, onOpenSettings: () -> Unit) {
    val blocked = status == LocationStatus.Denied || status == LocationStatus.Disabled
    val text = when (status) {
        LocationStatus.Denied -> "دسترسی به موقعیت داده نشده است."
        LocationStatus.Disabled -> "موقعیت‌یاب گوشی خاموش است."
        LocationStatus.Unavailable -> "موقعیت شما پیدا نشد."
        LocationStatus.Locating -> "در حال یافتن موقعیت…"
        else -> "برای نزدیک‌ترین مراکز، موقعیت را فعال کنید."
    }
    Row(
        Modifier
            .padding(horizontal = Space.page, vertical = Space.xs)
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceVariant, Radius.md)
            .padding(start = Space.md, end = Space.xs),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(Lucide.LocateFixed, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(Space.md))
        Text(text, style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f).padding(vertical = Space.md))
        if (status == LocationStatus.Locating) {
            CircularProgressIndicator(Modifier.padding(Space.md).size(18.dp), strokeWidth = 2.dp)
        } else {
            TextButton(onClick = if (blocked) onOpenSettings else onLocate) {
                Text(if (blocked) "تنظیمات" else "فعال‌سازی", fontWeight = FontWeight.Bold)
            }
        }
    }
}
