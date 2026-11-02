package xyz.sattar.javid.proqueue.feature.businessDetail

import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.LocateFixed
import com.composables.icons.lucide.Navigation
import androidx.compose.animation.AnimatedContent
import androidx.compose.ui.layout.layout
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull
import xyz.sattar.javid.proqueue.core.ui.components.map.loadedTileCount
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import xyz.sattar.javid.proqueue.core.location.driveEstimate
import xyz.sattar.javid.proqueue.core.location.label
import xyz.sattar.javid.proqueue.core.ui.components.map.distanceKmTo
import kotlin.math.PI
import kotlin.math.atan2
import ovh.plrapps.mapcompose.ui.state.MapState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import org.koin.compose.viewmodel.koinViewModel
import ovh.plrapps.mapcompose.api.BoundingBox
import ovh.plrapps.mapcompose.api.addMarker
import ovh.plrapps.mapcompose.api.addPath
import ovh.plrapps.mapcompose.api.removeMarker
import ovh.plrapps.mapcompose.api.removePath
import ovh.plrapps.mapcompose.api.scale
import ovh.plrapps.mapcompose.api.scrollTo
import ovh.plrapps.mapcompose.ui.MapUI
import xyz.sattar.javid.proqueue.core.location.LocationResult
import xyz.sattar.javid.proqueue.data.remoteDataSource.promo.PromoPlacement
import xyz.sattar.javid.proqueue.feature.promo.PromoStrip
import xyz.sattar.javid.proqueue.core.location.rememberLocationRequester
import xyz.sattar.javid.proqueue.core.ui.components.map.DisposeMapState
import xyz.sattar.javid.proqueue.core.ui.components.map.GeoPoint
import xyz.sattar.javid.proqueue.core.ui.components.map.MapAttribution
import xyz.sattar.javid.proqueue.core.ui.components.map.applyTileTint
import xyz.sattar.javid.proqueue.core.ui.components.map.mapBackground
import xyz.sattar.javid.proqueue.core.ui.components.map.newTileMapState
import xyz.sattar.javid.proqueue.core.ui.components.map.scaleForZoom
import xyz.sattar.javid.proqueue.core.ui.components.map.toNormalized
import xyz.sattar.javid.proqueue.core.location.rememberDirections
import xyz.sattar.javid.proqueue.core.utils.rememberReducedMotion
import xyz.sattar.javid.proqueue.domain.model.business.Business
import xyz.sattar.javid.proqueue.feature.booking.LocalReducedMotion
import xyz.sattar.javid.proqueue.feature.discovery.BackIcon
import xyz.sattar.javid.proqueue.feature.discovery.BusinessAvatar
import xyz.sattar.javid.proqueue.feature.discovery.Radius
import xyz.sattar.javid.proqueue.feature.discovery.Space
import xyz.sattar.javid.proqueue.feature.discovery.formatDistance
import xyz.sattar.javid.proqueue.feature.explore.BusinessPin
import xyz.sattar.javid.proqueue.feature.explore.MyLocationDot
import xyz.sattar.javid.proqueue.ui.theme.LocalIsDarkTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.graphics.vector.ImageVector
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import xyz.sattar.javid.proqueue.feature.discovery.categoryIcon
import xyz.sattar.javid.proqueue.feature.explore.ControlButton

private const val BUSINESS_MARKER = "business"
private const val ME_MARKER = "me"
private const val ROUTE_PATH = "route"
private const val ROUTE_HEAD = "route-head"
private const val DESTINATION_MARKER = "destination"

/** Straight segments the curve is drawn with; enough to look smooth at any zoom here. */
private const val ARC_SEGMENTS = 40
private const val ROUTE_ARROW = "route-arrow"


/** Where along the finished route its direction chevron sits. */
private const val MID_ARROW_AT = 0.55f

/** Zoom the map settles at once the route has arrived: street names and districts read. */
private const val ARRIVAL_ZOOM = 14.4

/** Where the destination sits from the top of the screen after arriving, leaving room for its card above and the sheet below. */
private const val ARRIVAL_PIN_AT = 0.46f

/** Share of the screen the bottom card leaves for the map. */
private const val MAP_VISIBLE_FRACTION = 0.62f

/**
 * The business on a full-screen map with the user's position beside it and
 * the straight-line distance between them. Asks for a fix on open and saves
 * it (see [BusinessMapViewModel]).
 */
@Composable
fun BusinessMapScreen(
    businessId: Long,
    onNavigateBack: () -> Unit,
    onOpenBusiness: (Long) -> Unit = {},
    viewModel: BusinessMapViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsState()
    val locator = rememberLocationRequester(viewModel::onLocationResult)
    val locate = {
        viewModel.onLocating()
        locator.request()
    }
    LaunchedEffect(businessId) {
        viewModel.load(businessId)
        locate()
    }

    CompositionLocalProvider(LocalReducedMotion provides rememberReducedMotion()) {
        Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
            val business = state.business
            val lat = business?.latitude
            val lng = business?.longitude
            if (business != null && lat != null && lng != null) {
                FullMap(business, GeoPoint(lat, lng), state.me, locating = state.locating, onLocate = locate)
            } else if (!state.isLoading) {
                Text(
                    state.errorMessage ?: "موقعیت مرکز ثبت نشده",
                    modifier = Modifier.align(Alignment.Center).padding(Space.xl),
                )
            }

            IconButton(
                onClick = onNavigateBack,
                modifier = Modifier
                    .statusBarsPadding()
                    .padding(Space.md)
                    .shadow(4.dp, CircleShape)
                    .background(MaterialTheme.colorScheme.surface, CircleShape)
                    .size(44.dp),
            ) { Icon(BackIcon, contentDescription = "بازگشت") }

            if (business != null) {
                BottomCard(
                    business = business,
                    state = state,
                    onLocate = locate,
                    onSettings = locator::openSettings,
                    onOpenBusiness = onOpenBusiness,
                    modifier = Modifier.align(Alignment.BottomCenter),
                )
            }
        }
    }
}

@Composable
private fun FullMap(business: Business, place: GeoPoint, me: GeoPoint?, locating: Boolean, onLocate: () -> Unit) {
    val density = LocalDensity.current.density
    val dark = LocalIsDarkTheme.current
    val reduced = LocalReducedMotion.current
    val accent = MaterialTheme.colorScheme.primary
    // Grey where the trip starts, dark through the middle, violet at the end.
    val routeColors = Triple(
        MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.55f),
        MaterialTheme.colorScheme.onSurface,
        accent,
    )
    val state = remember(place) { newTileMapState(place, 16.0, density) }
    DisposeMapState(state)
    val scope = rememberCoroutineScope()
    var flight by remember { mutableStateOf<Job?>(null) }
    // Origin or destination: pull back to show the whole trip, then dive in on
    // the chosen end with the same easing as the arrival zoom. A new tap
    // replaces a flight still in the air.
    val flyTo: (GeoPoint) -> Unit = { end ->
        flight?.cancel()
        flight = scope.launch {
            val target = end.toNormalized()
            val dest = place.toNormalized()
            val mine = me?.toNormalized()
            if (!reduced) {
                if (mine != null) fitBoth(state, mine, dest)
                else state.scrollTo(target.first, target.second, destScale = scaleForZoom(OVERVIEW_ZOOM, density), screenOffset = Offset(-0.5f, -MAP_VISIBLE_FRACTION / 2), animationSpec = tween(500))
                delay(150)
            }
            state.scrollTo(
                target.first,
                target.second,
                destScale = scaleForZoom(ARRIVAL_ZOOM, density),
                screenOffset = Offset(-0.5f, -ARRIVAL_PIN_AT),
                animationSpec = if (reduced) snap() else tween(1_100, easing = FastOutSlowInEasing),
            )
        }
    }
    LaunchedEffect(dark) { state.applyTileTint(dark) }

    // The map fades in once tiles arrive; only then does the route draw.
    val shown = remember { Animatable(if (reduced) 1f else 0f) }
    var played by remember { mutableStateOf(false) }
    var arrivedKm by remember { mutableStateOf<Double?>(null) }


    LaunchedEffect(state) {
        val (x, y) = place.toNormalized()
        // On the map itself, so the pin's tip is exactly on its point. The
        // arrival card rides above it.
        state.addMarker(BUSINESS_MARKER, x, y, relativeOffset = Offset(-0.5f, -1f), clickable = false, zIndex = 3f) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                arrivedKm?.let { km ->
                    ArrivalLabel(km)
                    Spacer(Modifier.height(Space.sm))
                }
                // The compact pin: the name is in the bottom card, and a wide
                // labelled bubble would cover the route at this zoom.
                Box(
                    Modifier.graphicsLayer {
                        scaleX = 1.3f
                        scaleY = 1.3f
                        transformOrigin = TransformOrigin(0.5f, 1f)
                    }
                ) { BusinessPin(business, selected = false) }
            }
        }
        // The destination on the ground, under the standing pin's tip.
        state.addMarker(DESTINATION_MARKER, x, y, relativeOffset = Offset(-0.5f, -0.5f), clickable = false, zIndex = 2f) {
            DestinationSquare()
        }
    }
    LaunchedEffect(state) {
        val start = loadedTileCount.value
        withTimeoutOrNull(4_000) { loadedTileCount.first { it >= start + 4 } }
        shown.animateTo(1f, tween(450))
    }
    LaunchedEffect(state, me) {
        state.removeMarker(ME_MARKER)
        state.clearRoute()
        arrivedKm = null
        val target = place.toNormalized()
        if (me == null) {
            // Above the bottom card, at street level.
            state.scrollTo(target.first, target.second, destScale = state.scale, screenOffset = Offset(-0.5f, -MAP_VISIBLE_FRACTION / 2))
            return@LaunchedEffect
        }
        val mine = me.toNormalized()
        state.addMarker(ME_MARKER, mine.first, mine.second, relativeOffset = Offset(-0.5f, -0.5f), clickable = false, zIndex = 0f) {
            MyLocationDot()
        }
        val before = loadedTileCount.value
        fitBoth(state, mine, target)
        val arc = arcPoints(mine, target)

        if (!reduced && !played) {
            played = true
            // The map first: wait for this view's tiles, and for the fade-in.
            withTimeoutOrNull(5_000) { loadedTileCount.first { it >= before + 6 } }
            shown.animateTo(1f, tween(300))
            delay(350)
            val flight = Animatable(0f)
            var drawn = 0
            flight.animateTo(1f, tween(2_000, easing = FastOutSlowInEasing)) {
                drawn = state.drawRoute(arc, value, routeColors, alreadyDrawn = drawn)
                // The arrow is the line's own tip, in the line's colour.
                state.drawChevron(arc, value, routeColor(routeColors, value))
            }
        }
        state.clearRoute()
        state.drawRoute(arc, 1f, routeColors)
        // Arrived: the square marks the end, a chevron mid-way shows the direction.
        state.drawChevron(arc, MID_ARROW_AT, routeColor(routeColors, MID_ARROW_AT))
        arrivedKm = me.distanceKmTo(place)
        // Then the camera goes in on the destination, at street level, with the
        // card above the pin and the pin above the bottom sheet.
        delay(if (reduced) 0 else 500)
        state.scrollTo(
            target.first,
            target.second,
            destScale = scaleForZoom(ARRIVAL_ZOOM, density),
            screenOffset = Offset(-0.5f, -ARRIVAL_PIN_AT),
            animationSpec = if (reduced) snap() else tween(1_100, easing = FastOutSlowInEasing),
        )
    }

    Box(Modifier.fillMaxSize().background(mapBackground(dark))) {
        Box(
            Modifier
                .fillMaxSize()
                .graphicsLayer {
                    alpha = shown.value
                    // A slight settle as it appears.
                    scaleX = 1f + 0.04f * (1f - shown.value)
                    scaleY = 1f + 0.04f * (1f - shown.value)
                }
        ) {
            MapUI(modifier = Modifier.fillMaxSize(), state = state)
        }

        // Origin and destination, above the bottom card on the side away from the back button.
        BoxWithConstraints(Modifier.fillMaxSize()) {
            TripControls(
                destinationIcon = business.categoryIcon(),
                locating = locating,
                onOrigin = { me?.let(flyTo) ?: onLocate() },
                onDestination = { flyTo(place) },
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(end = Space.page, bottom = maxHeight * (1f - MAP_VISIBLE_FRACTION) + Space.lg)
                    .graphicsLayer { alpha = shown.value },
            )
        }

        if (shown.value < 1f) {
            Text(
                "در حال بارگذاری نقشه…",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .align(Alignment.Center)
                    .graphicsLayer { alpha = 1f - shown.value }
                    .background(MaterialTheme.colorScheme.surface, CircleShape)
                    .padding(horizontal = Space.lg, vertical = Space.sm),
            )
        }
    }
}

private fun lerp(a: Pair<Double, Double>, b: Pair<Double, Double>, t: Double) =
    a.first + (b.first - a.first) * t to a.second + (b.second - a.second) * t

/**
 * The trip as a gentle arc rather than a ruler line: a quadratic curve whose
 * control point sits off the chord's middle by a fifth of its length, on the
 * side that bows up the screen.
 */
internal fun arcPoints(a: Pair<Double, Double>, b: Pair<Double, Double>, segments: Int = ARC_SEGMENTS): List<Pair<Double, Double>> {
    val dx = b.first - a.first
    val dy = b.second - a.second
    val len = kotlin.math.sqrt(dx * dx + dy * dy)
    if (len == 0.0) return listOf(a, b)
    // Unit normal; pick the one pointing up (map y grows downwards).
    var nx = -dy / len
    var ny = dx / len
    if (ny > 0) { nx = -nx; ny = -ny }
    val bow = len * 0.2
    val c = (a.first + b.first) / 2 + nx * bow to (a.second + b.second) / 2 + ny * bow
    return (0..segments).map { i ->
        val t = i.toDouble() / segments
        val u = 1 - t
        u * u * a.first + 2 * u * t * c.first + t * t * b.first to
            u * u * a.second + 2 * u * t * c.second + t * t * b.second
    }
}

/** The point [t] (0..1) of the way along [arc]. */
internal fun pointOnArc(arc: List<Pair<Double, Double>>, t: Float): Pair<Double, Double> {
    val pos = t.coerceIn(0f, 1f) * (arc.size - 1)
    val i = pos.toInt().coerceAtMost(arc.size - 2)
    return lerp(arc[i], arc[i + 1], (pos - i).toDouble())
}

/** Colour of the route [t] of the way along: start → middle → end. */
private fun routeColor(colors: Triple<Color, Color, Color>, t: Float): Color =
    if (t < 0.5f) androidx.compose.ui.graphics.lerp(colors.first, colors.second, t * 2)
    else androidx.compose.ui.graphics.lerp(colors.second, colors.third, (t - 0.5f) * 2)

/**
 * Draws [arc] up to [progress] as short segments in a colour ramp, adding
 * only the segments not drawn yet ([alreadyDrawn]) plus a moving head piece.
 * Returns how many whole segments are now drawn.
 */
private fun MapState.drawRoute(
    arc: List<Pair<Double, Double>>,
    progress: Float,
    colors: Triple<Color, Color, Color>,
    alreadyDrawn: Int = 0,
): Int {
    val segments = arc.size - 1
    val whole = (progress.coerceIn(0f, 1f) * segments).toInt().coerceAtMost(segments)
    for (i in alreadyDrawn until whole) {
        addPath("$ROUTE_PATH$i", width = 5.dp, color = routeColor(colors, (i + 0.5f) / segments)) {
            addPoints(listOf(arc[i], arc[i + 1]))
        }
    }
    removePath(ROUTE_HEAD)
    if (whole < segments) {
        val head = pointOnArc(arc, progress)
        addPath(ROUTE_HEAD, width = 5.dp, color = routeColor(colors, progress)) { addPoints(listOf(arc[whole], head)) }
    }
    return whole
}

private fun MapState.clearRoute() {
    for (i in 0 until ARC_SEGMENTS) removePath("$ROUTE_PATH$i")
    removePath(ROUTE_HEAD)
    removePath(ROUTE_ARROW)
}

/**
 * An open arrowhead drawn into the route at [t], pointing along it: two short
 * strokes meeting at the route, sized to the trip so it reads the same at the
 * zoom the trip is framed at.
 */
private fun MapState.drawChevron(arc: List<Pair<Double, Double>>, t: Float, color: Color) {
    removePath(ROUTE_ARROW)
    if (t < 0.06f) return
    val tip = pointOnArc(arc, t)
    val back = pointOnArc(arc, (t - 0.03f).coerceAtLeast(0f))
    var dx = tip.first - back.first
    var dy = tip.second - back.second
    val d = kotlin.math.sqrt(dx * dx + dy * dy)
    if (d == 0.0) return
    dx /= d
    dy /= d
    val chord = kotlin.math.sqrt(
        (arc.last().first - arc.first().first).let { it * it } + (arc.last().second - arc.first().second).let { it * it }
    )
    val length = chord * 0.028
    val half = chord * 0.018
    val baseX = tip.first - dx * length
    val baseY = tip.second - dy * length
    addPath(ROUTE_ARROW, width = 4.dp, color = color) {
        addPoints(listOf(baseX - dy * half to baseY + dx * half, tip, baseX + dy * half to baseY - dx * half))
    }
}

/** The destination as a small square on the ground, like a ride app's drop-off. */
@Composable
private fun DestinationSquare() {
    Box(
        Modifier
            .size(14.dp)
            .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(3.dp))
            .padding(3.dp)
            .background(MaterialTheme.colorScheme.primary, RoundedCornerShape(1.dp))
    )
}

/** Pops in over the destination once the arrow lands. */
@Composable
private fun ArrivalLabel(straightKm: Double) {
    val reduced = LocalReducedMotion.current
    val pop = remember { Animatable(if (reduced) 1f else 0f) }
    LaunchedEffect(Unit) { pop.animateTo(1f, spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMediumLow)) }
    val drive = remember(straightKm) { driveEstimate(straightKm) }
    Column(
        Modifier
            .graphicsLayer {
                scaleX = 0.6f + 0.4f * pop.value
                scaleY = 0.6f + 0.4f * pop.value
                alpha = pop.value.coerceIn(0f, 1f)
                transformOrigin = TransformOrigin(0.5f, 1f)
            }
            .shadow(10.dp, RoundedCornerShape(14.dp))
            .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(14.dp))
            .padding(horizontal = Space.md, vertical = Space.sm),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(formatDistance(straightKm), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
        Text(
            "مسیر حدودی ${formatDistance(drive.roadKm)}",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(drive.label(), style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Medium)
    }
}

/** The pull-back zoom when there is no user position to frame together with the business. */
private const val OVERVIEW_ZOOM = 12.0

/** The explore map's control card, here with the trip's two ends: where you are, where you're going. */
@Composable
private fun TripControls(
    destinationIcon: ImageVector,
    locating: Boolean,
    onOrigin: () -> Unit,
    onDestination: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surface,
        shadowElevation = 6.dp,
        modifier = modifier.width(48.dp),
    ) {
        Column {
            ControlButton(onClick = onOrigin) {
                if (locating) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                else Icon(Lucide.LocateFixed, contentDescription = "مبدا: موقعیت من", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(22.dp))
            }
            Box(Modifier.fillMaxWidth().padding(horizontal = 10.dp).height(1.dp).background(MaterialTheme.colorScheme.outlineVariant))
            ControlButton(onClick = onDestination) {
                Icon(destinationIcon, contentDescription = "مقصد: مرکز", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(22.dp))
            }
        }
    }
}

/** Frames the user and the business above the bottom card, as close as both fit. */
private suspend fun fitBoth(state: MapState, a: Pair<Double, Double>, b: Pair<Double, Double>) {
    val w = maxOf(kotlin.math.abs(a.first - b.first), 0.00002)
    val h = maxOf(kotlin.math.abs(a.second - b.second), 0.00002)
    val span = maxOf(w, h)
    val cx = (a.first + b.first) / 2
    val cy = (a.second + b.second) / 2
    // Street level: the trip fills most of the width, so street and area
    // names show (the arrival card keeps itself on screen).
    val halfW = span * 0.92
    // Room above for the bubble, below for the dot; then stretch down under the card.
    val top = cy - h / 2 - span * 0.55
    val visibleBottom = cy + h / 2 + span * 0.35
    val bottom = top + (visibleBottom - top) / MAP_VISIBLE_FRACTION
    state.scrollTo(BoundingBox(cx - halfW, top, cx + halfW, bottom), animationSpec = tween(500))
}

@Composable
private fun BottomCard(
    business: Business,
    state: BusinessMapState,
    onLocate: () -> Unit,
    onSettings: () -> Unit,
    onOpenBusiness: (Long) -> Unit,
    modifier: Modifier,
) {
    val directions = rememberDirections()
    Column(modifier.fillMaxWidth()) {
        MapAttribution(Modifier.padding(start = Space.md, bottom = Space.sm))
        // Tucked behind the card's rounded top, like a ride app's offer bar.
        PromoStrip(
            PromoPlacement.BUSINESS_MAP,
            onOpenBusiness = onOpenBusiness,
            shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
            overlap = 24.dp,
        )
        Surface(
            shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
            color = MaterialTheme.colorScheme.surface,
            shadowElevation = 12.dp,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(
                Modifier
                    .windowInsetsPadding(WindowInsets.navigationBars)
                    .padding(Space.page),
                verticalArrangement = Arrangement.spacedBy(Space.lg),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    BusinessAvatar(business, 48.dp)
                    Spacer(Modifier.width(Space.md))
                    Column(Modifier.weight(1f)) {
                        Text(business.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, maxLines = 1)
                        if (business.address.isNotBlank()) {
                            Text(business.address, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2)
                        }
                    }
                }
                DistanceBlock(state, onLocate, onSettings)
                Row(horizontalArrangement = Arrangement.spacedBy(Space.sm), verticalAlignment = Alignment.CenterVertically) {
                    val lat = business.latitude
                    val lng = business.longitude
                    Button(
                        onClick = { if (lat != null && lng != null) directions(lat, lng, business.title) },
                        shape = Radius.md,
                        modifier = Modifier.weight(1f).height(48.dp),
                    ) {
                        Icon(Lucide.Navigation, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(Space.sm))
                        Text("مسیریابی", fontWeight = FontWeight.Bold)
                    }
                    OutlinedButton(
                        onClick = onLocate,
                        enabled = !state.locating,
                        shape = Radius.md,
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp),
                        modifier = Modifier.size(48.dp),
                    ) {
                        if (state.locating) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                        else Icon(Lucide.LocateFixed, contentDescription = "موقعیت من")
                    }
                }
            }
        }
    }
}

private sealed interface DistanceView {
    data object Locating : DistanceView
    data class Known(val text: String, val drive: String, val fromSaved: Boolean) : DistanceView
    data class Problem(val result: LocationResult) : DistanceView
}

@Composable
private fun DistanceBlock(state: BusinessMapState, onLocate: () -> Unit, onSettings: () -> Unit) {
    val km = state.distanceKm()
    val view = when {
        km != null -> driveEstimate(km).let { d ->
            DistanceView.Known(formatDistance(km), "مسیر حدودی ${formatDistance(d.roadKm)} · ${d.label()}", fromSaved = !state.fresh)
        }
        state.locationProblem != null -> DistanceView.Problem(state.locationProblem)
        else -> DistanceView.Locating
    }
    AnimatedContent(
        targetState = view,
        transitionSpec = { (fadeIn(tween(220)) + slideInVertically { it / 4 }) togetherWith fadeOut(tween(120)) },
        label = "distance",
    ) { v ->
        Column(
            Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surfaceVariant, Radius.md)
                .padding(Space.md),
        ) {
            when (v) {
                DistanceView.Locating -> Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                    Spacer(Modifier.width(Space.sm))
                    Text("در حال پیدا کردن موقعیت شما…", style = MaterialTheme.typography.bodyMedium)
                }
                is DistanceView.Known -> {
                    Text("فاصله مستقیم تا مرکز", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(v.text, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                    Text(v.drive, style = MaterialTheme.typography.bodyMedium)
                    if (v.fromSaved) {
                        Text(
                            "بر اساس آخرین موقعیت ذخیره‌شده",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                is DistanceView.Problem -> {
                    Text(
                        when (v.result) {
                            LocationResult.PermissionDenied -> "برای فاصله، دسترسی به موقعیت را بدهید"
                            LocationResult.Disabled -> "موقعیت‌یاب گوشی خاموش است"
                            else -> "موقعیت شما پیدا نشد"
                        },
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    TextButton(
                        onClick = if (v.result == LocationResult.Unavailable) onLocate else onSettings,
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp),
                    ) {
                        Text(if (v.result == LocationResult.Unavailable) "تلاش دوباره" else "رفتن به تنظیمات")
                    }
                }
            }
        }
    }
}
