package xyz.sattar.javid.proqueue.core.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathMeasure
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sign
import kotlin.math.sin

/**
 * The Noobatyar mark: a checkmark inside an open ring (an appointment
 * confirmed, time coming round), with the single amber dot that is the brand's
 * one accent colour.
 *
 * Drawn rather than shipped as a drawable so it inherits [tint] from whatever
 * surface it sits on — the same reason the Django admin inlines it as SVG with
 * `currentColor` (backend/templates/admin/base_site.html). The geometry below
 * is that same artwork, authored in a 512x512 box and scaled to fit.
 *
 * The amber dot is deliberately NOT tinted: per docs/BRAND_GUIDE_FOR_AI.md it
 * is the one fixed accent and must not drift with the surrounding colour.
 */
@Composable
fun NoobatyarMark(
    modifier: Modifier = Modifier,
    tint: Color = Color.White,
    accent: Color = Color(0xFFF59E0B)
) {
    Canvas(modifier = modifier) {
        drawMark(tint = tint, accent = accent, pose = MarkPose.Final)
    }
}

/**
 * The mark assembling itself, played once when it enters composition:
 *
 * 1. Only the ring: no dot, no check.
 * 2. The dot pops into the ring's top opening, the gap between the two tips
 *    of the ن, and swings tick-tock, tick-tock like an alarm-clock hammer.
 * 3. It winds up, rises, and strikes down onto the ring.
 * 4. On the strike the ring gives a small jolt and the check draws itself in.
 *
 * Ends on exactly [NoobatyarMark]'s artwork, so it can stand in for it.
 */
@Composable
fun AnimatedNoobatyarMark(
    modifier: Modifier = Modifier,
    tint: Color = Color.White,
    accent: Color = Color(0xFFF59E0B)
) {
    val clock = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        clock.animateTo(
            targetValue = TOTAL_MS.toFloat(),
            animationSpec = tween(durationMillis = TOTAL_MS, easing = LinearEasing)
        )
    }
    Canvas(modifier = modifier) {
        drawMark(tint = tint, accent = accent, pose = poseAt(clock.value))
    }
}

/**
 * A loader built from the mark: tick-tock while [waiting], then a strike and
 * the check once it stops waiting, then [onFinished].
 *
 * 1. While [waiting] is true, the dot pops into the ring's opening (unless
 *    [dotAlreadyShown]) and swings tick-tock on a loop, for as long as the
 *    wait lasts.
 * 2. When [waiting] turns false, it eases out of the swing, winds up, strikes
 *    the ring, and the check draws in.
 * 3. Then [onFinished] fires. The caller hides the loader there, not the
 *    moment the data arrives, so the check is always seen.
 */
@Composable
fun WaitingNoobatyarMark(
    waiting: Boolean,
    modifier: Modifier = Modifier,
    tint: Color = Color.White,
    accent: Color = Color(0xFFF59E0B),
    dotAlreadyShown: Boolean = false,
    onFinished: () -> Unit = {}
) {
    val pop = remember { Animatable(if (dotAlreadyShown) 1f else 0f) }
    // A pulled dot is already leaning to one side (PullingNoobatyarMark), so
    // its first swing starts from that lean, a quarter of the way through a
    // cycle, instead of jumping back to centre.
    val swing = remember { Animatable(if (dotAlreadyShown) 0.25f else 0f) }
    val finish = remember { Animatable(0f) }
    var finishing by remember { mutableStateOf(false) }
    var angleAtFinish by remember { mutableStateOf(0f) }
    val currentOnFinished by rememberUpdatedState(onFinished)

    LaunchedEffect(waiting) {
        if (waiting) {
            finishing = false
            finish.snapTo(0f)
            if (pop.value < 1f) pop.animateTo(1f, tween(DOT_IN, easing = LinearEasing))
            while (true) {
                val left = ((1f - swing.value) * TICK_PERIOD_MS).toInt().coerceAtLeast(1)
                swing.animateTo(1f, tween(left, easing = LinearEasing))
                swing.snapTo(0f)
            }
        } else {
            angleAtFinish = tickAngle(swing.value)
            finishing = true
            val duration = RISE + STRIKE + CHECK
            finish.animateTo(duration.toFloat(), tween(duration, easing = LinearEasing))
            currentOnFinished()
        }
    }

    Canvas(modifier = modifier) {
        val pose = if (finishing) {
            // poseAt past the swing: rise, strike, jolt, check. The swing's
            // last angle eases back to centre over the wind-up's dip instead
            // of snapping.
            val base = poseAt(STRIKE_START_MS - RISE + finish.value)
            val settle = (finish.value / 140f).coerceIn(0f, 1f)
            base.copy(dotAngle = angleAtFinish * (1f - settle))
        } else {
            MarkPose(
                dotScale = if (pop.value >= 1f) 1f else easeOutBack(pop.value),
                dotAngle = tickAngle(swing.value),
                dotLift = 0f,
                dotSquash = 1f,
                ringScale = 1f,
                checkProgress = 0f
            )
        }
        drawMark(tint = tint, accent = accent, pose = pose)
    }
}

/** One tick-tock cycle, [phase] 0..1: snaps to each side and holds there. */
private fun tickAngle(phase: Float): Float {
    val s = sin(phase * 2f * PI.toFloat())
    return SWING_DEG * sign(s) * abs(s).pow(0.45f)
}

/**
 * The mark following a pull gesture, [progress] 0..1 towards the refresh
 * threshold: the dot grows into the ring's opening and leans a little with
 * the pull. Once released, [WaitingNoobatyarMark] (dotAlreadyShown) carries on
 * from the full-size dot.
 */
@Composable
fun PullingNoobatyarMark(
    progress: Float,
    modifier: Modifier = Modifier,
    tint: Color = Color.White,
    accent: Color = Color(0xFFF59E0B)
) {
    val p = progress.coerceIn(0f, 1f)
    val pose = MarkPose(
        dotScale = (p / 0.6f).coerceIn(0f, 1f),
        dotAngle = SWING_DEG * ((p - 0.6f) / 0.4f).coerceIn(0f, 1f),
        dotLift = 0f,
        dotSquash = 1f,
        ringScale = 1f,
        checkProgress = 0f
    )
    Canvas(modifier = modifier) {
        drawMark(tint = tint, accent = accent, pose = pose)
    }
}

/** Every animated quantity at one instant. [Final] is the static logo. */
private data class MarkPose(
    /** 0 = no dot, 1 = full size. */
    val dotScale: Float,
    /** Swing around the ring centre, in degrees; 0 is straight up. */
    val dotAngle: Float,
    /** Upward lift from the dot's resting spot, in 512-box units. */
    val dotLift: Float,
    /** Vertical squash on impact: 1 = round. */
    val dotSquash: Float,
    /** The ring's jolt when struck: 1 = rest. */
    val ringScale: Float,
    /** How much of the check is drawn, 0..1. */
    val checkProgress: Float
) {
    companion object {
        val Final = MarkPose(1f, 0f, 0f, 1f, 1f, 1f)
    }
}

// ── Timeline, in milliseconds ─────────────────────────────────────────────
private const val RING_ALONE = 250          // ring on its own
private const val DOT_IN = 300              // dot pops in
private const val SWING = 1_500             // tick-tock, three full swings
private const val RISE = 420                // wind up and lift
private const val STRIKE = 130              // drop onto the ring
private const val CHECK = 520               // check draws in
private const val TOTAL_MS = RING_ALONE + DOT_IN + SWING + RISE + STRIKE + CHECK

private const val TICK_PERIOD_MS = 1_000    // one left-right tick-tock while waiting
private const val STRIKE_START_MS = (RING_ALONE + DOT_IN + SWING + RISE).toFloat()

private const val SWINGS = 3                // full left-right-left cycles
private const val SWING_DEG = 11f           // stays inside the ring's top gap
private const val LIFT_UNITS = 78f          // how high the hammer rises

private fun poseAt(t: Float): MarkPose {
    val dotInStart = RING_ALONE.toFloat()
    val swingStart = dotInStart + DOT_IN
    val riseStart = swingStart + SWING
    val strikeStart = riseStart + RISE
    val impact = strikeStart + STRIKE

    val dotScale = when {
        t < dotInStart -> 0f
        t < swingStart -> easeOutBack((t - dotInStart) / DOT_IN)
        else -> 1f
    }

    // Tick-tock: a sine sharpened towards a square wave, so the dot snaps to
    // each side and pauses there like a clock hammer instead of drifting. The
    // swing fades out over its last cycle so it settles before winding up.
    val dotAngle = if (t in swingStart..riseStart) {
        val p = (t - swingStart) / SWING
        val s = sin(p * SWINGS * 2f * PI.toFloat())
        val sharp = sign(s) * abs(s).pow(0.45f)
        val fade = if (p > 1f - 1f / SWINGS) (1f - p) * SWINGS else 1f
        SWING_DEG * sharp * fade
    } else 0f

    val dotLift = when {
        t < riseStart -> 0f
        // A small dip first (wind-up), then up with ease-out.
        t < strikeStart -> {
            val p = (t - riseStart) / RISE
            if (p < 0.2f) -10f * sin(p / 0.2f * PI.toFloat())
            else LIFT_UNITS * easeOutCubic((p - 0.2f) / 0.8f)
        }
        // Accelerating fall: a strike, not a glide.
        t < impact -> LIFT_UNITS * (1f - easeInCubic((t - strikeStart) / STRIKE))
        else -> 0f
    }

    // After the strike: the dot squashes and springs back, the ring jolts.
    val sinceImpact = t - impact
    val dotSquash = if (sinceImpact in 0f..220f) 1f - 0.28f * decayingBounce(sinceImpact / 220f) else 1f
    val ringScale = if (sinceImpact in 0f..320f) 1f + 0.06f * decayingBounce(sinceImpact / 320f) else 1f
    val checkProgress = when {
        sinceImpact <= 0f -> 0f
        sinceImpact < CHECK -> easeOutCubic(sinceImpact / CHECK)
        else -> 1f
    }

    return MarkPose(dotScale, dotAngle, dotLift, dotSquash, ringScale, checkProgress)
}

private fun easeOutCubic(x: Float) = 1f - (1f - x).pow(3)
private fun easeInCubic(x: Float) = x * x * x
private fun easeOutBack(x: Float): Float {
    val c1 = 1.70158f
    val c3 = c1 + 1f
    return 1f + c3 * (x - 1f).pow(3) + c1 * (x - 1f).pow(2)
}
/** 1 at x = 0, oscillating down to 0 at x = 1. */
private fun decayingBounce(x: Float) = (1f - x) * cos(x * 2.5f * PI.toFloat())

private fun DrawScope.drawMark(tint: Color, accent: Color, pose: MarkPose) {
    // Uniform scale from the 512x512 authoring box, so the mark keeps its
    // proportions in whatever square (or rectangle) it is given.
    val s = minOf(size.width, size.height) / 512f
    val dx = (size.width - 512f * s) / 2f
    val dy = (size.height - 512f * s) / 2f
    fun x(v: Float) = dx + v * s
    fun y(v: Float) = dy + v * s

    val strokeWidth = 36f * s
    val centre = Offset(x(256f), y(256f))

    scale(pose.ringScale, pivot = centre) {
        // Open ring. The gap sits at the top, where the dot goes — start and
        // sweep are the arc from the original path's endpoints (337.64,156.96)
        // to (174.36,156.96) around centre (256,256), radius 146.
        drawArc(
            color = tint,
            startAngle = -50.5f,
            sweepAngle = 281f,
            useCenter = false,
            topLeft = Offset(x(110f), y(110f)),
            size = Size(292f * s, 292f * s),
            style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
        )

        // Checkmark, drawn along its own length as it comes in.
        if (pose.checkProgress > 0f) {
            val check = Path().apply {
                moveTo(x(191f), y(276f))
                lineTo(x(234f), y(323f))
                lineTo(x(327f), y(221f))
            }
            val drawn = if (pose.checkProgress >= 1f) check else {
                val measure = PathMeasure().apply { setPath(check, false) }
                Path().also { measure.getSegment(0f, measure.length * pose.checkProgress, it, true) }
            }
            drawPath(
                path = drawn,
                color = tint,
                style = Stroke(width = strokeWidth, cap = StrokeCap.Round, join = StrokeJoin.Round)
            )
        }
    }

    // The dot: resting at (256,122), 134 units above the centre. It swings on
    // that radius, so it tracks the ring's opening instead of sliding flat.
    if (pose.dotScale > 0f) {
        val a = pose.dotAngle * PI.toFloat() / 180f
        val cx = 256f + 134f * sin(a)
        val cy = 256f - 134f * cos(a) - pose.dotLift
        val r = 28f * s * pose.dotScale
        val dot = Offset(x(cx), y(cy))
        scale(scaleX = 2f - pose.dotSquash, scaleY = pose.dotSquash, pivot = Offset(dot.x, dot.y + r)) {
            drawCircle(color = accent, radius = r, center = dot)
        }
    }
}
