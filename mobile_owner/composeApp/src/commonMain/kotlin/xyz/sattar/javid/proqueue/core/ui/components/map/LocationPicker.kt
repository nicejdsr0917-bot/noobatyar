package xyz.sattar.javid.proqueue.core.ui.components.map

import xyz.sattar.javid.proqueue.core.i18n.Str
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.EditLocationAlt
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.Map
import androidx.compose.material.icons.rounded.MyLocation
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material.icons.rounded.TouchApp
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch
import ovh.plrapps.mapcompose.api.addMarker
import ovh.plrapps.mapcompose.api.centroidSnapshotFlow
import ovh.plrapps.mapcompose.api.centroidX
import ovh.plrapps.mapcompose.api.centroidY
import ovh.plrapps.mapcompose.api.disableGestures
import ovh.plrapps.mapcompose.api.scale
import ovh.plrapps.mapcompose.api.scrollTo
import ovh.plrapps.mapcompose.api.snapScrollTo
import ovh.plrapps.mapcompose.ui.MapUI
import ovh.plrapps.mapcompose.ui.state.MapState
import xyz.sattar.javid.proqueue.core.location.GeoPoint
import xyz.sattar.javid.proqueue.core.location.rememberCurrentLocationRequester
import xyz.sattar.javid.proqueue.core.ui.components.AppButton
import kotlin.math.max
import kotlin.math.pow
import kotlin.math.round

/**
 * The business's pin on the map, in the business form: a small map preview
 * once set, an invitation to set one otherwise. Optional — the written
 * address stays the required field — but with a pin the customer's booking
 * page gets a map and "route with Neshan / Balad" buttons.
 */
@Composable
fun BusinessLocationField(
    location: GeoPoint?,
    onLocation: (GeoPoint?) -> Unit,
    enabled: Boolean,
    modifier: Modifier = Modifier,
) {
    var showPicker by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(16.dp)

    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = Str.location_on_map,
            style = MaterialTheme.typography.titleSmall,
        )
        Text(
            text = Str.optional_customers_see_your_business,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        if (location == null) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(shape)
                    .border(
                        BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.35f)),
                        shape
                    )
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.06f))
                    .clickable(enabled = enabled) { showPicker = true }
                    .padding(horizontal = 16.dp, vertical = 18.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f), CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Map,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                    )
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = Str.pick_location_on_map,
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    Text(
                        text = Str.put_pin_on_your_businesss,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        } else {
            LocationPreviewMap(
                point = location,
                onClick = if (enabled) ({ showPicker = true }) else null,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(170.dp)
                    .clip(shape),
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                TextButton(enabled = enabled, onClick = { showPicker = true }) {
                    Icon(Icons.Rounded.EditLocationAlt, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(Str.change_location, style = MaterialTheme.typography.labelLarge)
                }
                TextButton(enabled = enabled, onClick = { onLocation(null) }) {
                    Icon(
                        Icons.Rounded.Delete,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        Str.remove_from_map,
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
        }
    }

    if (showPicker) {
        LocationPickerDialog(
            initial = location,
            onDismiss = { showPicker = false },
            onConfirm = {
                onLocation(it)
                showPicker = false
            },
        )
    }
}

/** A still map with the pin on [point], gestures off; [onClick] for the
 *  whole map (the form opens the picker with it). */
@Composable
fun LocationPreviewMap(
    point: GeoPoint,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
) {
    val density = LocalDensity.current.density
    val state = remember(point, density) {
        val (x, y) = point.toNormalized()
        newTileMapState(point, MapTiles.PIN_ZOOM - 1, density).apply {
            disableGestures()
            addMarker("pin", x, y, absoluteOffset = DpOffset(0.dp, PinTipInset), clickable = false) {
                MapPin()
            }
        }
    }
    DisposeMapState(state)

    Box(modifier = modifier.background(MapBackground)) {
        TileMapView(state, Modifier.fillMaxSize())
        MapAttribution(Modifier.align(Alignment.BottomStart).padding(6.dp))
        // On top of MapUI rather than on this Box: MapUI consumes taps even
        // with its gestures off, so a click on the parent would never fire.
        if (onClick != null) {
            Box(Modifier.matchParentSize().clickable(onClick = onClick))
        }
    }
}

/**
 * Full-screen picker: the map moves under a pin fixed at the centre, the
 * way Neshan and Balad do it — far easier to be precise with on a phone
 * than dragging a small pin under a finger.
 */
@Composable
fun LocationPickerDialog(
    initial: GeoPoint?,
    onDismiss: () -> Unit,
    onConfirm: (GeoPoint) -> Unit,
) {
    val density = LocalDensity.current.density
    val state = remember(density) {
        newTileMapState(
            center = initial ?: MapTiles.DEFAULT_CENTER,
            zoom = if (initial != null) MapTiles.PIN_ZOOM else MapTiles.DEFAULT_ZOOM,
            density = density,
        )
    }
    DisposeMapState(state)

    val scope = rememberCoroutineScope()
    val requester = rememberCurrentLocationRequester()
    var locating by remember { mutableStateOf(false) }
    var notice by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(notice) {
        if (notice != null) {
            delay(4500)
            notice = null
        }
    }

    // True while the map is being dragged or animated; the pin lifts off
    // the map meanwhile and drops back when it settles.
    val moving by produceState(false, state) {
        state.centroidSnapshotFlow().drop(1).collectLatest {
            value = true
            delay(220)
            value = false
        }
    }
    val pinLift by animateDpAsState(if (moving) 10.dp else 0.dp, tween(160))

    fun zoomBy(factor: Double) {
        scope.launch {
            state.scrollTo(state.centroidX, state.centroidY, destScale = state.scale * factor, animationSpec = tween(280))
        }
    }

    // Mouse wheel / trackpad zoom for the web panel on desktop (MapCompose
    // 0.13 has none). Always about the centre — that's where the pin is, so
    // the picked point stays put while zooming. Setting the scale alone would
    // zoom about the top-left corner, hence the re-centre right after.
    fun wheelZoom(scrollY: Float) {
        val factor = 2.0.pow((-scrollY * 0.25).coerceIn(-0.5, 0.5))
        scope.launch {
            val x = state.centroidX
            val y = state.centroidY
            state.scale *= factor
            state.snapScrollTo(x, y)
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        BoxWithConstraints(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            // Edge to edge on a phone; a large card on desktop, where a
            // window-wide map would be all empty margin around one pin.
            val fullScreen = maxWidth < 600.dp || maxHeight < 560.dp
            Surface(
                shape = if (fullScreen) RectangleShape else RoundedCornerShape(24.dp),
                color = MaterialTheme.colorScheme.surface,
                modifier = if (fullScreen) Modifier.fillMaxSize() else Modifier
                    .width(minOf(maxWidth - 48.dp, 760.dp))
                    .height(minOf(maxHeight - 48.dp, 820.dp)),
            ) {
                Column(Modifier.fillMaxSize()) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(start = 4.dp, end = 16.dp, top = 8.dp, bottom = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        IconButton(onClick = onDismiss) {
                            Icon(Icons.Rounded.Close, contentDescription = Str.close)
                        }
                        Text(
                            text = Str.business_location_on_map,
                            style = MaterialTheme.typography.titleMedium,
                            modifier = Modifier.weight(1f),
                        )
                    }

                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                            .clipToBounds()
                            .background(MapBackground)
                            .pointerInput(Unit) {
                                awaitPointerEventScope {
                                    while (true) {
                                        val event = awaitPointerEvent(PointerEventPass.Initial)
                                        if (event.type != PointerEventType.Scroll) continue
                                        val dy = event.changes.sumOf { it.scrollDelta.y.toDouble() }.toFloat()
                                        if (dy != 0f) {
                                            wheelZoom(dy)
                                            event.changes.forEach { it.consume() }
                                        }
                                    }
                                }
                            }
                    ) {
                        TileMapView(state, Modifier.fillMaxSize())

                        // The pin's tip is the picked point: the map's centre.
                        MapPin(
                            Modifier
                                .align(Alignment.Center)
                                .offset(y = -(PinSize / 2 - PinTipInset) - pinLift)
                        )

                        AnimatedContent(
                            targetState = notice,
                            modifier = Modifier.align(Alignment.TopCenter).padding(12.dp),
                        ) { message ->
                            MapHint(
                                text = message ?: Str.drag_map_so_pins_tip,
                                icon = if (message != null) Icons.Rounded.ErrorOutline else Icons.Rounded.TouchApp,
                                isError = message != null,
                            )
                        }

                        Column(
                            modifier = Modifier.align(Alignment.BottomEnd).padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            MapControlButton(Icons.Rounded.Add, Str.zoom_in) { zoomBy(2.0) }
                            MapControlButton(Icons.Rounded.Remove, Str.zoom_out) { zoomBy(0.5) }
                            if (requester.isAvailable) {
                                Spacer(Modifier.height(4.dp))
                                MapControlButton(Icons.Rounded.MyLocation, Str.my_location, loading = locating) {
                                    if (locating) return@MapControlButton
                                    locating = true
                                    requester.request { point ->
                                        locating = false
                                        if (point == null) {
                                            notice = Str.couldnt_find_your_current_location
                                        } else {
                                            val (x, y) = point.toNormalized()
                                            scope.launch {
                                                state.scrollTo(
                                                    x, y,
                                                    destScale = max(state.scale, scaleForZoom(MapTiles.PIN_ZOOM, density)),
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        MapAttribution(Modifier.align(Alignment.BottomStart).padding(8.dp))
                    }

                    Box(Modifier.fillMaxWidth().padding(16.dp)) {
                        AppButton(
                            text = Str.save_this_location,
                            enabled = !moving,
                            onClick = {
                                val picked = geoPointFromNormalized(state.centroidX, state.centroidY)
                                onConfirm(GeoPoint(picked.latitude.round6(), picked.longitude.round6()))
                            },
                        )
                    }
                }
            }
        }
    }
}

/**
 * The map itself, always laid out left-to-right: north-up, east-right is a
 * fact about the earth, not about the reading direction. Under the app's RTL
 * layout MapCompose would mirror where it places tiles and markers.
 */
@Composable
private fun TileMapView(state: MapState, modifier: Modifier) {
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
        MapUI(modifier = modifier, state = state)
    }
}

@Composable
private fun MapHint(text: String, icon: ImageVector, isError: Boolean) {
    val container = if (isError) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.surface
    val content = if (isError) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.onSurface
    Row(
        modifier = Modifier
            .shadow(4.dp, RoundedCornerShape(50))
            .background(container, RoundedCornerShape(50))
            .padding(horizontal = 14.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Icon(icon, contentDescription = null, tint = content, modifier = Modifier.size(18.dp))
        Text(text, style = MaterialTheme.typography.bodySmall, color = content)
    }
}

@Composable
private fun MapControlButton(
    icon: ImageVector,
    description: String,
    loading: Boolean = false,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .size(44.dp)
            .shadow(4.dp, CircleShape)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.surface)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        if (loading) {
            CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
        } else {
            Icon(icon, contentDescription = description, tint = MaterialTheme.colorScheme.onSurface)
        }
    }
}

private fun Double.round6(): Double = round(this * 1_000_000) / 1_000_000
