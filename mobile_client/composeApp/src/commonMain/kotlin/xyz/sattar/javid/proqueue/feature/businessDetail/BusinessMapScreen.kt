package xyz.sattar.javid.proqueue.feature.businessDetail

import androidx.compose.animation.AnimatedContent
import androidx.compose.runtime.MutableLongState
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.unit.Dp
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull
import xyz.sattar.javid.proqueue.core.ui.components.map.loadedTileCount
import kotlin.math.roundToInt
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.material.icons.rounded.Navigation
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import ovh.plrapps.mapcompose.api.moveMarker
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
import ovh.plrapps.mapcompose.api.BoundingBox
import ovh.plrapps.mapcompose.api.addMarker
import ovh.plrapps.mapcompose.api.addPath
import ovh.plrapps.mapcompose.api.removeMarker
import ovh.plrapps.mapcompose.api.removePath
import ovh.plrapps.mapcompose.api.scale
import ovh.plrapps.mapcompose.api.scrollTo
import ovh.plrapps.mapcompose.ui.MapUI
import xyz.sattar.javid.proqueue.core.location.LocationResult
import xyz.sattar.javid.proqueue.core.location.rememberLocationRequester
import xyz.sattar.javid.proqueue.core.ui.components.map.DisposeMapState
import xyz.sattar.javid.proqueue.core.ui.components.map.GeoPoint
import xyz.sattar.javid.proqueue.core.ui.components.map.MapAttribution
import xyz.sattar.javid.proqueue.core.ui.components.map.applyTileTint
import xyz.sattar.javid.proqueue.core.ui.components.map.newTileMapState
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

private const val BUSINESS_MARKER = "business"
private const val ME_MARKER = "me"
private const val LINE_PATH = "line"
private const val ARROW_MARKER = "arrow"

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
                    modifier = Modifier.align(Alignment.BottomCenter),
                )
            }
        }
    }
}

@Composable
private fun FullMap(business: Business, place: GeoPoint, me: GeoPoint?) {
    val density = LocalDensity.current.density
    val dark = LocalIsDarkTheme.current
    val reduced = LocalReducedMotion.current
    val accent = MaterialTheme.colorScheme.primary
    val state = remember(place) { newTileMapState(place, 16.0, density) }
    DisposeMapState(state)
    LaunchedEffect(dark) { state.applyTileTint(dark) }

    // The map fades in once tiles arrive; only then does it tilt (0 flat, 1
    // full 3D) and the arrow fly, settling into a gentle tilt at the end.
    val shown = remember { Animatable(if (reduced) 1f else 0f) }
    val tilt = remember { Animatable(0f) }
    var played by remember { mutableStateOf(false) }
    var flying by remember { mutableStateOf(false) }
    var heading by remember { mutableStateOf(0f) }
    var arrivedKm by remember { mutableStateOf<Double?>(null) }

    // Invisible anchors inside the tilted map; the upright overlays are placed
    // where those anchors land on screen after the 3D transform.
    val anchors = remember { mutableStateMapOf<String, LayoutCoordinates>() }
    var root by remember { mutableStateOf<LayoutCoordinates?>(null) }
    val frame = remember { mutableLongStateOf(0L) }
    LaunchedEffect(Unit) { while (true) withFrameNanos { frame.longValue = it } }

    LaunchedEffect(state) {
        val (x, y) = place.toNormalized()
        state.addMarker(BUSINESS_MARKER, x, y, relativeOffset = Offset(-0.5f, -0.5f), clickable = false) {
            Anchor { anchors[BUSINESS_MARKER] = it }
        }
    }
    LaunchedEffect(state) {
        val start = loadedTileCount.value
        withTimeoutOrNull(4_000) { loadedTileCount.first { it >= start + 4 } }
        shown.animateTo(1f, tween(450))
    }
    LaunchedEffect(state, me) {
        state.removeMarker(ME_MARKER)
        state.removeMarker(ARROW_MARKER)
        state.removePath(LINE_PATH)
        arrivedKm = null
        val target = place.toNormalized()
        if (me == null) {
            // Above the bottom card, at street level.
            state.scrollTo(target.first, target.second, destScale = state.scale, screenOffset = Offset(-0.5f, -MAP_VISIBLE_FRACTION / 2))
            return@LaunchedEffect
        }
        val mine = me.toNormalized()
        state.addMarker(ME_MARKER, mine.first, mine.second, relativeOffset = Offset(-0.5f, -0.5f), clickable = false, zIndex = 0f) {
            Box(Modifier.onGloballyPositioned { anchors[ME_MARKER] = it }) { MyLocationDot() }
        }
        val before = loadedTileCount.value
        fitBoth(state, mine, target)

        if (!reduced && !played) {
            played = true
            // The map first: wait for this view's tiles, and for the fade-in.
            withTimeoutOrNull(5_000) { loadedTileCount.first { it >= before + 6 } }
            shown.animateTo(1f, tween(300))
            delay(350)
            tilt.animateTo(1f, tween(900, easing = FastOutSlowInEasing))
            heading = screenHeading(root, anchors[ME_MARKER], anchors[BUSINESS_MARKER])
                ?: (atan2(target.second - mine.second, target.first - mine.first) * 180.0 / PI).toFloat()
            state.addMarker(ARROW_MARKER, mine.first, mine.second, relativeOffset = Offset(-0.5f, -0.5f), clickable = false) {
                Anchor { anchors[ARROW_MARKER] = it }
            }
            flying = true
            val flight = Animatable(0f)
            flight.animateTo(1f, tween(1_800, easing = FastOutSlowInEasing)) {
                val at = lerp(mine, target, value.toDouble())
                state.removePath(LINE_PATH)
                state.addPath(LINE_PATH, width = 5.dp, color = accent) { addPoints(listOf(mine, at)) }
                state.moveMarker(ARROW_MARKER, at.first, at.second)
            }
            flying = false
            state.removeMarker(ARROW_MARKER)
            anchors.remove(ARROW_MARKER)
            tilt.animateTo(0.6f, spring(dampingRatio = Spring.DampingRatioLowBouncy, stiffness = Spring.StiffnessLow))
        }
        state.removePath(LINE_PATH)
        state.addPath(LINE_PATH, width = 5.dp, color = accent.copy(alpha = 0.8f)) { addPoints(listOf(mine, target)) }
        arrivedKm = me.distanceKmTo(place)
    }

    Box(
        Modifier
            .fillMaxSize()
            // A soft sky behind the tilted map's far edge.
            .background(
                Brush.verticalGradient(
                    listOf(accent.copy(alpha = 0.14f), MaterialTheme.colorScheme.surfaceVariant)
                )
            )
            .onGloballyPositioned { root = it }
    ) {
        Box(
            Modifier
                .fillMaxSize()
                .graphicsLayer {
                    val t = tilt.value
                    alpha = shown.value
                    rotationX = 45f * t
                    // A far camera keeps the perspective soft, so the far edge
                    // narrows only a little; the scale-up covers what remains.
                    cameraDistance = 18f * density
                    scaleX = 1f + 0.28f * t + 0.04f * (1f - shown.value)
                    scaleY = 1f + 0.28f * t + 0.04f * (1f - shown.value)
                    transformOrigin = TransformOrigin(0.5f, 0.62f)
                }
        ) {
            MapUI(modifier = Modifier.fillMaxSize(), state = state)
        }

        // Upright, like signs standing on the map: never tilted with it.
        val r = root
        // The compact pin: the name is in the bottom card, and a wide labelled
        // bubble would cover the route at this zoom.
        Standing(r, anchors[BUSINESS_MARKER], frame, alignBottom = true, alpha = shown.value) {
            Box(Modifier.scale(1.35f)) { BusinessPin(business, selected = false) }
        }
        if (flying) {
            Standing(r, anchors[ARROW_MARKER], frame, alignBottom = false) { ArrowHead(heading) }
        }
        arrivedKm?.let { km ->
            Standing(r, anchors[BUSINESS_MARKER], frame, alignBottom = true, lift = 58.dp, keepOnScreen = true) { ArrivalLabel(km) }
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

/** A 1 dp point in the map that reports where it is. */
@Composable
private fun Anchor(onPlaced: (LayoutCoordinates) -> Unit) {
    Box(Modifier.size(1.dp).onGloballyPositioned(onPlaced))
}

/** Where [anchor]'s centre is on screen, after the map layer's 3D transform. */
private fun projected(root: LayoutCoordinates?, anchor: LayoutCoordinates?): Offset? {
    if (root == null || anchor == null || !root.isAttached || !anchor.isAttached) return null
    return root.localPositionOf(anchor, Offset(anchor.size.width / 2f, anchor.size.height / 2f))
}

/** The on-screen direction from [from] to [to], in degrees from "right". */
private fun screenHeading(root: LayoutCoordinates?, from: LayoutCoordinates?, to: LayoutCoordinates?): Float? {
    val a = projected(root, from) ?: return null
    val b = projected(root, to) ?: return null
    if (a == b) return null
    return (atan2((b.y - a.y).toDouble(), (b.x - a.x).toDouble()) * 180.0 / PI).toFloat()
}

/**
 * Places [content] upright at [anchor]'s projected point, re-placed every
 * frame so it follows the map's pans and tilt: bottom-centre on the point
 * when [alignBottom], centred otherwise, raised by [lift].
 */
@Composable
private fun Standing(
    root: LayoutCoordinates?,
    anchor: LayoutCoordinates?,
    frame: MutableLongState,
    alignBottom: Boolean,
    lift: Dp = 0.dp,
    alpha: Float = 1f,
    keepOnScreen: Boolean = false,
    content: @Composable () -> Unit,
) {
    if (root == null || anchor == null) return
    val liftPx = with(LocalDensity.current) { lift.roundToPx() }
    Box(
        Modifier
            .graphicsLayer { this.alpha = alpha }
            .layout { measurable, constraints ->
                val placeable = measurable.measure(constraints.copy(minWidth = 0, minHeight = 0))
                // Full size, so the origin is the screen's: a content-sized box
                // would be put at the start edge, the right side in RTL.
                layout(constraints.maxWidth, constraints.maxHeight) {
                    frame.longValue // re-place on every frame
                    val at = projected(root, anchor) ?: return@layout
                    val centred = at.x.roundToInt() - placeable.width / 2
                    // A card slides inwards rather than being cut by the screen edge.
                    val margin = 12.dp.roundToPx()
                    val x = if (keepOnScreen) {
                        centred.coerceIn(margin, maxOf(margin, constraints.maxWidth - placeable.width - margin))
                    } else centred
                    val y = if (alignBottom) at.y.roundToInt() - placeable.height - liftPx
                    else at.y.roundToInt() - placeable.height / 2
                    placeable.place(x, y)
                }
            }
    ) { content() }
}

private fun lerp(a: Pair<Double, Double>, b: Pair<Double, Double>, t: Double) =
    a.first + (b.first - a.first) * t to a.second + (b.second - a.second) * t

/** The travelling arrowhead: a violet disc with a white arrow pointing along the flight. */
@Composable
private fun ArrowHead(headingDegrees: Float) {
    Box(
        Modifier
            .size(34.dp)
            .shadow(8.dp, CircleShape, ambientColor = MaterialTheme.colorScheme.primary, spotColor = MaterialTheme.colorScheme.primary)
            .background(MaterialTheme.colorScheme.primary, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        // The glyph points up; screen headings are measured from "right".
        Icon(
            Icons.Rounded.Navigation,
            contentDescription = null,
            tint = Color.White,
            modifier = Modifier.size(20.dp).rotate(headingDegrees + 90f),
        )
    }
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

/**
 * Frames the user and the business above the bottom card, with room on the
 * sides for the arrival card (about 180 dp wide) centred over the business.
 */
private suspend fun fitBoth(state: MapState, a: Pair<Double, Double>, b: Pair<Double, Double>) {
    val w = maxOf(kotlin.math.abs(a.first - b.first), 0.00002)
    val h = maxOf(kotlin.math.abs(a.second - b.second), 0.00002)
    val span = maxOf(w, h)
    val cx = (a.first + b.first) / 2
    val cy = (a.second + b.second) / 2
    // Room for the arrival card over the business; the tilt's scale-up
    // spreads the points outwards a little too.
    val halfW = span * 1.5
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
    modifier: Modifier,
) {
    Column(modifier.fillMaxWidth()) {
        MapAttribution(Modifier.padding(start = Space.md, bottom = Space.sm))
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
