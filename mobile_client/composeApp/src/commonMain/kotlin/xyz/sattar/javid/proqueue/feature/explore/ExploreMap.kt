package xyz.sattar.javid.proqueue.feature.explore

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ContentCut
import androidx.compose.material.icons.rounded.EditLocationAlt
import androidx.compose.material.icons.rounded.MedicalServices
import androidx.compose.material.icons.rounded.MonitorHeart
import androidx.compose.material.icons.rounded.MyLocation
import androidx.compose.material.icons.rounded.School
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Storefront
import androidx.compose.material.icons.rounded.Work
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
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
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import ovh.plrapps.mapcompose.api.BoundingBox
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
import ovh.plrapps.mapcompose.ui.state.MapState
import xyz.sattar.javid.proqueue.core.ui.components.map.DisposeMapState
import xyz.sattar.javid.proqueue.core.ui.components.map.GeoPoint
import xyz.sattar.javid.proqueue.core.ui.components.map.MapAttribution
import xyz.sattar.javid.proqueue.core.ui.components.map.MapTiles
import xyz.sattar.javid.proqueue.core.ui.components.map.applyTileTint
import xyz.sattar.javid.proqueue.core.ui.components.map.distanceKmTo
import xyz.sattar.javid.proqueue.core.ui.components.map.geoPointFromNormalized
import xyz.sattar.javid.proqueue.core.ui.components.map.mapBackground
import xyz.sattar.javid.proqueue.core.ui.components.map.newTileMapState
import xyz.sattar.javid.proqueue.core.ui.components.map.toNormalized
import xyz.sattar.javid.proqueue.domain.model.business.Business
import xyz.sattar.javid.proqueue.domain.model.business.BusinessCategory
import xyz.sattar.javid.proqueue.domain.model.business.BusinessCategoryGroup
import xyz.sattar.javid.proqueue.feature.discovery.ToothFilled
import xyz.sattar.javid.proqueue.feature.discovery.canBookOnline
import xyz.sattar.javid.proqueue.feature.discovery.toPersianDigits
import xyz.sattar.javid.proqueue.ui.theme.LocalIsDarkTheme
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

private const val ME_MARKER = "me"
private const val CLUSTERER = "results"
private const val RADIUS_PATH = "radius"

/** Smallest box the camera fits to, in normalized units (≈ 2 km): one pin
 *  alone shouldn't zoom the map down to the building. */
private const val MIN_SPAN = 0.00005

/** Zooming into a cluster may go this close (≈ 100 m) — enough to pull pins a few doors apart. */
private const val CLUSTER_MIN_SPAN = 0.0000025

/** A search centre counts as "moved away from" past this share of the visible width. */
private const val MOVED_THRESHOLD = 0.2

// ── Category look ────────────────────────────────────────────────────────────
//
// One hue per group so a glance at the map tells clinics from salons. Mid-tone
// colours that hold white icons and read on both the light and the dark map.

private data class PinStyle(val color: Color, val icon: ImageVector)

private fun Business.pinStyle(): PinStyle = when (BusinessCategory.fromString(category).group) {
    BusinessCategoryGroup.MEDICAL -> PinStyle(Color(0xFF0D9488), Icons.Rounded.MedicalServices)
    BusinessCategoryGroup.DENTAL -> PinStyle(Color(0xFF2563EB), ToothFilled)
    BusinessCategoryGroup.HEALTH -> PinStyle(Color(0xFF16A34A), Icons.Rounded.MonitorHeart)
    BusinessCategoryGroup.BEAUTY -> PinStyle(Color(0xFFDB2777), Icons.Rounded.ContentCut)
    BusinessCategoryGroup.PROFESSIONAL -> PinStyle(Color(0xFFD97706), Icons.Rounded.Work)
    BusinessCategoryGroup.EDUCATION -> PinStyle(Color(0xFF7C3AED), Icons.Rounded.School)
    BusinessCategoryGroup.TRADES, BusinessCategoryGroup.OTHER -> PinStyle(Color(0xFF475569), Icons.Rounded.Storefront)
}

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
    val me = (area as? SearchArea.NearMe)?.let { GeoPoint(it.latitude, it.longitude).toNormalized() }
    val picked = (area as? SearchArea.Picked)?.let { GeoPoint(it.latitude, it.longitude).toNormalized() }

    LaunchedEffect(placed, me, picked) {
        state.removeAllMarkers()
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
        // A picked point already has the camera where the user wants it.
        if (picked == null) {
            fitCamera(state, placed.map { it.second } + listOfNotNull(me), fraction)
        }
        userMoved = false
    }

    // The search area as a soft ring: what "۱۰ کیلومتر" means, drawn on the map.
    val ringColor = MaterialTheme.colorScheme.primary
    val center = area.latitude?.let { GeoPoint(it, area.longitude!!) }
    LaunchedEffect(center, radiusKm, ringColor) {
        state.removePath(RADIUS_PATH)
        center ?: return@LaunchedEffect
        state.addPath(
            RADIUS_PATH,
            // Outline only: at street zoom the ring is larger than the screen,
            // and a fill would just wash the whole map in the accent colour.
            width = 2.dp,
            color = ringColor.copy(alpha = 0.55f),
        ) {
            addPoints(circlePoints(center, radiusKm))
        }
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
                    icon = Icons.Outlined.Refresh,
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
                    Icon(Icons.Rounded.Search, contentDescription = null, modifier = Modifier.size(20.dp))
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

// ── Camera maths ─────────────────────────────────────────────────────────────

/** A circle of [radiusKm] around [center] as normalized map points (a 72-gon is smooth at any zoom we show). */
private fun circlePoints(center: GeoPoint, radiusKm: Double): List<Pair<Double, Double>> {
    val dLat = radiusKm / 111.32
    val dLng = radiusKm / (111.32 * cos(center.latitude * PI / 180.0))
    return (0..72).map { i ->
        val a = 2 * PI * i / 72
        GeoPoint(center.latitude + dLat * sin(a), center.longitude + dLng * cos(a)).toNormalized()
    }
}

/** Centre of the strip above the sheet, in normalized coordinates. */
private fun stripCenter(box: BoundingBox, fraction: Float): Pair<Double, Double> =
    (box.xLeft + box.xRight) / 2 to box.yTop + (box.yBottom - box.yTop) * fraction / 2

/** Radius options the API takes; the smallest one that covers [km]. */
private fun radiusCovering(km: Double): Double =
    radiusOptionsKm.firstOrNull { it >= km } ?: radiusOptionsKm.last()

/** What the user is looking at: the uncovered strip's centre and its half-width. */
private suspend fun visibleArea(state: MapState, fraction: Float): MapArea {
    val box = state.visibleBoundingBox()
    val (cx, cy) = stripCenter(box, fraction)
    return MapArea(geoPointFromNormalized(cx, cy), radiusCovering(halfWidthKm(box, cy)))
}

/** A long-pressed point, with the radius of the current view. */
private suspend fun areaAround(state: MapState, point: GeoPoint, fraction: Float): MapArea {
    val box = state.visibleBoundingBox()
    val (_, cy) = stripCenter(box, fraction)
    return MapArea(point, radiusCovering(halfWidthKm(box, cy)))
}

private fun halfWidthKm(box: BoundingBox, y: Double): Double =
    geoPointFromNormalized(box.xLeft, y).distanceKmTo(geoPointFromNormalized(box.xRight, y)) / 2

/**
 * Moves the camera so [points] sit in the top [visibleFraction] of the map.
 * The box is stretched downwards by the share of the screen the sheet hides,
 * so "fit" means "fit above the sheet".
 */
private suspend fun fitCamera(
    state: MapState,
    points: List<Pair<Double, Double>>,
    visibleFraction: Float,
    minSpan: Double = MIN_SPAN,
) {
    if (points.isEmpty()) return
    val left = points.minOf { it.first }
    val right = points.maxOf { it.first }
    val top = points.minOf { it.second }
    val bottom = points.maxOf { it.second }
    val w = maxOf(right - left, minSpan)
    val h = maxOf(bottom - top, minSpan)
    val cx = (left + right) / 2
    val cy = (top + bottom) / 2
    // 15% breathing room on the sides; extra on top for the pins' heads and
    // at the bottom for the location button row.
    val padW = w * 1.3
    val yTop = cy - h * 0.5 - h * 0.35
    val yBottomVisible = cy + h * 0.5 + h * 0.3
    val total = (yBottomVisible - yTop) / visibleFraction.coerceIn(0.2f, 1f)
    val box = BoundingBox(
        xLeft = cx - padW / 2,
        yTop = yTop,
        xRight = cx + padW / 2,
        yBottom = yTop + total,
    )
    state.scrollTo(box, animationSpec = tween(450))
}

// ── Pins & overlays ──────────────────────────────────────────────────────────

/**
 * A rounded body with a pointer at the bottom centre, drawn as one contour so
 * the shadow and the white ring follow the whole outline without a seam.
 * With a square body and [cornerRadius] at half its side it's the round pin;
 * with a wide body it's the speech-bubble label of the selected pin.
 */
private class BubbleShape(private val cornerRadius: Dp, private val tail: Dp) : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline {
        val t = with(density) { tail.toPx() }
        val h = size.height - t
        val r = minOf(with(density) { cornerRadius.toPx() }, h / 2, size.width / 2)
        val cx = size.width / 2
        val path = Path().apply {
            moveTo(r, 0f)
            lineTo(size.width - r, 0f)
            arcTo(Rect(size.width - 2 * r, 0f, size.width, 2 * r), -90f, 90f, false)
            lineTo(size.width, h - r)
            arcTo(Rect(size.width - 2 * r, h - 2 * r, size.width, h), 0f, 90f, false)
            lineTo(cx + t * 0.85f, h)
            quadraticTo(cx + t * 0.25f, h, cx, size.height)
            quadraticTo(cx - t * 0.25f, h, cx - t * 0.85f, h)
            lineTo(r, h)
            arcTo(Rect(0f, h - 2 * r, 2 * r, h), 90f, 90f, false)
            lineTo(0f, r)
            arcTo(Rect(0f, 0f, 2 * r, 2 * r), 180f, 90f, false)
            close()
        }
        return Outline.Generic(path)
    }
}

private val PinTail = 6.dp

/**
 * Three looks, one shape family:
 * - bookable: a solid category-coloured pin with a white glyph — the product;
 * - directory-only: smaller, white, glyph in the category colour — present but quiet;
 * - selected: grows into a bubble carrying the name, on top of its neighbours.
 */
@Composable
private fun BusinessPin(business: Business, selected: Boolean) {
    val style = business.pinStyle()
    val bookable = business.canBookOnline
    val ring = MaterialTheme.colorScheme.surface
    val pop by animateFloatAsState(
        if (selected) 1f else 0f,
        spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMediumLow)
    )

    if (selected) {
        val shape = BubbleShape(cornerRadius = 22.dp, tail = PinTail)
        Row(
            Modifier
                .scale(0.85f + 0.15f * pop)
                .shadow(10.dp, shape, ambientColor = style.color, spotColor = style.color)
                .background(ring, shape)
                .padding(start = 4.dp, end = 14.dp, top = 4.dp, bottom = 4.dp + PinTail),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(Modifier.size(32.dp).background(style.color, CircleShape), contentAlignment = Alignment.Center) {
                Icon(style.icon, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
            }
            Spacer(Modifier.width(8.dp))
            Text(
                business.title,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.widthIn(max = 168.dp)
            )
            if (bookable) {
                Spacer(Modifier.width(4.dp))
                Icon(Icons.Rounded.Bolt, contentDescription = "نوبت آنلاین", tint = style.color, modifier = Modifier.size(16.dp))
            }
        }
        return
    }

    val body = if (bookable) 34.dp else 26.dp
    val shape = BubbleShape(cornerRadius = body / 2, tail = PinTail)
    Box(
        Modifier
            .size(width = body, height = body + PinTail)
            .shadow(if (bookable) 5.dp else 2.dp, shape, ambientColor = style.color, spotColor = style.color)
            .background(if (bookable) style.color else ring, shape)
            .border(if (bookable) 2.dp else 1.5.dp, if (bookable) ring else style.color.copy(alpha = 0.7f), shape),
        contentAlignment = Alignment.TopCenter
    ) {
        Icon(
            style.icon,
            contentDescription = business.title,
            tint = if (bookable) Color.White else style.color,
            modifier = Modifier.padding(top = body * 0.22f).size(body * 0.56f)
        )
    }
}

/** Several pins too close to tell apart: a count in the brand colour, sized by how many. */
@Composable
private fun ClusterBubble(count: Int) {
    val primary = MaterialTheme.colorScheme.primary
    val size = when {
        count < 10 -> 40.dp
        count < 50 -> 46.dp
        else -> 52.dp
    }
    Box(
        Modifier.size(size + 12.dp).background(primary.copy(alpha = 0.16f), CircleShape),
        contentAlignment = Alignment.Center
    ) {
        Box(
            Modifier
                .size(size)
                .shadow(6.dp, CircleShape, ambientColor = primary, spotColor = primary)
                .background(primary, CircleShape)
                .border(2.5.dp, Color.White, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Text(
                count.toPersianDigits(),
                color = Color.White,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
private fun MyLocationDot() {
    val accent = Color(0xFF2F80ED)
    Box(Modifier.size(30.dp).background(accent.copy(alpha = 0.18f), CircleShape), contentAlignment = Alignment.Center) {
        Box(
            Modifier
                .size(16.dp)
                .shadow(3.dp, CircleShape)
                .background(Color.White, CircleShape)
                .padding(3.dp)
                .background(accent, CircleShape)
        )
    }
}

/** Marks the centre of a "search this area" / picked search. */
@Composable
private fun PickedAreaDot() {
    val primary = MaterialTheme.colorScheme.primary
    Box(Modifier.size(22.dp).background(primary.copy(alpha = 0.2f), CircleShape), contentAlignment = Alignment.Center) {
        Box(Modifier.size(10.dp).border(2.dp, Color.White, CircleShape).background(primary, CircleShape))
    }
}

/** Distance from the top of [CenterPin] to its tip, when resting. */
private val CenterPinTip = 54.dp

/** The aiming pin of pick mode. Lifts off its shadow while the map moves. */
@Composable
private fun CenterPin(lifted: Boolean, modifier: Modifier = Modifier) {
    val primary = MaterialTheme.colorScheme.primary
    val lift by animateDpAsState(if (lifted) 12.dp else 0.dp, spring(stiffness = Spring.StiffnessMediumLow))
    val shape = BubbleShape(cornerRadius = 22.dp, tail = 13.dp)
    Box(modifier.size(width = 44.dp, height = CenterPinTip + 6.dp)) {
        // The shadow stays on the ground and shrinks as the pin rises.
        Box(
            Modifier
                .align(Alignment.BottomCenter)
                .size(width = 14.dp - lift / 3, height = 5.dp)
                .background(Color.Black.copy(alpha = 0.28f - lift.value / 100f), CircleShape)
        )
        Box(
            Modifier
                .align(Alignment.TopCenter)
                .offset(y = -lift)
                .size(width = 44.dp, height = CenterPinTip)
                .shadow(8.dp, shape)
                .background(primary, shape)
                .border(2.dp, Color.White, shape),
            contentAlignment = Alignment.TopCenter
        ) {
            Box(Modifier.padding(top = 15.dp).size(14.dp).background(Color.White, CircleShape))
        }
    }
}

@Composable
private fun MapPill(text: String, icon: ImageVector?, onClick: (() -> Unit)?) {
    val content: @Composable () -> Unit = {
        Row(Modifier.padding(horizontal = 16.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            if (icon != null) {
                Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
            }
            Text(text, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
        }
    }
    if (onClick != null) {
        Surface(onClick = onClick, shape = CircleShape, color = MaterialTheme.colorScheme.surface, shadowElevation = 6.dp) { content() }
    } else {
        Surface(shape = CircleShape, color = MaterialTheme.colorScheme.surface, shadowElevation = 6.dp) { content() }
    }
}

/**
 * The map's own controls, grouped in one card the way Google and Apple Maps
 * do: "choose on map" on top, "my location" under it. The active pick mode
 * fills its button with the accent so the mode is never ambiguous.
 */
@Composable
private fun MapControls(
    pickMode: Boolean,
    locating: Boolean,
    onTogglePickMode: () -> Unit,
    onLocate: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surface,
        shadowElevation = 6.dp,
        modifier = modifier.width(48.dp)
    ) {
        Column {
            val activeBg by animateColorAsState(if (pickMode) MaterialTheme.colorScheme.primary else Color.Transparent)
            val activeFg by animateColorAsState(
                if (pickMode) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface
            )
            ControlButton(onClick = onTogglePickMode, background = activeBg) {
                Icon(
                    if (pickMode) Icons.Rounded.Close else Icons.Rounded.EditLocationAlt,
                    contentDescription = if (pickMode) "لغو انتخاب روی نقشه" else "انتخاب محل روی نقشه",
                    tint = activeFg,
                    modifier = Modifier.size(22.dp)
                )
            }
            Box(Modifier.fillMaxWidth().padding(horizontal = 10.dp).height(1.dp).background(MaterialTheme.colorScheme.outlineVariant))
            ControlButton(onClick = onLocate) {
                if (locating) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                else Icon(Icons.Rounded.MyLocation, contentDescription = "موقعیت من", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(22.dp))
            }
        }
    }
}

@Composable
private fun ControlButton(onClick: () -> Unit, background: Color = Color.Transparent, content: @Composable () -> Unit) {
    Surface(onClick = onClick, color = background, modifier = Modifier.fillMaxWidth().height(48.dp)) {
        Box(contentAlignment = Alignment.Center) { content() }
    }
}
