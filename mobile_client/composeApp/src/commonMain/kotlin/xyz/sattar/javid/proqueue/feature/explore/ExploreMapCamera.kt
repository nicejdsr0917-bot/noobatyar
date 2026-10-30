package xyz.sattar.javid.proqueue.feature.explore

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import org.maplibre.compose.camera.CameraState
import org.maplibre.spatialk.geojson.BoundingBox
import org.maplibre.spatialk.geojson.Position
import xyz.sattar.javid.proqueue.core.ui.components.map.GeoPoint
import xyz.sattar.javid.proqueue.core.ui.components.map.distanceKmTo
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.time.Duration.Companion.milliseconds

// ── Camera maths ─────────────────────────────────────────────────────────────

/** Smallest span the camera fits to: one pin alone shouldn't zoom the map down to the building. */
internal const val MIN_SPAN_KM = 2.0

/** Zooming into a cluster may go this close: enough to pull pins a few doors apart. */
internal const val CLUSTER_MIN_SPAN_KM = 0.1

/** A search centre counts as "moved away from" past this share of the visible width. */
internal const val MOVED_THRESHOLD = 0.2

internal fun GeoPoint.toPosition() = Position(longitude, latitude)
internal fun Position.toGeoPoint() = GeoPoint(latitude, longitude)

/** A circle of [radiusKm] around [center] (a 72-gon is smooth at any zoom we show). */
internal fun circlePoints(center: GeoPoint, radiusKm: Double): List<GeoPoint> {
    val dLat = radiusKm / 111.32
    val dLng = radiusKm / (111.32 * cos(center.latitude * PI / 180.0))
    return (0..72).map { i ->
        val a = 2 * PI * i / 72
        GeoPoint(center.latitude + dLat * sin(a), center.longitude + dLng * cos(a))
    }
}

/** Radius options the API takes; the smallest one that covers [km]. */
internal fun radiusCovering(km: Double): Double =
    radiusOptionsKm.firstOrNull { it >= km } ?: radiusOptionsKm.last()

/** The box around [points], grown to at least [minSpanKm] each way around its centre. */
internal fun boxAround(points: List<GeoPoint>, minSpanKm: Double): BoundingBox {
    val south = points.minOf { it.latitude }
    val north = points.maxOf { it.latitude }
    val west = points.minOf { it.longitude }
    val east = points.maxOf { it.longitude }
    val midLat = (south + north) / 2
    val midLng = (west + east) / 2
    val minDLat = minSpanKm / 111.32 / 2
    val minDLng = minSpanKm / (111.32 * cos(midLat * PI / 180.0)) / 2
    return BoundingBox(
        west = minOf(west, midLng - minDLng),
        south = minOf(south, midLat - minDLat),
        east = maxOf(east, midLng + minDLng),
        north = maxOf(north, midLat + minDLat),
    )
}

/**
 * Moves the camera so [points] sit in the strip above the sheet: the sheet's
 * height ([bottomInset]) is padding, so "fit" means "fit above the sheet".
 */
internal suspend fun fitCamera(
    camera: CameraState,
    points: List<GeoPoint>,
    bottomInset: Dp,
    minSpanKm: Double = MIN_SPAN_KM,
) {
    if (points.isEmpty()) return
    camera.animateTo(
        boundingBox = boxAround(points, minSpanKm),
        // Room on top for the pins' heads, on the bottom for the control row.
        padding = PaddingValues(start = 40.dp, end = 40.dp, top = 64.dp, bottom = bottomInset + 72.dp),
        duration = 450.milliseconds,
    )
}

/**
 * What the user is looking at: the uncovered strip's centre, and a radius
 * that covers half its width. Null before the map is laid out.
 */
internal fun visibleArea(camera: CameraState, width: Dp, stripHeight: Dp): MapArea? {
    val projection = camera.projection ?: return null
    val y = stripHeight / 2
    val center = projection.positionFromScreenLocation(DpOffset(width / 2, y)).toGeoPoint()
    return MapArea(center, radiusCovering(halfWidthKm(camera, width, y) ?: return null))
}

/** A long-pressed point, with the radius of the current view. */
internal fun areaAround(camera: CameraState, point: GeoPoint, width: Dp, stripHeight: Dp): MapArea? {
    val half = halfWidthKm(camera, width, stripHeight / 2) ?: return null
    return MapArea(point, radiusCovering(half))
}

private fun halfWidthKm(camera: CameraState, width: Dp, y: Dp): Double? {
    val projection = camera.projection ?: return null
    val left = projection.positionFromScreenLocation(DpOffset(0.dp, y)).toGeoPoint()
    val right = projection.positionFromScreenLocation(DpOffset(width, y)).toGeoPoint()
    return left.distanceKmTo(right) / 2
}

/**
 * Pins closer than [threshold] on screen merge into one cluster. Greedy, in
 * the given order, so the grouping is stable between frames; [exempt] (the
 * selected pin) is never folded into a cluster. Returns groups of ids: a
 * single id is a lone pin.
 */
internal fun clusterOnScreen(points: List<Pair<Long, Offset>>, threshold: Float, exempt: Long? = null): List<List<Long>> {
    val taken = BooleanArray(points.size)
    val groups = mutableListOf<List<Long>>()
    for (i in points.indices) {
        if (taken[i]) continue
        taken[i] = true
        val (id, at) = points[i]
        if (id == exempt) {
            groups += listOf(id)
            continue
        }
        val group = mutableListOf(id)
        for (j in i + 1 until points.size) {
            if (taken[j] || points[j].first == exempt) continue
            if ((points[j].second - at).getDistance() < threshold) {
                taken[j] = true
                group += points[j].first
            }
        }
        groups += group
    }
    return groups
}
