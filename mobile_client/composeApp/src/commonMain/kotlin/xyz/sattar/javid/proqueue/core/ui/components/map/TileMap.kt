package xyz.sattar.javid.proqueue.core.ui.components.map

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.unit.dp
import io.ktor.client.HttpClient
import io.ktor.client.plugins.UserAgent
import io.ktor.client.request.prepareGet
import io.ktor.client.statement.bodyAsChannel
import io.ktor.http.isSuccess
import io.ktor.utils.io.readBuffer
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

private val osmTiles = TileStreamProvider { row, col, zoom ->
    runCatching {
        tileClient.prepareGet(MapTiles.url(zoom, col, row)).execute { response ->
            if (response.status.isSuccess()) response.bodyAsChannel().readBuffer() else null
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

// ── Tile tint ────────────────────────────────────────────────────────────────
//
// The raw tiles are drawn for a general-purpose map: saturated roads, green
// parks, orange highways. Behind our pins they compete for attention, so the
// tiles are toned down — mostly grey in light mode, inverted to a deep slate
// in dark mode (so a dark app doesn't flash a white map). Labels stay legible
// in both because the filter keeps contrast, it only drains colour.

/** 4×5 colour matrices, applied left to right: [first] runs first. */
private fun concat(first: FloatArray, second: FloatArray): FloatArray {
    val out = FloatArray(20)
    for (row in 0 until 4) {
        for (col in 0 until 5) {
            var v = if (col == 4) second[row * 5 + 4] else 0f
            for (k in 0 until 4) v += second[row * 5 + k] * first[k * 5 + col]
            out[row * 5 + col] = v
        }
    }
    return out
}

private fun saturation(s: Float): FloatArray {
    val r = 0.2126f * (1 - s); val g = 0.7152f * (1 - s); val b = 0.0722f * (1 - s)
    return floatArrayOf(
        r + s, g, b, 0f, 0f,
        r, g + s, b, 0f, 0f,
        r, g, b + s, 0f, 0f,
        0f, 0f, 0f, 1f, 0f,
    )
}

/** Scales each channel by [scale] and adds [rgb] (0..255). */
private fun scaleAdd(scale: Float, rgb: Triple<Float, Float, Float>) = floatArrayOf(
    scale, 0f, 0f, 0f, rgb.first,
    0f, scale, 0f, 0f, rgb.second,
    0f, 0f, scale, 0f, rgb.third,
    0f, 0f, 0f, 1f, 0f,
)

private val INVERT = floatArrayOf(
    -1f, 0f, 0f, 0f, 255f,
    0f, -1f, 0f, 0f, 255f,
    0f, 0f, -1f, 0f, 255f,
    0f, 0f, 0f, 1f, 0f,
)

/**
 * Light: almost no colour left, compressed towards white with a cool cast —
 * roads become pale grey lines on near-white, like Apple's or Google's light
 * maps, instead of the HOT style's brown arterials. Tuned on Tehran tiles
 * side by side (2026-10-27): labels stay readable at 0.84 contrast.
 */
private val LIGHT_TINT = ColorFilter.colorMatrix(
    ColorMatrix(concat(saturation(0.12f), scaleAdd(0.84f, Triple(36f, 38f, 46f))))
)

/** Dark: grey, inverted, then pulled down onto a cool slate under the app's dark surfaces. */
private val DARK_TINT = ColorFilter.colorMatrix(
    ColorMatrix(concat(concat(saturation(0.08f), INVERT), scaleAdd(0.64f, Triple(16f, 17f, 28f))))
)

/** What the map shows before (or between) tiles, matching each tint. */
fun mapBackground(dark: Boolean): Color = if (dark) Color(0xFF191A24) else Color(0xFFF1F2F6)

/** Tints every tile for the current theme. Call again when the theme flips. */
fun MapState.applyTileTint(dark: Boolean) {
    val filter = if (dark) DARK_TINT else LIGHT_TINT
    setColorFilterProvider { _, _, _ -> filter }
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

/** The tile provider's required credit, in a corner of every map. */
@Composable
fun MapAttribution(modifier: Modifier = Modifier) {
    Text(
        text = MapTiles.ATTRIBUTION,
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier
            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.75f), RoundedCornerShape(6.dp))
            .padding(horizontal = 6.dp, vertical = 2.dp)
    )
}
