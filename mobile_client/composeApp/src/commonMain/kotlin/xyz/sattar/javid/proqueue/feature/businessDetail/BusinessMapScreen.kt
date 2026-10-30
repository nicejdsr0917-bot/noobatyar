package xyz.sattar.javid.proqueue.feature.businessDetail

import androidx.compose.animation.AnimatedContent
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
    val line = MaterialTheme.colorScheme.primary
    val state = remember(place) { newTileMapState(place, 16.0, density) }
    DisposeMapState(state)
    LaunchedEffect(dark) { state.applyTileTint(dark) }
    LaunchedEffect(state) {
        val (x, y) = place.toNormalized()
        state.addMarker(BUSINESS_MARKER, x, y, relativeOffset = Offset(-0.5f, -1f), clickable = false, zIndex = 1f) {
            BusinessPin(business, selected = true)
        }
    }
    LaunchedEffect(state, me) {
        state.removeMarker(ME_MARKER)
        state.removePath(LINE_PATH)
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
        // The straight line the distance measures.
        state.addPath(LINE_PATH, width = 3.dp, color = line.copy(alpha = 0.7f)) {
            addPoints(listOf(mine, target))
        }
        fitBoth(state, mine, target)
    }
    MapUI(modifier = Modifier.fillMaxSize(), state = state)
}

/**
 * Frames the user and the business above the bottom card. Wider margins than
 * the explore map's fit: the business pin is a labelled bubble about 220 dp
 * wide (over half the screen), centred on its point, so the points stay in
 * the middle third.
 */
private suspend fun fitBoth(state: MapState, a: Pair<Double, Double>, b: Pair<Double, Double>) {
    val w = maxOf(kotlin.math.abs(a.first - b.first), 0.00002)
    val h = maxOf(kotlin.math.abs(a.second - b.second), 0.00002)
    val span = maxOf(w, h)
    val cx = (a.first + b.first) / 2
    val cy = (a.second + b.second) / 2
    val halfW = span * 1.6
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
    data class Known(val text: String, val fromSaved: Boolean) : DistanceView
    data class Problem(val result: LocationResult) : DistanceView
}

@Composable
private fun DistanceBlock(state: BusinessMapState, onLocate: () -> Unit, onSettings: () -> Unit) {
    val km = state.distanceKm()
    val view = when {
        km != null -> DistanceView.Known(formatDistance(km), fromSaved = !state.fresh)
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
