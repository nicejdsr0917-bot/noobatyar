package xyz.sattar.javid.proqueue.core.ui.components.map

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.LocationOn
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import io.ktor.client.HttpClient
import io.ktor.client.plugins.UserAgent
import io.ktor.client.request.prepareGet
import io.ktor.client.statement.bodyAsChannel
import io.ktor.http.isSuccess
import io.ktor.utils.io.readBuffer
import ovh.plrapps.mapcompose.api.addLayer
import ovh.plrapps.mapcompose.core.TileStreamProvider
import ovh.plrapps.mapcompose.ui.layout.Forced
import ovh.plrapps.mapcompose.ui.state.MapState
import xyz.sattar.javid.proqueue.core.location.GeoPoint
import xyz.sattar.javid.proqueue.core.utils.AppInfo
import kotlin.math.PI
import kotlin.math.atan
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.ln
import kotlin.math.log2
import kotlin.math.pow
import kotlin.math.sinh
import kotlin.math.tan

/**
 * Where the map's tiles come from. Raster tiles on purpose: the street names
 * are drawn on the tile server and arrive as pictures, so Persian is joined
 * and right-to-left correctly on every platform — a vector map would have to
 * shape Persian itself on the device, which is exactly where map libraries
 * still get it wrong.
 *
 * OpenStreetMap's standard tiles for now: free, no key, local (Persian) names
 * inside Iran. Its usage policy asks for the attribution shown on every map
 * ([MapAttribution]) and, from apps, a User-Agent that names the app. To move
 * to another source (a caching proxy on our own server, Neshan, …) change
 * [URL] — nothing else depends on the provider.
 */
object MapTiles {
    const val URL = "https://tile.openstreetmap.org/{z}/{x}/{y}.png"
    const val ATTRIBUTION = "© OpenStreetMap"
    const val MAX_ZOOM = 19
    const val MIN_ZOOM = 5
    const val TILE_SIZE = 256

    /** Where an empty picker opens: Tehran, at city level. */
    val DEFAULT_CENTER = GeoPoint(35.6997, 51.3380)
    const val DEFAULT_ZOOM = 12.0
    /** Close enough to tell which building the pin is on. */
    const val PIN_ZOOM = 17.0
}

// Browsers refuse a scripted User-Agent, and any custom header turns the tile
// fetch into a CORS preflight the tile server does not allow — so on web the
// request goes out bare (the browser's own UA and Referer identify us there).
private val tileClient by lazy {
    HttpClient {
        if (!AppInfo.isWeb) {
            install(UserAgent) { agent = "Noobatyar/${AppInfo.versionName} (+https://noobatyar.ir)" }
        }
    }
}

private val osmTiles = TileStreamProvider { row, col, zoom ->
    runCatching {
        val url = MapTiles.URL
            .replace("{z}", zoom.toString())
            .replace("{x}", col.toString())
            .replace("{y}", row.toString())
        tileClient.prepareGet(url).execute { response ->
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

/**
 * MapState scale for a web-map zoom level. MapState counts physical pixels
 * and scale 1 is [MapTiles.MAX_ZOOM] at one tile pixel per screen pixel; the
 * [density] factor shows a tile pixel per dp instead, as every web map does —
 * otherwise a phone at 3x draws the tiles a third of their size and the
 * street names become unreadable specks.
 */
fun scaleForZoom(zoom: Double, density: Float): Double =
    density * 2.0.pow(zoom - MapTiles.MAX_ZOOM)

/** A MapState showing the tiles, centred on [center] at [zoom]. Call
 *  [MapState.shutdown] when done — [DisposeMapState] does it for a composable. */
fun newTileMapState(center: GeoPoint, zoom: Double, density: Float): MapState {
    val size = MapTiles.TILE_SIZE * 2.0.pow(MapTiles.MAX_ZOOM).toInt()
    val (x, y) = center.toNormalized()
    return MapState(
        levelCount = MapTiles.MAX_ZOOM + 1,
        fullWidth = size,
        fullHeight = size,
        tileSize = MapTiles.TILE_SIZE,
        // Tiles come over the network: more parallel fetches, faster fill.
        workerCount = 16,
    ) {
        minimumScaleMode(Forced(scaleForZoom(MapTiles.MIN_ZOOM.toDouble(), density)))
        // One level past the last tiles, magnified — enough to point at a door.
        maxScale(scaleForZoom(MapTiles.MAX_ZOOM + 1.0, density))
        // Pick the tiles of the zoom level itself (magnified by the density)
        // instead of the next ones down: same picture as the web, readable
        // labels, and a quarter as many tiles to download per screen.
        magnifyingFactor(ceil(log2(density.toDouble().coerceAtLeast(1.0))).toInt())
        // Fetch a ring of tiles just outside the view, so a short pan
        // doesn't uncover blank squares.
        preloadingPadding(MapTiles.TILE_SIZE)
        scroll(x, y)
        scale(scaleForZoom(zoom, density))
    }.apply { addLayer(osmTiles) }
}

/** Releases a [MapState] made in composition when it leaves. */
@Composable
fun DisposeMapState(state: MapState) {
    DisposableEffect(state) { onDispose { state.shutdown() } }
}

internal val PinSize = 48.dp
/** How far above the pin's bottom edge its tip is (the icon's own padding). */
internal val PinTipInset = 4.dp
/** OSM's land colour, behind tiles that haven't arrived yet. */
internal val MapBackground = Color(0xFFF2EFE9)

/** The business pin. Its tip, [PinTipInset] above the bottom edge, is the
 *  location — both the map marker and the picker's centre pin align it so. */
@Composable
fun MapPin(modifier: Modifier = Modifier) {
    Box(modifier = modifier.size(PinSize), contentAlignment = Alignment.TopCenter) {
        // A small shadow at the tip, so the pin reads as standing on the map.
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .offset(y = -(PinTipInset - 2.5.dp))
                .size(width = 12.dp, height = 5.dp)
                .background(Color.Black.copy(alpha = 0.25f), CircleShape)
        )
        Icon(
            imageVector = Icons.Rounded.LocationOn,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(PinSize)
        )
    }
}

/** The tile provider's required credit, in a corner of every map. */
@Composable
fun MapAttribution(modifier: Modifier = Modifier) {
    Text(
        text = MapTiles.ATTRIBUTION,
        style = MaterialTheme.typography.labelSmall,
        color = Color(0xFF374151),
        modifier = modifier
            .background(Color.White.copy(alpha = 0.8f), RoundedCornerShape(6.dp))
            .padding(horizontal = 6.dp, vertical = 2.dp)
    )
}
