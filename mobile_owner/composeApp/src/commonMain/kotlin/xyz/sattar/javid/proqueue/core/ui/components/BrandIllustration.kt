package xyz.sattar.javid.proqueue.core.ui.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.StartOffset
import androidx.compose.animation.core.StartOffsetType
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import xyz.sattar.javid.proqueue.ui.theme.PrimaryPurple
import xyz.sattar.javid.proqueue.ui.theme.PrimaryPurpleDark
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/*
 * Building blocks for the app's animated brand pictures: the first-run
 * introduction (feature/onboarding) and the headers of the sign-in screens
 * (AuthIllustrations.kt).
 *
 * Every picture sits on the same stage — a soft violet glow behind the
 * brand-gradient disc, per docs/BRAND_GUIDE_FOR_AI.md ("soft purple glow",
 * one amber accent, calm rather than busy) — with an icon moving on the disc
 * and, where there is room, small cards floating beside it. All drawn in
 * Compose, so they follow the theme and need no image assets.
 *
 * Pictures are authored on an [IllustrationBaseSize] stage; [BrandIllustration]
 * scales one to whatever size a screen has room for.
 */

internal val IllustrationBaseSize = 240.dp
internal val IllustrationDiscSize = 136.dp
internal val BrandIllustrationGradient = Brush.linearGradient(
    listOf(Color(0xFFA78BFA), PrimaryPurple, PrimaryPurpleDark)
)
internal val BrandAmber = Color(0xFFF59E0B)

/** Shows a picture authored at [IllustrationBaseSize], scaled to [size]. */
@Composable
fun BrandIllustration(
    size: Dp,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    Box(modifier = modifier.size(size), contentAlignment = Alignment.Center) {
        Box(
            modifier = Modifier
                .requiredSize(IllustrationBaseSize)
                .graphicsLayer {
                    val s = size / IllustrationBaseSize
                    scaleX = s
                    scaleY = s
                },
            contentAlignment = Alignment.Center
        ) {
            content()
        }
    }
}

/** The shared backdrop: glow, disc, and [content] layered on top. */
@Composable
internal fun IllustrationStage(content: @Composable BoxScope.() -> Unit) {
    val glowColor = MaterialTheme.colorScheme.primary
    val breathe by rememberInfiniteTransition(label = "stage").animateFloat(
        initialValue = 0.94f,
        targetValue = 1.04f,
        animationSpec = infiniteRepeatable(tween(2400, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "breathe"
    )
    Box(modifier = Modifier.size(IllustrationBaseSize), contentAlignment = Alignment.Center) {
        Box(
            modifier = Modifier
                .size(IllustrationBaseSize)
                .graphicsLayer {
                    scaleX = breathe
                    scaleY = breathe
                }
                .background(
                    Brush.radialGradient(listOf(glowColor.copy(alpha = 0.30f), Color.Transparent)),
                    CircleShape
                )
        )
        Box(
            modifier = Modifier
                .size(IllustrationDiscSize)
                .shadow(18.dp, CircleShape, ambientColor = PrimaryPurple, spotColor = PrimaryPurple)
                .background(BrandIllustrationGradient, CircleShape)
        )
        content()
    }
}

/**
 * A small card floating beside the disc, bobbing on its own phase. Leave
 * [label] off where the picture is shown small — scaled-down text turns
 * unreadable well before an icon does.
 */
@Composable
internal fun FloatingChip(
    icon: ImageVector,
    modifier: Modifier = Modifier,
    label: String? = null,
    iconTint: Color = MaterialTheme.colorScheme.primary,
    phaseMillis: Int = 0
) {
    val bob by rememberInfiniteTransition(label = "chip").animateFloat(
        initialValue = -5f,
        targetValue = 5f,
        animationSpec = infiniteRepeatable(
            tween(1900, easing = FastOutSlowInEasing),
            RepeatMode.Reverse,
            // Fast-forwarded, not delayed: each chip starts mid-bob rather
            // than standing still until its turn.
            initialStartOffset = StartOffset(phaseMillis, StartOffsetType.FastForward)
        ),
        label = "bob"
    )
    Surface(
        modifier = modifier.graphicsLayer { translationY = bob.dp.toPx() },
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)),
        shadowElevation = 6.dp
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(icon, contentDescription = null, tint = iconTint, modifier = Modifier.size(18.dp))
            if (label != null) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
        }
    }
}

/** Dots circling the disc slowly — the day's appointments, one amber. */
@Composable
internal fun OrbitDots(modifier: Modifier = Modifier) {
    val dotColor = MaterialTheme.colorScheme.primary
    val angle by rememberInfiniteTransition(label = "orbit").animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(16000, easing = LinearEasing)),
        label = "angle"
    )
    Canvas(modifier = modifier.size(IllustrationBaseSize)) {
        val radius = size.minDimension / 2 - 14.dp.toPx()
        val count = 9
        for (i in 0 until count) {
            val a = (angle + i * 360f / count) * PI.toFloat() / 180f
            val accent = i == 0
            drawCircle(
                color = if (accent) BrandAmber else dotColor.copy(alpha = 0.25f + 0.25f * (i % 3)),
                radius = (if (accent) 6f else 3.5f).dp.toPx(),
                center = Offset(center.x + radius * cos(a), center.y + radius * sin(a))
            )
        }
    }
}

/**
 * Counts up each time [settled] turns true, so a `key()` on it replays a
 * play-once animation whenever its page comes back into view.
 */
@Composable
internal fun rememberReplayKey(settled: Boolean): Int {
    var replay by remember { mutableIntStateOf(0) }
    LaunchedEffect(settled) { if (settled) replay++ }
    return replay
}
