package xyz.sattar.javid.proqueue.feature.explore

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.shape.GenericShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.BusinessCenter
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.ContentCut
import androidx.compose.material.icons.outlined.FitnessCenter
import androidx.compose.material.icons.outlined.MedicalServices
import androidx.compose.material.icons.outlined.MyLocation
import androidx.compose.material.icons.outlined.Psychology
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Storefront
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import ovh.plrapps.mapcompose.api.BoundingBox
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
import xyz.sattar.javid.proqueue.feature.discovery.ToothOutlined
import xyz.sattar.javid.proqueue.feature.discovery.canBookOnline
import xyz.sattar.javid.proqueue.ui.theme.LocalIsDarkTheme

private const val ME_MARKER = "me"

/** Smallest box the camera fits to, in normalized units (≈ 2 km): one pin
 *  alone shouldn't zoom the map down to the building. */
private const val MIN_SPAN = 0.00005

/** A search centre counts as "moved away from" past this share of the visible width. */
private const val MOVED_THRESHOLD = 0.2

// ── Category look ────────────────────────────────────────────────────────────
//
// One hue per group so a glance at the map tells clinics from salons. Mid-tone
// colours that hold white icons and read on both the light and the dark map.

private data class PinStyle(val color: Color, val icon: ImageVector)

private fun Business.pinStyle(): PinStyle = when (BusinessCategory.fromString(category).group) {
    BusinessCategoryGroup.MEDICAL -> PinStyle(Color(0xFF0E9F8E), Icons.Outlined.MedicalServices)
    BusinessCategoryGroup.DENTAL -> PinStyle(Color(0xFF2F80ED), ToothOutlined)
    BusinessCategoryGroup.HEALTH -> PinStyle(Color(0xFF3FA34D), Icons.Outlined.Psychology)
    BusinessCategoryGroup.BEAUTY -> PinStyle(Color(0xFFE0457B), Icons.Outlined.ContentCut)
    BusinessCategoryGroup.PROFESSIONAL -> PinStyle(Color(0xFFD9822B), Icons.Outlined.BusinessCenter)
    BusinessCategoryGroup.EDUCATION -> PinStyle(Color(0xFF8E5CD9), Icons.Outlined.FitnessCenter)
    BusinessCategoryGroup.TRADES, BusinessCategoryGroup.OTHER -> PinStyle(Color(0xFF5B6475), Icons.Outlined.Storefront)
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
@Composable
fun ExploreMap(
    businesses: List<Business>,
    area: SearchArea,
    selectedId: State<Long?>,
    onSelect: (Long?) -> Unit,
    onSearchArea: (MapArea) -> Unit,
    onLocate: () -> Unit,
    locating: Boolean,
    pickMode: Boolean,
    onExitPickMode: () -> Unit,
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

    // The selected pin draws over its neighbours and slides into view.
    var lastSelected by remember { mutableStateOf<Long?>(null) }
    LaunchedEffect(selectedId.value) {
        lastSelected?.let { state.updateMarkerZ(it.toString(), 1f) }
        val id = selectedId.value
        lastSelected = id
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
                MapPill(text = "نقشه را بکشید تا نشانگر روی محل موردنظر برود", icon = null, onClick = null)
            }
        }

        // Pick mode: a pin fixed to the centre of the uncovered strip.
        if (pickMode) {
            CenterPin(Modifier.align(Alignment.TopCenter).offset(y = stripHeight / 2 - 52.dp))
        }

        // Bottom of the uncovered strip: credit, location button, pick-mode actions.
        Box(Modifier.fillMaxSize().padding(bottom = bottomInset + 12.dp)) {
            MapAttribution(Modifier.align(Alignment.BottomStart).padding(start = 12.dp))
            if (pickMode) {
                Row(
                    Modifier.align(Alignment.BottomCenter).padding(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Button(
                        onClick = { scope.launch { searchArea(visibleArea(state, fraction)) } },
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.height(48.dp)
                    ) { Text("جستجو در این نقطه", fontWeight = FontWeight.Bold) }
                    RoundMapButton(icon = Icons.Outlined.Close, description = "لغو", onClick = onExitPickMode)
                }
            } else {
                RoundMapButton(
                    icon = Icons.Outlined.MyLocation,
                    description = "موقعیت من",
                    busy = locating,
                    tint = MaterialTheme.colorScheme.primary,
                    onClick = onLocate,
                    modifier = Modifier.align(Alignment.BottomEnd).padding(end = 16.dp)
                )
            }
        }
    }
}

// ── Camera maths ─────────────────────────────────────────────────────────────

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
) {
    if (points.isEmpty()) return
    val left = points.minOf { it.first }
    val right = points.maxOf { it.first }
    val top = points.minOf { it.second }
    val bottom = points.maxOf { it.second }
    val w = maxOf(right - left, MIN_SPAN)
    val h = maxOf(bottom - top, MIN_SPAN)
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

/** A circle with a pointed bottom — the classic map pin, drawn as one shape so the shadow follows it. */
private val PinShape = GenericShape { size, _ ->
    val r = size.width / 2
    addOval(androidx.compose.ui.geometry.Rect(0f, 0f, size.width, size.width))
    moveTo(r - r * 0.42f, size.width * 0.86f)
    lineTo(r, size.height)
    lineTo(r + r * 0.42f, size.width * 0.86f)
    close()
}

@Composable
private fun BusinessPin(business: Business, selected: Boolean) {
    val style = business.pinStyle()
    val bookable = business.canBookOnline
    val scale by animateFloatAsState(
        if (selected) 1.18f else 1f,
        spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium)
    )
    // Bookable listings are the product — they get the solid pin.
    val size = if (bookable || selected) 38.dp else 30.dp
    val surface = MaterialTheme.colorScheme.surface

    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        AnimatedVisibility(
            visible = selected,
            enter = fadeIn() + scaleIn(initialScale = 0.8f),
            exit = fadeOut() + scaleOut(targetScale = 0.8f),
        ) {
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = surface,
                shadowElevation = 4.dp,
                modifier = Modifier.padding(bottom = 6.dp)
            ) {
                Row(Modifier.padding(horizontal = 10.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        business.title,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.widthIn(max = 170.dp)
                    )
                    if (bookable) {
                        Spacer(Modifier.width(4.dp))
                        Icon(Icons.Rounded.Bolt, contentDescription = null, tint = style.color, modifier = Modifier.size(14.dp))
                    }
                }
            }
        }
        Box(
            Modifier
                .scale(scale)
                .size(width = size, height = size * 1.22f)
                .shadow(if (selected) 8.dp else 3.dp, PinShape)
                .background(if (bookable || selected) style.color else surface, PinShape)
                .border(2.dp, if (bookable || selected) surface else style.color, PinShape),
            contentAlignment = Alignment.TopCenter
        ) {
            Icon(
                style.icon,
                contentDescription = business.title,
                tint = if (bookable || selected) Color.White else style.color,
                modifier = Modifier.padding(top = size * 0.2f).size(size * 0.56f)
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

/** The fixed aiming pin of pick mode: its tip marks the centre of the uncovered strip. */
@Composable
private fun CenterPin(modifier: Modifier = Modifier) {
    val primary = MaterialTheme.colorScheme.primary
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            Modifier
                .size(width = 40.dp, height = 49.dp)
                .shadow(8.dp, PinShape)
                .background(primary, PinShape),
            contentAlignment = Alignment.TopCenter
        ) {
            Box(Modifier.padding(top = 13.dp).size(14.dp).background(Color.White, CircleShape))
        }
        Box(Modifier.padding(top = 2.dp).size(width = 10.dp, height = 4.dp).background(Color.Black.copy(alpha = 0.25f), CircleShape))
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

@Composable
private fun RoundMapButton(
    icon: ImageVector,
    description: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    busy: Boolean = false,
    tint: Color = MaterialTheme.colorScheme.onSurface,
) {
    Surface(
        onClick = onClick,
        shape = CircleShape,
        color = MaterialTheme.colorScheme.surface,
        shadowElevation = 6.dp,
        modifier = modifier.size(48.dp)
    ) {
        Box(contentAlignment = Alignment.Center) {
            if (busy) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
            else Icon(icon, contentDescription = description, tint = tint)
        }
    }
}
