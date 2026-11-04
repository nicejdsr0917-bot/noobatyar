package xyz.sattar.javid.proqueue.feature.home

import xyz.sattar.javid.proqueue.core.i18n.Str
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp

/**
 * A line chart of appointment counts: one clean stroke over a faint area,
 * drawn in with a sweep. Pure Compose Canvas, so it renders the same on
 * Android, iOS and web.
 *
 * This is the drawing only; the card, its title and any period switching belong
 * to the caller (see [HomeTrendCard]). It used to be a whole card, which meant
 * the chart could not be reused inside another one.
 */
@Composable
fun NeonLineCanvas(
    counts: List<Int>,
    modifier: Modifier = Modifier,
    height: Dp = 120.dp,
    lineColor: Color = MaterialTheme.colorScheme.primary,
    emptyLabel: String = Str.no_data_to_show_yet,
) {
    val peak = counts.maxOrNull() ?: 0
    val empty = counts.size < 2 || peak == 0

    // The line draws itself in from the start each time the series changes:
    // a sweep reads as "here is the data", where a cut just flickers.
    val sweep = remember(counts) { Animatable(0f) }
    LaunchedEffect(counts) {
        sweep.animateTo(1f, tween(900, easing = FastOutSlowInEasing))
    }
    val gridColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.07f)
    val dotFill = MaterialTheme.colorScheme.surfaceContainerLow

    Box(modifier = modifier.fillMaxWidth().height(height), contentAlignment = Alignment.Center) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val w = size.width
            val h = size.height
            val padTop = 12.dp.toPx()
            val padBottom = 8.dp.toPx()
            val padH = 6.dp.toPx()
            val plotTop = padTop
            val plotBottom = h - padBottom

            // Dashed gridlines and a solid baseline. Without them the line
            // floats in space and there is no way to read a height off it.
            listOf(0f, 0.5f, 1f).forEach { t ->
                val y = plotBottom - t * (plotBottom - plotTop)
                drawLine(
                    color = if (t == 0f) gridColor.copy(alpha = 0.14f) else gridColor,
                    start = Offset(padH, y),
                    end = Offset(w - padH, y),
                    strokeWidth = 1.dp.toPx(),
                    pathEffect = if (t == 0f) null else PathEffect.dashPathEffect(floatArrayOf(6f, 6f)),
                )
            }
            if (empty) return@Canvas

            val maxV = peak.toFloat()
            val n = counts.size
            val stepX = (w - padH * 2) / (n - 1)
            // Canvas is not mirrored for RTL, so time runs from the start side
            // by hand: right to left in Persian, matching the month chips and
            // everything else on the screen.
            val rtl = layoutDirection == LayoutDirection.Rtl
            val pts = (0 until n).map { i ->
                val x = padH + stepX * i
                Offset(if (rtl) w - x else x, plotBottom - (counts[i] / maxV) * (plotBottom - plotTop))
            }
            val line = smoothPath(pts)
            val fill = smoothPath(pts).apply {
                lineTo(pts.last().x, plotBottom)
                lineTo(pts.first().x, plotBottom)
                close()
            }

            val reveal = padH + (w - padH * 2) * sweep.value + 4.dp.toPx()
            clipRect(left = if (rtl) w - reveal else 0f, right = if (rtl) w else reveal) {
                drawPath(fill, color = lineColor.copy(alpha = 0.10f))
                drawPath(
                    line,
                    color = lineColor,
                    style = Stroke(width = 2.5.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round),
                )
                // A dot per reading, so the curve is visibly sampled data rather
                // than a free-drawn shape.
                if (n <= 14) {
                    pts.forEach { p ->
                        drawCircle(color = lineColor, radius = 3.5.dp.toPx(), center = p)
                        drawCircle(color = dotFill, radius = 1.8.dp.toPx(), center = p)
                    }
                }
            }
        }
        if (empty) {
            Text(
                text = emptyLabel,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(dotFill)
                    .padding(horizontal = 10.dp, vertical = 4.dp),
            )
        }
    }
}

/**
 * Builds a smooth cubic-bezier path through the given points.
 * Control points are placed at 1/3 distance between consecutive points,
 * which gives a natural curve without overshooting.
 */
private fun smoothPath(pts: List<Offset>): Path = Path().apply {
    if (pts.isEmpty()) return@apply
    moveTo(pts[0].x, pts[0].y)
    if (pts.size == 1) return@apply
    for (i in 0 until pts.size - 1) {
        val p0 = pts[i]
        val p1 = pts[i + 1]
        val dx = (p1.x - p0.x) / 3f
        cubicTo(
            p0.x + dx, p0.y,
            p1.x - dx, p1.y,
            p1.x, p1.y
        )
    }
}
