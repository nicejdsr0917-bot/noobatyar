package xyz.sattar.javid.proqueue.feature.explore

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import org.maplibre.compose.camera.CameraMoveReason
import org.maplibre.compose.camera.CameraPosition
import org.maplibre.compose.camera.CameraState
import org.maplibre.compose.camera.rememberCameraState
import org.maplibre.compose.expressions.dsl.const
import org.maplibre.compose.layers.LineLayer
import org.maplibre.compose.sources.GeoJsonData
import org.maplibre.compose.sources.rememberGeoJsonSource
import org.maplibre.compose.util.ClickResult
import org.maplibre.spatialk.geojson.LineString
import xyz.sattar.javid.proqueue.core.ui.components.map.GeoPoint
import xyz.sattar.javid.proqueue.core.ui.components.map.MapAttribution
import xyz.sattar.javid.proqueue.core.ui.components.map.MapTiles
import xyz.sattar.javid.proqueue.core.ui.components.map.VectorMap
import xyz.sattar.javid.proqueue.core.ui.components.map.distanceKmTo
import xyz.sattar.javid.proqueue.core.ui.components.map.mapBackground
import xyz.sattar.javid.proqueue.domain.model.business.Business
import xyz.sattar.javid.proqueue.feature.businessDetail.PinAnchor
import xyz.sattar.javid.proqueue.feature.businessDetail.PinnedAt
import xyz.sattar.javid.proqueue.ui.theme.LocalIsDarkTheme
import kotlin.time.Duration.Companion.milliseconds

/** A point on the map plus a radius that covers what's on screen around it. */
data class MapArea(val center: GeoPoint, val radiusKm: Double)

/** Pins closer than this on screen fold into a count bubble. */
private val CLUSTER_DISTANCE = 44.dp

/**
 * The results on a map.
 *
 * - One pin per business with coordinates, coloured by category; listings
 *   that can't take a booking get a quieter, outlined pin. Pins close on
 *   screen fold into a count bubble; tapping one fits its pins.
 * - The camera fits the pins into the strip the sheet leaves uncovered
 *   (the top [visibleFraction] of this composable; [bottomInset] is the sheet).
 * - Moving the map by hand offers "search this area"; a long press searches
 *   around the pressed point; [pickMode] shows a fixed centre pin to aim with.
 */
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
    val dark = LocalIsDarkTheme.current
    val scope = rememberCoroutineScope()
    val camera = rememberCameraState(
        CameraPosition(
            target = (area.latitude?.let { GeoPoint(it, area.longitude!!) } ?: MapTiles.DEFAULT_CENTER).toPosition(),
            zoom = MapTiles.DEFAULT_ZOOM,
        )
    )
    var loaded by remember { mutableStateOf(false) }
    val select by rememberUpdatedState(onSelect)
    val searchArea by rememberUpdatedState(onSearchArea)

    val placed = remember(businesses) {
        businesses.mapNotNull { b ->
            val lat = b.latitude ?: return@mapNotNull null
            val lng = b.longitude ?: return@mapNotNull null
            b to GeoPoint(lat, lng)
        }
    }
    val me = (area as? SearchArea.NearMe)?.let { GeoPoint(it.latitude, it.longitude) }
    val picked = (area as? SearchArea.Picked)?.let { GeoPoint(it.latitude, it.longitude) }
    val center = area.latitude?.let { GeoPoint(it, area.longitude!!) }

    BoxWithConstraints(modifier.background(mapBackground(dark))) {
        val width = maxWidth
        val stripHeight = maxHeight * visibleFraction

        // New results: fit them (and the user) above the sheet. A picked point
        // already has the camera where the user wants it.
        LaunchedEffect(placed, me, picked, loaded) {
            if (!loaded || picked != null) return@LaunchedEffect
            fitCamera(camera, placed.map { it.second } + listOfNotNull(me), bottomInset)
        }

        // The selected pin slides into the middle of the uncovered strip.
        LaunchedEffect(selectedId.value, loaded) {
            if (!loaded) return@LaunchedEffect
            val point = placed.firstOrNull { it.first.id == selectedId.value }?.second ?: return@LaunchedEffect
            camera.animateTo(
                camera.position.copy(target = point.toPosition(), padding = PaddingValues(bottom = bottomInset)),
                duration = 350.milliseconds,
            )
        }

        // "Search this area": once the map comes to rest after the user moved
        // it, and only if it really moved away from what was searched.
        var offerSearchHere by remember { mutableStateOf(false) }
        LaunchedEffect(camera.isCameraMoving, area) {
            if (camera.isCameraMoving) return@LaunchedEffect
            if (camera.moveReason != CameraMoveReason.GESTURE) {
                offerSearchHere = false
                return@LaunchedEffect
            }
            val visible = visibleArea(camera, width, stripHeight) ?: return@LaunchedEffect
            offerSearchHere = center == null ||
                visible.center.distanceKmTo(center) > visible.radiusKm * 2 * MOVED_THRESHOLD
        }

        val ringColor = MaterialTheme.colorScheme.primary
        VectorMap(
            cameraState = camera,
            dark = dark,
            modifier = Modifier.fillMaxSize(),
            onMapClick = { _, _ ->
                select(null)
                ClickResult.Pass
            },
            onMapLongClick = { position, _ ->
                areaAround(camera, position.toGeoPoint(), width, stripHeight)?.let(searchArea)
                ClickResult.Consume
            },
            onMapLoadFinished = { loaded = true },
        ) {
            // The search area as a soft ring: what "۱۰ کیلومتر" means, drawn on
            // the map. Outline only: at street zoom the ring is larger than the
            // screen, and a fill would wash the whole map in the accent colour.
            if (center != null) {
                val ring = rememberGeoJsonSource(
                    GeoJsonData.Features(LineString(circlePoints(center, radiusKm).map { it.toPosition() }))
                )
                LineLayer(id = "search-radius", source = ring, width = const(2.dp), color = const(ringColor.copy(alpha = 0.55f)))
            }
        }

        me?.let { PinnedAt(camera, it, PinAnchor.Center) { MyLocationDot() } }
        picked?.let { PinnedAt(camera, it, PinAnchor.Center) { PickedAreaDot() } }
        ClusteredPins(
            camera = camera,
            placed = placed,
            selectedId = selectedId.value,
            onSelect = { select(it) },
            onCluster = { points ->
                scope.launch { fitCamera(camera, points, bottomInset, minSpanKm = CLUSTER_MIN_SPAN_KM) }
            },
        )

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
                        offerSearchHere = false
                        visibleArea(camera, width, stripHeight)?.let(searchArea)
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
            CenterPin(
                lifted = camera.isCameraMoving,
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
                    onClick = { visibleArea(camera, width, stripHeight)?.let(searchArea) },
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

/**
 * The business pins, folded into count bubbles where they crowd on screen.
 * Regrouped on every camera move (a few dozen pins: cheap). The selected pin
 * never folds away and draws on top.
 */
@Composable
private fun ClusteredPins(
    camera: CameraState,
    placed: List<Pair<Business, GeoPoint>>,
    selectedId: Long?,
    onSelect: (Long) -> Unit,
    onCluster: (List<GeoPoint>) -> Unit,
) {
    val projection = camera.projection ?: return
    camera.position // regroup whenever the camera moves
    val density = LocalDensity.current
    val byId = remember(placed) { placed.associate { it.first.id to it } }
    val onScreen = placed.map { (business, point) ->
        val at = projection.screenLocationFromPosition(point.toPosition())
        business.id to with(density) { Offset(at.x.toPx(), at.y.toPx()) }
    }
    val groups = clusterOnScreen(onScreen, with(density) { CLUSTER_DISTANCE.toPx() }, exempt = selectedId)
    // Lone pins after bubbles, and the selected one last, so it draws on top.
    val ordered = groups.sortedWith(compareBy({ it.size == 1 }, { it.singleOrNull() == selectedId }))
    ordered.forEach { ids ->
        key(ids) {
            if (ids.size == 1) {
                val (business, point) = byId.getValue(ids.single())
                PinnedAt(camera, point, PinAnchor.Bottom) {
                    Box(Modifier.clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { onSelect(business.id) }) {
                        BusinessPin(business, selected = selectedId == business.id)
                    }
                }
            } else {
                val points = ids.map { byId.getValue(it).second }
                val middle = GeoPoint(points.map { it.latitude }.average(), points.map { it.longitude }.average())
                PinnedAt(camera, middle, PinAnchor.Center) {
                    Box(Modifier.clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { onCluster(points) }) {
                        ClusterBubble(count = ids.size)
                    }
                }
            }
        }
    }
}
