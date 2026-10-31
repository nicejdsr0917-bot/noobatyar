package xyz.sattar.javid.proqueue.feature.explore

import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.RefreshCw
import com.composables.icons.lucide.Search
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.Canvas
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import ovh.plrapps.mapcompose.api.Custom
import ovh.plrapps.mapcompose.api.ExperimentalClusteringApi
import ovh.plrapps.mapcompose.api.addClusterer
import ovh.plrapps.mapcompose.api.addPath
import ovh.plrapps.mapcompose.api.removePath
import ovh.plrapps.mapcompose.api.setClustererExemptList
import ovh.plrapps.mapcompose.ui.state.markers.model.RenderingStrategy
import ovh.plrapps.mapcompose.api.addMarker
import ovh.plrapps.mapcompose.api.idleStateFlow
import ovh.plrapps.mapcompose.api.onLongPress
import ovh.plrapps.mapcompose.api.onMarkerClick
import ovh.plrapps.mapcompose.api.onTap
import ovh.plrapps.mapcompose.api.onTouchDown
import ovh.plrapps.mapcompose.api.removeAllMarkers
import ovh.plrapps.mapcompose.api.scale
import ovh.plrapps.mapcompose.api.scrollTo
import ovh.plrapps.mapcompose.api.updateMarkerZ
import ovh.plrapps.mapcompose.api.visibleBoundingBox
import ovh.plrapps.mapcompose.ui.MapUI
import xyz.sattar.javid.proqueue.core.ui.components.map.DisposeMapState
import xyz.sattar.javid.proqueue.core.ui.components.map.GeoPoint
import xyz.sattar.javid.proqueue.core.ui.components.map.MapAttribution
import xyz.sattar.javid.proqueue.core.ui.components.map.MapTiles
import ovh.plrapps.mapcompose.ui.state.MapState
import xyz.sattar.javid.proqueue.core.ui.components.map.applyTileTint
import xyz.sattar.javid.proqueue.core.ui.components.map.geoPointFromNormalized
import xyz.sattar.javid.proqueue.core.ui.components.map.mapBackground
import xyz.sattar.javid.proqueue.core.ui.components.map.newTileMapState
import xyz.sattar.javid.proqueue.core.ui.components.map.toNormalized
import xyz.sattar.javid.proqueue.domain.model.business.Business
import xyz.sattar.javid.proqueue.ui.theme.LocalIsDarkTheme



internal const val ME_MARKER = "me"
internal const val CLUSTERER = "results"
internal const val RADIUS_PATH = "radius"
internal const val AREA_LOGO_MARKER = "area-logo"

/** The area's mark is this share of the ring's diameter, and barely there. */
private const val AREA_LOGO_SHARE = 0.5f
private const val AREA_LOGO_ALPHA = 0.10f
private const val WORLD_PX = MapTiles.TILE_SIZE.toDouble() * (1 shl MapTiles.MAX_ZOOM)

@Composable
private fun AreaLogo(state: MapState, center: GeoPoint, radiusKm: Double) {
    val density = LocalDensity.current.density
    // Kilometres to the map's own pixels: the world is WORLD_PX wide at scale 1.
    val kmPerWorld = 40_075.0 * kotlin.math.cos(center.latitude * kotlin.math.PI / 180.0)
    val diameterPx = radiusKm * 2 / kmPerWorld * WORLD_PX * state.scale
    val size = (diameterPx / density * AREA_LOGO_SHARE).toFloat().coerceIn(32f, 1_200f).dp
    NoobatyarMark(MaterialTheme.colorScheme.primary.copy(alpha = AREA_LOGO_ALPHA), Modifier.size(size))
}

/**
 * The brand mark drawn as lines (an open ring, a tick and a dot), so a faint tint
 * of it reads as a watermark; the app icon is a solid square and would show as one.
 */
@Composable
private fun NoobatyarMark(color: Color, modifier: Modifier) {
    Canvas(modifier) {
        val s = size.minDimension
        val c = Offset(s / 2, s / 2)
        val stroke = Stroke(width = s * 0.07f, cap = StrokeCap.Round, join = StrokeJoin.Round)
        val r = s * 0.37f
        drawArc(color, startAngle = -68f, sweepAngle = 316f, useCenter = false, topLeft = Offset(c.x - r, c.y - r), size = Size(2 * r, 2 * r), style = stroke)
        val tick = Path().apply {
            moveTo(c.x - s * 0.186f, c.y + s * 0.029f)
            lineTo(c.x - s * 0.057f, c.y + s * 0.157f)
            lineTo(c.x + s * 0.2f, c.y - s * 0.1f)
        }
        drawPath(tick, color, style = stroke)
        drawCircle(color, radius = s * 0.045f, center = Offset(c.x, c.y - s * 0.4f))
    }
}

/** Smallest box the camera fits to, in normalized units (≈ 2 km): one pin
 *  alone shouldn't zoom the map down to the building. */
internal const val MIN_SPAN = 0.00005

/** Zooming into a cluster may go this close (≈ 100 m) — enough to pull pins a few doors apart. */
internal const val CLUSTER_MIN_SPAN = 0.0000025

/** A search centre counts as "moved away from" past this share of the visible width. */
internal const val MOVED_THRESHOLD = 0.2

/** A point on the map plus a radius that covers what's on screen around it. */
data class MapArea(val center: GeoPoint, val radiusKm: Double)

/**
 * The results on a map.
 *
 * - One pin per business with coordinates, coloured by category; listings
 *   that can't take a booking get a quieter, outlined pin.
 * - The camera fits the pins into the strip the sheet leaves uncovered
 *   (the top [visibleFraction] of this composable).
 * - Moving the map by hand offers "search this area"; a long press searches
 *   around the pressed point; [pickMode] shows a fixed centre pin to aim with.
 */
@OptIn(ExperimentalClusteringApi::class)
@Composable
fun ExploreMap(
    businesses: List<Business>,
    area: SearchArea,
    radiusKm: Double,
    selectedId: State<Long?>,
    onSelect: (Long?) -> Unit,
    onSearchArea: (MapArea) -> Unit,
    onLocate: () -> Unit,
    locating: Boolean,
    pickMode: Boolean,
    onTogglePickMode: () -> Unit,
    visibleFraction: Float,
    bottomInset: Dp,
    modifier: Modifier = Modifier,
) {
    val density = LocalDensity.current.density
    val dark = LocalIsDarkTheme.current
    val scope = rememberCoroutineScope()
    val state = remember {
        val center = area.latitude?.let { lat -> GeoPoint(lat, area.longitude!!) } ?: MapTiles.DEFAULT_CENTER
        newTileMapState(center, MapTiles.DEFAULT_ZOOM, density)
    }
    DisposeMapState(state)
    LaunchedEffect(dark) { state.applyTileTint(dark) }

    // Set by a finger on the map, cleared by every camera move we make ourselves.
    var userMoved by remember { mutableStateOf(false) }
    val select by rememberUpdatedState(onSelect)
    val searchArea by rememberUpdatedState(onSearchArea)
    val fraction by rememberUpdatedState(visibleFraction)

    LaunchedEffect(state) {
        // Pins closer than this merge into a count bubble. Tapping one fits its
        // pins above the sheet (the library's own zoom ignores the sheet and
        // can leave some of them behind it).
        state.addClusterer(
            CLUSTERER,
            clusteringThreshold = 44.dp,
            clusterClickBehavior = Custom(withDefaultBehavior = false) { cluster ->
                scope.launch {
                    fitCamera(state, cluster.markers.map { it.x to it.y }, fraction, minSpan = CLUSTER_MIN_SPAN)
                    userMoved = false
                }
            },
        ) { ids ->
            { ClusterBubble(count = ids.size) }
        }
        state.onMarkerClick { id, _, _ -> id.toLongOrNull()?.let(select) }
        state.onTap { _, _ -> select(null) }
        state.onTouchDown { userMoved = true }
        state.onLongPress { x, y ->
            scope.launch { searchArea(areaAround(state, geoPointFromNormalized(x, y), fraction)) }
        }
    }

    val placed = remember(businesses) {
        businesses.mapNotNull { b ->
            val lat = b.latitude ?: return@mapNotNull null
            val lng = b.longitude ?: return@mapNotNull null
            b to GeoPoint(lat, lng).toNormalized()
        }
    }
    // The area the user chose: a city shows its own radius; "near me", a point picked
    // on the map and the radius chips show theirs.
    val areaCenter = area.latitude?.let { GeoPoint(it, area.longitude!!) }
    val areaRadiusKm = (area as? SearchArea.City)?.radiusKm ?: radiusKm
    val fitsArea = areaCenter != null
    val me = (area as? SearchArea.NearMe)?.let { GeoPoint(it.latitude, it.longitude).toNormalized() }
    val picked = (area as? SearchArea.Picked)?.let { GeoPoint(it.latitude, it.longitude).toNormalized() }

    LaunchedEffect(placed, me, picked, areaCenter, areaRadiusKm) {
        state.removeAllMarkers()
        // The area carries a faint Noobatyar mark, sized to the ring so it grows with the zoom.
        areaCenter?.let { c ->
            val (x, y) = c.toNormalized()
            state.addMarker(AREA_LOGO_MARKER, x, y, relativeOffset = Offset(-0.5f, -0.5f), clickable = false, zIndex = -1f) {
                AreaLogo(state, c, areaRadiusKm)
            }
        }
        me?.let { (x, y) ->
            state.addMarker(ME_MARKER, x, y, relativeOffset = Offset(-0.5f, -0.5f), clickable = false, zIndex = 0f) {
                MyLocationDot()
            }
        }
        picked?.let { (x, y) ->
            state.addMarker("picked", x, y, relativeOffset = Offset(-0.5f, -0.5f), clickable = false, zIndex = 0f) {
                PickedAreaDot()
            }
        }
        placed.forEach { (business, xy) ->
            state.addMarker(
                business.id.toString(), xy.first, xy.second,
                relativeOffset = Offset(-0.5f, -1f),
                zIndex = 1f,
                renderingStrategy = RenderingStrategy.Clustering(CLUSTERER),
            ) {
                BusinessPin(business, selected = selectedId.value == business.id)
            }
        }
        // A chosen area places the camera itself (below); only the whole directory fits to its pins.
        if (!fitsArea) {
            fitCamera(state, placed.map { it.second } + listOfNotNull(me), fraction)
        }
        userMoved = false
    }

    // The search area as a soft ring: what "۱۰ کیلومتر" means, drawn on the map.
    val ringColor = MaterialTheme.colorScheme.primary
    val center = area.latitude?.let { GeoPoint(it, area.longitude!!) }
    val ringKm = areaRadiusKm
    LaunchedEffect(center, ringKm, ringColor) {
        state.removePath(RADIUS_PATH)
        center ?: return@LaunchedEffect
        state.addPath(
            RADIUS_PATH,
            // Outline only: at street zoom the ring is larger than the screen,
            // and a fill would just wash the whole map in the accent colour.
            width = 2.dp,
            color = ringColor.copy(alpha = 0.55f),
        ) {
            addPoints(circlePoints(center, ringKm))
        }
    }

    // Choosing an area (a city, "near me", another radius) zooms the map onto it; its
    // businesses load in the list and show up as pins.
    LaunchedEffect(areaCenter, areaRadiusKm) {
        val c = areaCenter ?: return@LaunchedEffect
        fitArea(state, c, areaRadiusKm, fraction)
        userMoved = false
    }

    // The selected pin draws over its neighbours, never folds into a cluster,
    // and slides into view.
    var lastSelected by remember { mutableStateOf<Long?>(null) }
    LaunchedEffect(selectedId.value) {
        lastSelected?.let { state.updateMarkerZ(it.toString(), 1f) }
        val id = selectedId.value
        lastSelected = id
        state.setClustererExemptList(CLUSTERER, setOfNotNull(id?.toString()))
        id ?: return@LaunchedEffect
        state.updateMarkerZ(id.toString(), 2f)
        val (_, xy) = placed.firstOrNull { it.first.id == id } ?: return@LaunchedEffect
        state.scrollTo(
            xy.first, xy.second,
            destScale = state.scale,
            animationSpec = tween(350),
            screenOffset = Offset(-0.5f, -fraction / 2),
        )
        userMoved = false
    }

    // "Search this area": only once the map is at rest, and only if it really moved.
    var offerSearchHere by remember { mutableStateOf(false) }
    LaunchedEffect(userMoved, area) {
        offerSearchHere = false
        if (!userMoved) return@LaunchedEffect
        state.idleStateFlow().first { it }
        val box = state.visibleBoundingBox()
        val (cx, cy) = stripCenter(box, fraction)
        val searched = area.latitude?.let { GeoPoint(it, area.longitude!!).toNormalized() }
        offerSearchHere = searched == null ||
            kotlin.math.abs(searched.first - cx) > (box.xRight - box.xLeft) * MOVED_THRESHOLD ||
            kotlin.math.abs(searched.second - cy) > (box.yBottom - box.yTop) * fraction * MOVED_THRESHOLD
    }

    BoxWithConstraints(modifier.background(mapBackground(dark))) {
        val stripHeight = maxHeight * visibleFraction
        MapUI(modifier = Modifier.fillMaxSize(), state = state)
        // A soft shade under the toolbar, so the map reads as sitting beneath it.
        Box(
            Modifier
                .fillMaxWidth()
                .height(14.dp)
                .background(Brush.verticalGradient(listOf(Color.Black.copy(alpha = if (dark) 0.35f else 0.10f), Color.Transparent)))
        )

        // Top of the map: "search this area" or the pick-mode hint.
        Box(Modifier.fillMaxWidth().padding(top = 12.dp), contentAlignment = Alignment.TopCenter) {
            AnimatedVisibility(
                visible = offerSearchHere && !pickMode,
                enter = fadeIn() + slideInVertically { -it },
                exit = fadeOut() + slideOutVertically { -it },
            ) {
                MapPill(
                    text = "جستجو در این محدوده",
                    icon = Lucide.RefreshCw,
                    onClick = {
                        scope.launch {
                            offerSearchHere = false
                            searchArea(visibleArea(state, fraction))
                        }
                    }
                )
            }
            AnimatedVisibility(visible = pickMode, enter = fadeIn(), exit = fadeOut()) {
                MapPill(text = "نقشه را جابه‌جا کنید", icon = null, onClick = null)
            }
        }

        // Pick mode: a pin fixed to the centre of the uncovered strip; it lifts
        // while the map moves under it and settles when the map comes to rest.
        if (pickMode) {
            val idle by state.idleStateFlow().collectAsState()
            CenterPin(
                lifted = !idle,
                modifier = Modifier.align(Alignment.TopCenter).offset(y = stripHeight / 2 - CenterPinTip)
            )
        }

        // Bottom of the uncovered strip: credit, the control stack, the pick action.
        Box(Modifier.fillMaxSize().padding(bottom = bottomInset + 16.dp)) {
            // Lifted above the pick button while it's there, so the credit stays readable.
            val creditLift by animateDpAsState(if (pickMode) 60.dp else 0.dp)
            MapAttribution(Modifier.align(Alignment.BottomStart).padding(start = 12.dp, bottom = creditLift))
            AnimatedVisibility(
                visible = pickMode,
                enter = fadeIn() + slideInVertically { it / 2 },
                exit = fadeOut() + slideOutVertically { it / 2 },
                modifier = Modifier.align(Alignment.BottomCenter)
            ) {
                Button(
                    onClick = { scope.launch { searchArea(visibleArea(state, fraction)) } },
                    shape = CircleShape,
                    contentPadding = PaddingValues(horizontal = 22.dp),
                    elevation = ButtonDefaults.buttonElevation(defaultElevation = 6.dp),
                    modifier = Modifier.height(48.dp)
                ) {
                    Icon(Lucide.Search, contentDescription = null, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("جستجو در این نقطه", fontWeight = FontWeight.Bold)
                }
            }
            MapControls(
                pickMode = pickMode,
                locating = locating,
                onTogglePickMode = onTogglePickMode,
                onLocate = onLocate,
                modifier = Modifier.align(Alignment.BottomEnd).padding(end = 16.dp)
            )
        }
    }
}
