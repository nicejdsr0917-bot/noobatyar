package xyz.sattar.javid.proqueue.core.ui.components.map

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.unit.dp
import io.ktor.client.HttpClient
import io.ktor.client.plugins.UserAgent
import io.ktor.client.request.prepareGet
import io.ktor.client.statement.bodyAsChannel
import io.ktor.http.isSuccess
import io.ktor.utils.io.readBuffer
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.io.Buffer
import kotlinx.io.readByteArray
import ovh.plrapps.mapcompose.api.addLayer
import ovh.plrapps.mapcompose.api.setColorFilterProvider
import ovh.plrapps.mapcompose.api.setMapBackground
import ovh.plrapps.mapcompose.core.TileStreamProvider
import ovh.plrapps.mapcompose.ui.layout.Forced
import ovh.plrapps.mapcompose.ui.state.MapState
import xyz.sattar.javid.proqueue.core.utils.AppInfo
import kotlin.math.PI
import kotlin.math.asin
import kotlin.math.atan
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.ln
import kotlin.math.log2
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sinh
import kotlin.math.sqrt
import kotlin.math.tan

data class GeoPoint(val latitude: Double, val longitude: Double)

/**
 * Raster tiles, as in the owner app's map (mobile_owner …/map/TileMap.kt), so
 * Persian street names arrive pre-shaped and RTL on every platform.
 *
 * The Humanitarian (HOT) style served by OSM France rather than
 * tile.openstreetmap.org: measured from Iran (2026-10-27) it answered in
 * ~0.7 s while the main OSM server took 10 s or refused outright, it labels
 * streets in Persian, and its softer palette lets the pins stand out. CARTO's
 * cleaner basemaps now need an API key. For production these should go
 * through a caching proxy on our own server — change [URL] and nothing else.
 */
object MapTiles {
    const val URL = "https://{s}.tile.openstreetmap.fr/hot/{z}/{x}/{y}.png"
    private val SUBDOMAINS = listOf("a", "b", "c")
    const val ATTRIBUTION = "© OpenStreetMap · HOT"
    const val MAX_ZOOM = 19
    const val MIN_ZOOM = 5
    const val TILE_SIZE = 256

    /** Where the map opens with no location: Tehran, at city level. */
    val DEFAULT_CENTER = GeoPoint(35.6997, 51.3380)
    const val DEFAULT_ZOOM = 12.0

    /** Spreads tiles over the server's hostnames, so more download in parallel. */
    fun url(zoom: Int, col: Int, row: Int): String = URL
        .replace("{s}", SUBDOMAINS[(col + row) % SUBDOMAINS.size])
        .replace("{z}", zoom.toString())
        .replace("{x}", col.toString())
        .replace("{y}", row.toString())
}

// OSM's usage policy asks apps to name themselves in the User-Agent.
private val tileClient by lazy {
    HttpClient {
        install(UserAgent) { agent = "Noobatyar/${AppInfo.versionName} (+https://noobatyar.ir)" }
    }
}

/**
 * How many tiles have arrived since launch. A screen that animates over the
 * map waits for this to move, so the motion starts on a drawn map rather than
 * on the blank ground.
 */
val loadedTileCount = MutableStateFlow(0)

private val osmTiles = TileStreamProvider { row, col, zoom ->
    runCatching {
        tileClient.prepareGet(MapTiles.url(zoom, col, row)).execute { response ->
            if (!response.status.isSuccess()) return@execute null
            // Restyled on the way in (see TileRecolor) — before the tile is decoded or cached.
            val png = response.bodyAsChannel().readBuffer().readByteArray()
            Buffer().apply { write(TileRecolor.recolor(png)) }
                .also { loadedTileCount.update { n -> n + 1 } }
        }
    }.getOrNull()
}

/** Web Mercator, normalized to the [0..1] square MapState works in. */
fun GeoPoint.toNormalized(): Pair<Double, Double> {
    val x = (longitude + 180.0) / 360.0
    val latRad = latitude * PI / 180.0
    val y = (1.0 - ln(tan(latRad) + 1.0 / cos(latRad)) / PI) / 2.0
    return x.coerceIn(0.0, 1.0) to y.coerceIn(0.0, 1.0)
}

fun geoPointFromNormalized(x: Double, y: Double): GeoPoint {
    val longitude = x * 360.0 - 180.0
    val latitude = atan(sinh(PI * (1.0 - 2.0 * y))) * 180.0 / PI
    return GeoPoint(latitude, longitude)
}

/** Great-circle distance in km. */
fun GeoPoint.distanceKmTo(other: GeoPoint): Double {
    val r = 6371.0
    val dLat = (other.latitude - latitude) * PI / 180.0
    val dLng = (other.longitude - longitude) * PI / 180.0
    val a = sin(dLat / 2).pow(2) +
        cos(latitude * PI / 180.0) * cos(other.latitude * PI / 180.0) * sin(dLng / 2).pow(2)
    return 2 * r * asin(sqrt(a))
}

// ── Theme ────────────────────────────────────────────────────────────────────
//
// The colours themselves come from TileRecolor (soft, natural, readable). Dark
// mode shows the same map dimmed rather than inverted: inverting raster tiles
// turns white streets black on a dark ground — the contrast that makes the map
// readable — and sends colours wrong (parks pink).

/** The recoloured map at 80 %, so it doesn't glare out of a dark screen. */
private val DIM = ColorFilter.colorMatrix(
    ColorMatrix(
        floatArrayOf(
            0.8f, 0f, 0f, 0f, 0f,
            0f, 0.8f, 0f, 0f, 0f,
            0f, 0f, 0.8f, 0f, 0f,
            0f, 0f, 0f, 1f, 0f,
        )
    )
)

/** What the map shows before (or between) tiles: the recoloured ground. */
fun mapBackground(dark: Boolean): Color = if (dark) Color(0xFFBDBEBA) else Color(0xFFECEDE9)

/** Applies the theme to the tiles. Call again when the theme flips. */
fun MapState.applyTileTint(dark: Boolean) {
    setColorFilterProvider { _, _, _ -> if (dark) DIM else null }
    setMapBackground(mapBackground(dark))
}

/** MapState scale for a web-map zoom level, one tile pixel per dp. */
fun scaleForZoom(zoom: Double, density: Float): Double =
    density * 2.0.pow(zoom - MapTiles.MAX_ZOOM)

/** A MapState showing the tiles, centred on [center] at [zoom]. Release it with [DisposeMapState]. */
fun newTileMapState(center: GeoPoint, zoom: Double, density: Float): MapState {
    val size = MapTiles.TILE_SIZE * 2.0.pow(MapTiles.MAX_ZOOM).toInt()
    val (x, y) = center.toNormalized()
    return MapState(
        levelCount = MapTiles.MAX_ZOOM + 1,
        fullWidth = size,
        fullHeight = size,
        tileSize = MapTiles.TILE_SIZE,
        workerCount = 16,
    ) {
        minimumScaleMode(Forced(scaleForZoom(MapTiles.MIN_ZOOM.toDouble(), density)))
        maxScale(scaleForZoom(MapTiles.MAX_ZOOM + 1.0, density))
        magnifyingFactor(ceil(log2(density.toDouble().coerceAtLeast(1.0))).toInt())
        preloadingPadding(MapTiles.TILE_SIZE)
        scroll(x, y)
        scale(scaleForZoom(zoom, density))
    }.apply { addLayer(osmTiles) }
}

@Composable
fun DisposeMapState(state: MapState) {
    DisposableEffect(state) { onDispose { state.shutdown() } }
}

/** Our address, signed in the map's corner next to the tile credit. */
const val MAP_SIGNATURE = "noobatyar.ir"

/**
 * The corner label of every map: our address on top in the accent colour,
 * the tile provider's required credit under it. One chip, so the credit is
 * never hidden behind the signature.
 */
@Composable
fun MapAttribution(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.75f), RoundedCornerShape(8.dp))
            .padding(horizontal = 8.dp, vertical = 4.dp),
        horizontalAlignment = Alignment.Start,
    ) {
        Text(
            text = MAP_SIGNATURE,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary,
        )
        Text(
            text = MapTiles.ATTRIBUTION,
            // Starts with "©", a neutral character: in the app's RTL layout it
            // would otherwise jump to the end of the line.
            style = MaterialTheme.typography.labelSmall.copy(textDirection = TextDirection.Ltr),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
