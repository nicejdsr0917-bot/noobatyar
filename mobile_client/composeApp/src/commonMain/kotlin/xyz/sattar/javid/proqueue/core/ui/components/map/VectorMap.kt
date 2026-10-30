package xyz.sattar.javid.proqueue.core.ui.components.map

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import org.maplibre.compose.camera.CameraState
import org.maplibre.compose.map.GestureOptions
import org.maplibre.compose.map.MapOptions
import org.maplibre.compose.map.MaplibreMap
import org.maplibre.compose.map.OrnamentOptions
import org.maplibre.compose.style.BaseStyle
import org.maplibre.compose.util.ClickResult
import org.maplibre.compose.util.MapClickHandler
import org.maplibre.compose.util.MaplibreComposable
import xyz.sattar.javid.proqueue.BuildKonfig

/**
 * The vector map, from Map.ir (an Iranian provider, so it keeps working
 * whatever happens to international links). Its key travels in the
 * `x-api-key` header on every Map.ir request (see [installMapAuth]).
 *
 * Without a key (a fresh checkout, a CI build without the secret) the same
 * MapLibre map shows OSM raster tiles instead, so nothing else changes.
 */
object VectorMapStyle {
    val apiKey: String get() = BuildKonfig.MAPIR_API_KEY

    val hasMapIr: Boolean get() = apiKey.isNotBlank()

    // The "min POI" style: streets and names, without a shop on every corner.
    private const val MAPIR_LIGHT = "https://map.ir/vector/styles/main/mapir-xyz-style-min-poi.json"
    private const val MAPIR_DARK = "https://map.ir/vector/styles/main/mapir-style-dark.json"

    /** Map.ir asks for credit like OSM does; the map's corner label shows this. */
    val attribution: String get() = if (hasMapIr) "© Map.ir · OpenStreetMap" else MapTiles.ATTRIBUTION

    fun style(dark: Boolean): BaseStyle =
        if (hasMapIr) BaseStyle.Uri(if (dark) MAPIR_DARK else MAPIR_LIGHT)
        else BaseStyle.Json(osmRasterStyle)

    private val osmRasterStyle = """
        {"version":8,
         "sources":{"osm":{"type":"raster","tileSize":256,"maxzoom":19,
           "tiles":["https://a.tile.openstreetmap.fr/hot/{z}/{x}/{y}.png",
                    "https://b.tile.openstreetmap.fr/hot/{z}/{x}/{y}.png",
                    "https://c.tile.openstreetmap.fr/hot/{z}/{x}/{y}.png"]}},
         "layers":[{"id":"osm","type":"raster","source":"osm"}]}
    """.trimIndent()
}

/**
 * Sets up map networking once per process: the Map.ir key header, and a
 * User-Agent naming the app (OSM's tile policy asks for one).
 */
expect fun installMapAuth(apiKey: String, userAgent: String)

private var authInstalled = false

/**
 * The app's map: Map.ir vector style, no MapLibre ornaments (our own credit
 * chip replaces them). [content] holds MapLibre sources and layers.
 */
@Composable
fun VectorMap(
    cameraState: CameraState,
    dark: Boolean,
    modifier: Modifier = Modifier,
    gestures: GestureOptions = GestureOptions.RotationLocked,
    onMapClick: MapClickHandler = { _, _ -> ClickResult.Pass },
    onMapLongClick: MapClickHandler = { _, _ -> ClickResult.Pass },
    onMapLoadFinished: () -> Unit = {},
    content: @Composable @MaplibreComposable () -> Unit = {},
) {
    if (!authInstalled) {
        installMapAuth(VectorMapStyle.apiKey, "Noobatyar/${xyz.sattar.javid.proqueue.core.utils.AppInfo.versionName} (+https://noobatyar.ir)")
        authInstalled = true
    }
    val style = remember(dark) { VectorMapStyle.style(dark) }
    MaplibreMap(
        modifier = modifier,
        baseStyle = style,
        cameraState = cameraState,
        options = MapOptions(gestureOptions = gestures, ornamentOptions = OrnamentOptions.AllDisabled),
        onMapClick = onMapClick,
        onMapLongClick = onMapLongClick,
        onMapLoadFinished = onMapLoadFinished,
        content = content,
    )
}
