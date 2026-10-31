package xyz.sattar.javid.proqueue.feature.explore

import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import kotlinx.coroutines.flow.first
import ovh.plrapps.mapcompose.api.BoundingBox
import ovh.plrapps.mapcompose.api.scrollTo
import ovh.plrapps.mapcompose.api.visibleBoundingBox
import ovh.plrapps.mapcompose.ui.state.MapState
import xyz.sattar.javid.proqueue.core.ui.components.map.GeoPoint
import xyz.sattar.javid.proqueue.core.ui.components.map.distanceKmTo
import xyz.sattar.javid.proqueue.core.ui.components.map.geoPointFromNormalized
import xyz.sattar.javid.proqueue.core.ui.components.map.toNormalized
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

// ── Camera maths ─────────────────────────────────────────────────────────────


/** A circle of [radiusKm] around [center] as normalized map points (a 72-gon is smooth at any zoom we show). */
internal fun circlePoints(center: GeoPoint, radiusKm: Double): List<Pair<Double, Double>> {
    val dLat = radiusKm / 111.32
    val dLng = radiusKm / (111.32 * cos(center.latitude * PI / 180.0))
    return (0..72).map { i ->
        val a = 2 * PI * i / 72
        GeoPoint(center.latitude + dLat * sin(a), center.longitude + dLng * cos(a)).toNormalized()
    }
}

/** Centre of the strip above the sheet, in normalized coordinates. */
internal fun stripCenter(box: BoundingBox, fraction: Float): Pair<Double, Double> =
    (box.xLeft + box.xRight) / 2 to box.yTop + (box.yBottom - box.yTop) * fraction / 2

/** Radius options the API takes; the smallest one that covers [km]. */
internal fun radiusCovering(km: Double): Double =
    radiusOptionsKm.firstOrNull { it >= km } ?: radiusOptionsKm.last()

/** What the user is looking at: the uncovered strip's centre and its half-width. */
internal suspend fun visibleArea(state: MapState, fraction: Float): MapArea {
    val box = state.visibleBoundingBox()
    val (cx, cy) = stripCenter(box, fraction)
    return MapArea(geoPointFromNormalized(cx, cy), radiusCovering(halfWidthKm(box, cy)))
}

/** A long-pressed point, with the radius of the current view. */
internal suspend fun areaAround(state: MapState, point: GeoPoint, fraction: Float): MapArea {
    val box = state.visibleBoundingBox()
    val (_, cy) = stripCenter(box, fraction)
    return MapArea(point, radiusCovering(halfWidthKm(box, cy)))
}

internal fun halfWidthKm(box: BoundingBox, y: Double): Double =
    geoPointFromNormalized(box.xLeft, y).distanceKmTo(geoPointFromNormalized(box.xRight, y)) / 2

/**
 * Moves the camera so [points] sit in the top [visibleFraction] of the map.
 * The box is stretched downwards by the share of the screen the sheet hides,
 * so "fit" means "fit above the sheet".
 */
internal suspend fun fitCamera(
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

/**
 * Zooms onto a chosen area: the ring of [radiusKm] around [center] fills the strip the
 * sheet leaves uncovered, with only a thin margin (no pins to leave room for).
 */
internal suspend fun fitArea(state: MapState, center: GeoPoint, radiusKm: Double, visibleFraction: Float) {
    val ring = circlePoints(center, radiusKm)
    val left = ring.minOf { it.first }
    val right = ring.maxOf { it.first }
    val top = ring.minOf { it.second }
    val bottom = ring.maxOf { it.second }
    val w = right - left
    val h = bottom - top
    val margin = 0.08
    val yTop = top - h * margin
    val visibleHeight = h * (1 + 2 * margin)
    val box = BoundingBox(
        xLeft = left - w * margin,
        yTop = yTop,
        xRight = right + w * margin,
        yBottom = yTop + visibleHeight / visibleFraction.coerceIn(0.2f, 1f),
    )
    state.scrollTo(box, animationSpec = tween(450))
}
