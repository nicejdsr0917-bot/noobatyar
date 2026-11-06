package xyz.sattar.javid.proqueue.core.ui.components.city

import xyz.sattar.javid.proqueue.core.ui.components.ForwardChevron
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowLeft
import androidx.compose.material.icons.rounded.LocationCity
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.jetbrains.compose.resources.ExperimentalResourceApi
import proqueue.composeapp.generated.resources.Res
import xyz.sattar.javid.proqueue.core.i18n.Str
import xyz.sattar.javid.proqueue.core.ui.components.AdaptiveSheet

/** A city the owner picked: Divar's city id plus the names shown beside it. */
data class PickedCity(val code: Int, val name: String, val province: String)

@Serializable
data class IranCity(val id: Int, val name: String)

@Serializable
data class IranProvince(val id: Int, val name: String, val cities: List<IranCity>)

@Serializable
private data class IranCitiesFile(val provinces: List<IranProvince>)

/**
 * Iran's provinces and cities as Divar lists them (`files/iran_cities.json`,
 * the same file the backend validates `city_code` against).
 */
object IranCities {
    private var cache: List<IranProvince>? = null
    private val json = Json { ignoreUnknownKeys = true }

    @OptIn(ExperimentalResourceApi::class)
    suspend fun provinces(): List<IranProvince> = cache ?: json
        .decodeFromString<IranCitiesFile>(Res.readBytes("files/iran_cities.json").decodeToString())
        .provinces
        .also { cache = it }
}

/** The business-form field: shows the picked city, opens the picker. */
@Composable
fun BusinessCityField(
    city: PickedCity?,
    onCity: (PickedCity) -> Unit,
    enabled: Boolean,
    error: String?,
    modifier: Modifier = Modifier,
) {
    var showPicker by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(16.dp)
    val borderColor = if (error != null) MaterialTheme.colorScheme.error
    else MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)

    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(shape)
                .border(BorderStroke(1.dp, borderColor), shape)
                .clickable(enabled = enabled) { showPicker = true }
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f), CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Rounded.LocationCity, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = Str.city,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = city?.let { "${it.name}، ${it.province}" } ?: Str.pick_city,
                    style = MaterialTheme.typography.titleSmall,
                    color = if (city == null) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                )
            }
            Icon(
                ForwardChevron,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (error != null) {
            Text(
                text = error,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(start = 16.dp),
            )
        }
    }

    if (showPicker) {
        CityPickerSheet(
            onDismiss = { showPicker = false },
            onPick = {
                onCity(it)
                showPicker = false
            },
        )
    }
}

/**
 * Province, then city — Divar's list in Divar's order. Typing searches every
 * city at once, each shown with its province (several names repeat across
 * provinces).
 */
@Composable
fun CityPickerSheet(onDismiss: () -> Unit, onPick: (PickedCity) -> Unit) {
    var provinces by remember { mutableStateOf<List<IranProvince>>(emptyList()) }
    LaunchedEffect(Unit) { provinces = IranCities.provinces() }
    var query by remember { mutableStateOf("") }
    var openProvince by remember { mutableStateOf<IranProvince?>(null) }
    // One scroll position per list: the province list keeps its place for
    // the way back, and each province's cities start from the top instead of
    // inheriting wherever the province list was scrolled to.
    val provinceListState = rememberLazyListState()
    val cityListState = remember(openProvince) { LazyListState() }

    AdaptiveSheet(onDismissRequest = onDismiss) {
        Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                val province = openProvince
                if (province != null && query.isBlank()) {
                    IconButton(onClick = { openProvince = null }) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowForward, contentDescription = Str.back)
                    }
                }
                Text(
                    text = if (province != null && query.isBlank()) province.name else Str.pick_city,
                    style = MaterialTheme.typography.titleMedium,
                )
            }
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                placeholder = { Text(Str.search_cities) },
                leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
                singleLine = true,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(8.dp))
        }

        val q = query.trim()
        val province = openProvince
        LazyColumn(
            modifier = Modifier.fillMaxWidth().height(460.dp),
            state = if (province != null && query.isBlank()) cityListState else provinceListState,
        ) {
            when {
                q.isNotEmpty() -> {
                    val matches = provinces.flatMap { p ->
                        p.cities.filter { it.name.contains(q) }.map { it to p }
                    }
                    items(matches, key = { it.first.id }) { (city, p) ->
                        CityRow(title = city.name, subtitle = p.name) {
                            onPick(PickedCity(city.id, city.name, p.name))
                        }
                    }
                }

                province != null -> items(province.cities, key = { it.id }) { city ->
                    CityRow(title = city.name) { onPick(PickedCity(city.id, city.name, province.name)) }
                }

                else -> items(provinces, key = { it.id }) { p ->
                    CityRow(title = p.name, drillDown = true) { openProvince = p }
                }
            }
        }
    }
}

@Composable
private fun CityRow(title: String, subtitle: String? = null, drillDown: Boolean = false, onClick: () -> Unit) {
    Column {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onClick)
                .padding(horizontal = 20.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.bodyLarge)
                if (subtitle != null) {
                    Text(
                        subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            if (drillDown) {
                Icon(
                    ForwardChevron,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        HorizontalDivider(modifier = Modifier.padding(horizontal = 20.dp), thickness = 0.5.dp)
    }
}
