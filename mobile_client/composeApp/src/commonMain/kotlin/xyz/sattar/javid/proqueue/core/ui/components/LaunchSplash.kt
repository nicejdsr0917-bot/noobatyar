package xyz.sattar.javid.proqueue.core.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.graphics.luminance
import kotlinx.coroutines.delay
import xyz.sattar.javid.proqueue.ui.theme.SystemAppearance

/**
 * The launch screen, the same boot moment as the web panel's loader
 * (wasmJsMain/resources/index.html, #compose-loading): on near-black, the
 * mark in violet with a faint ring a bright band runs around, the amber dot
 * ticking in the ring's opening; then the dot strikes, the check draws in,
 * and the screen fades to the app underneath.
 *
 * It sits over the app as it starts, so the first screen loads behind it.
 * Plays once per launch (rememberSaveable: not again on rotation).
 */
@Composable
fun LaunchSplash() {
    var visible by rememberSaveable { mutableStateOf(true) }
    var waiting by rememberSaveable { mutableStateOf(true) }
    LaunchedEffect(Unit) {
        delay(SPLASH_MIN_MS)
        waiting = false
    }

    // Light status-bar icons over the dark splash, then back to what the
    // theme's background needs. Stays composed after the splash hides, so
    // it keeps agreeing with AppTheme's own SystemAppearance call.
    SystemAppearance(if (visible) false else MaterialTheme.colorScheme.background.luminance() > 0.5f)

    AnimatedVisibility(visible = visible, enter = EnterTransition.None, exit = fadeOut(tween(250))) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(SplashBackground)
                // Swallow taps so nothing underneath reacts while it shows.
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {},
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(20.dp)) {
                Box(modifier = Modifier.size(MARK_SIZE), contentAlignment = Alignment.Center) {
                    Glow()
                    // The ring is faint while loading and brightens for the check.
                    val trackAlpha by animateFloatAsState(if (waiting) 0.3f else 1f, tween(250), label = "track")
                    WaitingNoobatyarMark(
                        waiting = waiting,
                        modifier = Modifier.fillMaxSize(),
                        tint = SplashViolet.copy(alpha = trackAlpha),
                        onFinished = { visible = false }
                    )
                    val bandAlpha by animateFloatAsState(if (waiting) 1f else 0f, tween(200), label = "band")
                    if (bandAlpha > 0f) RingBand(alpha = bandAlpha)
                }
                Text(
                    text = "نوبت‌یار",
                    color = SplashText,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.titleLarge
                )
            }
        }
    }
}

/** Soft violet light behind the mark, centred on the ring. */
@Composable
private fun Glow() {
    Canvas(modifier = Modifier.fillMaxSize()) {
        val centre = Offset(size.width / 2f, size.height / 2f)
        drawCircle(
            brush = Brush.radialGradient(
                0f to SplashViolet.copy(alpha = 0.25f),
                0.7f to Color.Transparent,
                center = centre,
                radius = size.minDimension * 0.7f
            ),
            radius = size.minDimension * 0.7f,
            center = centre
        )
    }
}

/**
 * The bright band travelling round the ring. Drawn as the ring's own arc
 * (same 512-box geometry as NoobatyarMark's drawMark) stroked with a turning
 * sweep gradient, so the light only ever shows on the ring, never in its gap.
 */
@Composable
private fun RingBand(alpha: Float) {
    val turn by rememberInfiniteTransition(label = "band").animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(1150, easing = LinearEasing), RepeatMode.Restart),
        label = "turn"
    )
    Canvas(modifier = Modifier.fillMaxSize()) {
        val s = minOf(size.width, size.height) / 512f
        val dx = (size.width - 512f * s) / 2f
        val dy = (size.height - 512f * s) / 2f
        val centre = Offset(dx + 256f * s, dy + 256f * s)
        val light = Color(0xFFEDE9FE)
        val brush = Brush.sweepGradient(
            0f to light.copy(alpha = 0f),
            250f / 360f to light.copy(alpha = 0f),
            335f / 360f to light.copy(alpha = 0.85f * alpha),
            355f / 360f to Color.White.copy(alpha = alpha),
            1f to light.copy(alpha = 0f),
            center = centre
        )
        // The gradient turns; the arc it is drawn on stays put.
        rotate(turn, pivot = centre) {
            drawArc(
                brush = brush,
                startAngle = -50.5f - turn,
                sweepAngle = 281f,
                useCenter = false,
                topLeft = Offset(dx + 110f * s, dy + 110f * s),
                size = Size(292f * s, 292f * s),
                style = Stroke(width = 36f * s, cap = StrokeCap.Round)
            )
        }
    }
}

/** Long enough for the dot to pop in and tick-tock once, as on the web. */
private const val SPLASH_MIN_MS = 1_600L

/** The ring is 292 of the 512-unit box: ~71dp, as on the web loader. */
private val MARK_SIZE = 124.dp

private val SplashBackground = Color(0xFF0F0F0F)
private val SplashViolet = Color(0xFFA78BFA)
private val SplashText = Color(0xFFEDE9FE)
