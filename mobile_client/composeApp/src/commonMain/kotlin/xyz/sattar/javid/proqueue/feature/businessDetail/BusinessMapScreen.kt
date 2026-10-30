package xyz.sattar.javid.proqueue.feature.businessDetail

import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.ui.layout.layout
import androidx.compose.ui.unit.Dp
import org.maplibre.compose.camera.CameraPosition
import org.maplibre.compose.camera.CameraState
import org.maplibre.compose.camera.rememberCameraState
import org.maplibre.compose.expressions.dsl.const
import org.maplibre.compose.expressions.dsl.feature
import org.maplibre.compose.expressions.dsl.interpolate
import org.maplibre.compose.expressions.dsl.linear
import org.maplibre.compose.expressions.value.LineCap
import org.maplibre.compose.expressions.value.LineJoin
import org.maplibre.compose.layers.LineLayer
import org.maplibre.compose.sources.GeoJsonData
import org.maplibre.compose.sources.GeoJsonOptions
import org.maplibre.compose.sources.rememberGeoJsonSource
import org.maplibre.spatialk.geojson.BoundingBox
import org.maplibre.spatialk.geojson.LineString
import org.maplibre.spatialk.geojson.Position
import xyz.sattar.javid.proqueue.core.ui.components.map.VectorMap
import xyz.sattar.javid.proqueue.core.ui.components.map.geoPointFromNormalized
import kotlin.time.Duration.Companion.milliseconds
import androidx.compose.animation.AnimatedContent
import androidx.compose.ui.layout.layout
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Directions
import androidx.compose.material.icons.outlined.MyLocation
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
import xyz.sattar.javid.proqueue.core.location.LocationResult
import xyz.sattar.javid.proqueue.data.remoteDataSource.promo.PromoPlacement
import xyz.sattar.javid.proqueue.feature.promo.PromoStrip
import xyz.sattar.javid.proqueue.core.location.rememberLocationRequester
import xyz.sattar.javid.proqueue.core.ui.components.map.GeoPoint
import xyz.sattar.javid.proqueue.core.ui.components.map.MapAttribution
import xyz.sattar.javid.proqueue.core.ui.components.map.mapBackground
import xyz.sattar.javid.proqueue.core.ui.components.map.toNormalized
import xyz.sattar.javid.proqueue.core.utils.openMap
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

/** Straight segments the curve is drawn with; enough to look smooth at any zoom here. */
private const val ARC_SEGMENTS = 40

/** Where along the finished route its direction chevron sits. */
private const val MID_ARROW_AT = 0.55f

/** Share of the screen the bottom card leaves for the map. */
private const val MAP_VISIBLE_FRACTION = 0.62f

/** Street level, where street and area names show. */
private const val STREET_ZOOM = 15.5

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
                FullMap(business, GeoPoint(lat, lng), state.me)
            } else if (!state.isLoading) {
                Text(
                    state.errorMessage ?: "موقعیت این مرکز روی نقشه ثبت نشده است",
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
private fun FullMap(business: Business, place: GeoPoint, me: GeoPoint?) {
    val dark = LocalIsDarkTheme.current
    val reduced = LocalReducedMotion.current
    val accent = MaterialTheme.colorScheme.primary
    // Grey where the trip starts, dark through the middle, violet at the end.
    val routeColors = Triple(
        MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.55f),
        MaterialTheme.colorScheme.onSurface,
        accent,
    )
    val camera = rememberCameraState(
        CameraPosition(target = Position(place.longitude, place.latitude), zoom = STREET_ZOOM)
    )
    // The map fades in once its style is up; only then does the route draw.
    val shown = remember { Animatable(if (reduced) 1f else 0f) }
    var loaded by remember { mutableStateOf(false) }
    var progress by remember { mutableStateOf(0f) }
    var played by remember { mutableStateOf(false) }
    var arrivedKm by remember { mutableStateOf<Double?>(null) }
    val arc = remember(me, place) { me?.let { arcGeo(it, place) } }

    LaunchedEffect(loaded) { if (loaded) shown.animateTo(1f, tween(450)) }

    BoxWithConstraints(Modifier.fillMaxSize().background(mapBackground(dark))) {
        // Room under the bottom card, and above the business for its card.
        val framePadding = PaddingValues(
            start = 56.dp,
            end = 56.dp,
            top = 170.dp,
            bottom = maxHeight * (1f - MAP_VISIBLE_FRACTION) + 48.dp,
        )
        LaunchedEffect(arc, loaded) {
            if (!loaded) return@LaunchedEffect
            arrivedKm = null
            if (arc == null || me == null) {
                progress = 0f
                camera.animateTo(
                    CameraPosition(target = Position(place.longitude, place.latitude), zoom = STREET_ZOOM, padding = framePadding),
                    duration = 400.milliseconds,
                )
                return@LaunchedEffect
            }
            camera.animateTo(boundsOf(arc), padding = framePadding, duration = 600.milliseconds)
            if (!reduced && !played) {
                played = true
                delay(450)
                val flight = Animatable(0f)
                flight.animateTo(1f, tween(2_000, easing = FastOutSlowInEasing)) { progress = value }
            }
            progress = 1f
            arrivedKm = me.distanceKmTo(place)
        }

        VectorMap(
            cameraState = camera,
            dark = dark,
            modifier = Modifier.fillMaxSize().graphicsLayer { alpha = shown.value },
            onMapLoadFinished = { loaded = true },
        ) {
            if (arc != null && progress > 0f) {
                val drawn = partialArc(arc, progress)
                val route = rememberGeoJsonSource(
                    GeoJsonData.Features(LineString(drawn.map { Position(it.longitude, it.latitude) })),
                    GeoJsonOptions(lineMetrics = true),
                )
                LineLayer(
                    id = "route",
                    source = route,
                    width = const(5.dp),
                    cap = const(LineCap.Round),
                    join = const(LineJoin.Round),
                    gradient = interpolate(
                        linear(),
                        feature.lineProgress(),
                        0f to const(routeColors.first),
                        0.5f to const(routeColors.second),
                        1f to const(routeColors.third),
                    ),
                )
                // The arrow is the line's own tip, in the line's colour; once
                // landed, a chevron mid-way shows the direction instead.
                val at = if (progress < 1f) progress else MID_ARROW_AT
                chevronGeo(arc, at)?.let { v ->
                    val arrow = rememberGeoJsonSource(
                        GeoJsonData.Features(LineString(v.map { Position(it.longitude, it.latitude) }))
                    )
                    LineLayer(
                        id = "route-arrow",
                        source = arrow,
                        width = const(4.dp),
                        cap = const(LineCap.Round),
                        join = const(LineJoin.Round),
                        color = const(routeColor(routeColors, at)),
                    )
                }
            }
        }

        // Markers are Compose, pinned to their point through the camera's
        // projection, so they look exactly like the rest of the app.
        val pinAlpha = shown.value
        if (me != null) PinnedAt(camera, me, PinAnchor.Center, pinAlpha) { MyLocationDot() }
        PinnedAt(camera, place, PinAnchor.Center, pinAlpha) { DestinationSquare() }
        PinnedAt(camera, place, PinAnchor.Bottom, pinAlpha, lift = 6.dp) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                arrivedKm?.let { km ->
                    ArrivalLabel(km)
                    Spacer(Modifier.height(Space.sm))
                }
                // The compact pin: the name is in the bottom card.
                Box(
                    Modifier.graphicsLayer {
                        scaleX = 1.3f
                        scaleY = 1.3f
                        transformOrigin = TransformOrigin(0.5f, 1f)
                    }
                ) { BusinessPin(business, selected = false) }
            }
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

internal enum class PinAnchor { Center, Bottom }

/**
 * Places [content] on [point] of the map: centred on it, or standing on it
 * by its bottom edge, raised by [lift]. Re-placed whenever the camera moves.
 * Takes the full parent size, so its origin is the map's even in RTL.
 */
@Composable
internal fun PinnedAt(
    camera: CameraState,
    point: GeoPoint,
    anchor: PinAnchor,
    alpha: Float = 1f,
    lift: Dp = 0.dp,
    content: @Composable () -> Unit,
) {
    val position = remember(point) { Position(point.longitude, point.latitude) }
    Box(
        Modifier
            .graphicsLayer { this.alpha = alpha }
            .layout { measurable, constraints ->
                val placeable = measurable.measure(constraints.copy(minWidth = 0, minHeight = 0))
                layout(constraints.maxWidth, constraints.maxHeight) {
                    camera.position // re-place on every camera move
                    val at = camera.projection?.screenLocationFromPosition(position) ?: return@layout
                    val x = at.x.roundToPx() - placeable.width / 2
                    val y = when (anchor) {
                        PinAnchor.Center -> at.y.roundToPx() - placeable.height / 2
                        PinAnchor.Bottom -> at.y.roundToPx() - placeable.height + lift.roundToPx()
                    }
                    placeable.place(x, y)
                }
            }
    ) { content() }
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


/** The curved trip between two places, in geographic points. */
internal fun arcGeo(from: GeoPoint, to: GeoPoint): List<GeoPoint> =
    arcPoints(from.toNormalized(), to.toNormalized()).map { (x, y) -> geoPointFromNormalized(x, y) }

/** [arc] drawn up to [progress]: whole segments plus the moving head. */
internal fun partialArc(arc: List<GeoPoint>, progress: Float): List<GeoPoint> {
    val segments = arc.size - 1
    val pos = progress.coerceIn(0f, 1f) * segments
    val whole = pos.toInt().coerceAtMost(segments)
    if (whole >= segments) return arc
    val a = arc[whole]
    val b = arc[whole + 1]
    val t = (pos - whole).toDouble()
    val head = GeoPoint(a.latitude + (b.latitude - a.latitude) * t, a.longitude + (b.longitude - a.longitude) * t)
    return arc.subList(0, whole + 1) + head
}

/**
 * An open arrowhead on the route at [t], pointing along it: three points
 * (wing, tip, wing), sized to the trip so it reads the same at the zoom the
 * trip is framed at. Null at the very start.
 */
internal fun chevronGeo(arc: List<GeoPoint>, t: Float): List<GeoPoint>? {
    if (t < 0.06f) return null
    val flat = arc.map { it.toNormalized() }
    val tip = pointOnArc(flat, t)
    val back = pointOnArc(flat, (t - 0.03f).coerceAtLeast(0f))
    var dx = tip.first - back.first
    var dy = tip.second - back.second
    val d = kotlin.math.sqrt(dx * dx + dy * dy)
    if (d == 0.0) return null
    dx /= d
    dy /= d
    val first = flat.first()
    val last = flat.last()
    val chord = kotlin.math.sqrt((last.first - first.first).let { it * it } + (last.second - first.second).let { it * it })
    val length = chord * 0.028
    val half = chord * 0.018
    val baseX = tip.first - dx * length
    val baseY = tip.second - dy * length
    return listOf(
        baseX - dy * half to baseY + dx * half,
        tip,
        baseX + dy * half to baseY - dx * half,
    ).map { (x, y) -> geoPointFromNormalized(x, y) }
}

/** The box around [points], for the camera to frame. */
private fun boundsOf(points: List<GeoPoint>): BoundingBox = BoundingBox(
    west = points.minOf { it.longitude },
    south = points.minOf { it.latitude },
    east = points.maxOf { it.longitude },
    north = points.maxOf { it.latitude },
)

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

@Composable
private fun BottomCard(
    business: Business,
    state: BusinessMapState,
    onLocate: () -> Unit,
    onSettings: () -> Unit,
    onOpenBusiness: (Long) -> Unit,
    modifier: Modifier,
) {
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
                        onClick = { if (lat != null && lng != null) openMap(lat, lng, business.title) },
                        shape = Radius.md,
                        modifier = Modifier.weight(1f).height(48.dp),
                    ) {
                        Icon(Icons.Outlined.Directions, contentDescription = null, modifier = Modifier.size(18.dp))
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
                        else Icon(Icons.Outlined.MyLocation, contentDescription = "موقعیت من")
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
                    Text("فاصلهٔ مستقیم شما تا مرکز", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(v.text, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                    Text(v.drive, style = MaterialTheme.typography.bodyMedium)
                    if (v.fromSaved) {
                        Text(
                            "بر اساس آخرین موقعیت ذخیره‌شدهٔ شما",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                is DistanceView.Problem -> {
                    Text(
                        when (v.result) {
                            LocationResult.PermissionDenied -> "برای دیدن فاصله، اجازهٔ دسترسی به موقعیت را بدهید"
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
