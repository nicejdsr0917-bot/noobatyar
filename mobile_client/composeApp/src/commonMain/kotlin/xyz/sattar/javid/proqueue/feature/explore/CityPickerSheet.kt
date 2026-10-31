package xyz.sattar.javid.proqueue.feature.explore

import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.ArrowLeft
import com.composables.icons.lucide.Check
import com.composables.icons.lucide.ChevronRight
import com.composables.icons.lucide.Search
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import xyz.sattar.javid.proqueue.core.location.IranCities
import xyz.sattar.javid.proqueue.core.location.IranCity
import xyz.sattar.javid.proqueue.core.location.IranProvince
import xyz.sattar.javid.proqueue.core.prefs.ALL_IRAN
import xyz.sattar.javid.proqueue.feature.discovery.Hairline
import xyz.sattar.javid.proqueue.feature.discovery.Radius
import xyz.sattar.javid.proqueue.feature.discovery.Space

/**
 * Divar's city picker, one city at a time: "کل ایران", the last few picks,
 * then every province, each opening onto its cities in Divar's order. Typing
 * searches all cities at once, each shown with its province since several
 * names repeat across provinces.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CityPickerSheet(
    /** Divar city id, or [ALL_IRAN]; null when nothing has been picked yet. */
    selectedCode: Int?,
    recentCodes: List<Int>,
    onDismiss: () -> Unit,
    onPick: (SearchArea) -> Unit,
) {
    var provinces by remember { mutableStateOf<List<IranProvince>>(emptyList()) }
    LaunchedEffect(Unit) { provinces = IranCities.provinces() }
    var query by remember { mutableStateOf("") }
    var openProvince by remember { mutableStateOf<IranProvince?>(null) }

    val pickCity: (IranCity, IranProvince) -> Unit = { city, _ -> onPick(city.toArea()) }
    val citiesById = remember(provinces) {
        provinces.flatMap { p -> p.cities.map { it.id to (it to p) } }.toMap()
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.surface,
    ) {
        Column(Modifier.fillMaxHeight(0.92f).navigationBarsPadding()) {
            Row(
                Modifier.padding(horizontal = Space.page, vertical = Space.xs),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                val province = openProvince
                if (province != null && query.isBlank()) {
                    IconButton(onClick = { openProvince = null }) {
                        Icon(Lucide.ArrowLeft, contentDescription = "بازگشت")
                    }
                }
                Text(
                    if (province != null && query.isBlank()) province.name else "انتخاب شهر",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                )
            }
            CitySearchField(
                query = query,
                onQueryChange = { query = it },
                modifier = Modifier.padding(horizontal = Space.page, vertical = Space.sm),
            )

            val q = query.trim()
            val province = openProvince
            LazyColumn(Modifier.fillMaxWidth().weight(1f)) {
                when {
                    q.isNotEmpty() -> {
                        val matches = provinces.flatMap { p ->
                            p.cities.filter { it.name.contains(q) }.map { it to p }
                        }
                        items(matches, key = { "s-${it.first.id}" }) { (city, p) ->
                            CityRow(city.name, subtitle = p.name, selected = city.id == selectedCode) {
                                pickCity(city, p)
                            }
                        }
                    }

                    province != null -> items(province.cities, key = { "c-${it.id}" }) { city ->
                        CityRow(city.name, selected = city.id == selectedCode) { pickCity(city, province) }
                    }

                    else -> {
                        val recent = recentCodes.mapNotNull { code ->
                            if (code == ALL_IRAN) null else citiesById[code]
                        }
                        if (recent.isNotEmpty()) {
                            item(key = "recent-title") { SheetSection("انتخاب‌های قبلی") }
                            items(recent, key = { "r-${it.first.id}" }) { (city, p) ->
                                CityRow(city.name, subtitle = p.name, selected = city.id == selectedCode) {
                                    pickCity(city, p)
                                }
                            }
                        }
                        item(key = "all-title") { SheetSection("همه شهرها") }
                        item(key = "all-iran") {
                            CityRow("کل ایران", selected = selectedCode == ALL_IRAN) { onPick(SearchArea.Anywhere) }
                        }
                        items(provinces, key = { "p-${it.id}" }) { p ->
                            CityRow(p.name, drillDown = true) { openProvince = p }
                        }
                    }
                }
            }
        }
    }
}

fun IranCity.toArea() = SearchArea.City(code = id, label = name, latitude = lat, longitude = lng, radiusKm = radiusKm)

@Composable
private fun SheetSection(title: String) {
    Text(
        title,
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.padding(start = Space.page, end = Space.page, top = Space.lg, bottom = Space.xs),
    )
}

@Composable
private fun CityRow(
    title: String,
    subtitle: String? = null,
    selected: Boolean = false,
    drillDown: Boolean = false,
    onClick: () -> Unit,
) {
    Column {
        Row(
            Modifier
                .fillMaxWidth()
                .clickable(onClick = onClick)
                .padding(horizontal = Space.page, vertical = Space.md),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    title,
                    style = MaterialTheme.typography.bodyLarge,
                    color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                )
                if (subtitle != null) {
                    Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            when {
                selected -> Icon(Lucide.Check, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                drillDown -> Icon(
                    Lucide.ChevronRight,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Hairline(Modifier.padding(horizontal = Space.page))
    }
}

@Composable
private fun CitySearchField(query: String, onQueryChange: (String) -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier
            .fillMaxWidth()
            .height(48.dp)
            .background(MaterialTheme.colorScheme.surfaceVariant, Radius.md)
            .padding(horizontal = Space.md),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Lucide.Search, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.width(Space.sm))
        Box(Modifier.weight(1f)) {
            if (query.isEmpty()) {
                Text(
                    "جستجو در شهرهای ایران",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodyLarge,
                )
            }
            BasicTextField(
                value = query,
                onValueChange = onQueryChange,
                singleLine = true,
                textStyle = MaterialTheme.typography.bodyLarge.copy(color = MaterialTheme.colorScheme.onSurface),
                cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}
