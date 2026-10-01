package xyz.sattar.javid.proqueue.feature.onboarding

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Groups
import androidx.compose.material.icons.rounded.Link
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material.icons.rounded.QrCode2
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.Sms
import androidx.compose.material.icons.rounded.TaskAlt
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import xyz.sattar.javid.proqueue.core.ui.components.AnimatedNoobatyarMark
import xyz.sattar.javid.proqueue.core.ui.components.BrandAmber
import xyz.sattar.javid.proqueue.core.ui.components.FloatingChip
import xyz.sattar.javid.proqueue.core.ui.components.IllustrationBaseSize
import xyz.sattar.javid.proqueue.core.ui.components.IllustrationDiscSize
import xyz.sattar.javid.proqueue.core.ui.components.IllustrationStage
import xyz.sattar.javid.proqueue.core.ui.components.OrbitDots
import xyz.sattar.javid.proqueue.core.ui.components.rememberReplayKey
import xyz.sattar.javid.proqueue.ui.theme.SuccessColor

/*
 * The pictures of the first-run introduction's first four pages (the last
 * page reuses the register screen's NewAccountIllustration). Built from the
 * shared pieces in core/ui/components/BrandIllustration.kt.
 */

// ── Page 1: welcome ─────────────────────────────────────────────────────────

/** The brand mark assembling itself, with the day's appointments orbiting it. */
@Composable
internal fun WelcomeIllustration(settled: Boolean) {
    val replay = rememberReplayKey(settled)
    IllustrationStage {
        OrbitDots()
        key(replay) {
            AnimatedNoobatyarMark(modifier = Modifier.size(84.dp), tint = Color.White)
        }
    }
}

// ── Page 2: booking link ────────────────────────────────────────────────────

/** A link sending out rings, with the bookings it brings in floating around. */
@Composable
internal fun BookingLinkIllustration() {
    val ringColor = MaterialTheme.colorScheme.primary
    val transition = rememberInfiniteTransition(label = "link")
    val ripple by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(2600, easing = LinearEasing)),
        label = "ripple"
    )
    val wobble by transition.animateFloat(
        initialValue = -8f,
        targetValue = 8f,
        animationSpec = infiniteRepeatable(tween(1600, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "wobble"
    )
    IllustrationStage {
        Canvas(modifier = Modifier.size(IllustrationBaseSize)) {
            val minRadius = IllustrationDiscSize.toPx() / 2
            val maxRadius = size.minDimension / 2
            repeat(2) { k ->
                val f = (ripple + k * 0.5f) % 1f
                drawCircle(
                    color = ringColor.copy(alpha = 0.5f * (1f - f)),
                    radius = minRadius + (maxRadius - minRadius) * f,
                    style = Stroke(width = 2.dp.toPx())
                )
            }
        }
        Icon(
            imageVector = Icons.Rounded.Link,
            contentDescription = null,
            tint = Color.White,
            modifier = Modifier
                .size(64.dp)
                .graphicsLayer { rotationZ = -45f + wobble }
        )
        FloatingChip(
            icon = Icons.Rounded.CalendarMonth,
            label = "شنبه ۱۰:۳۰",
            modifier = Modifier.align(Alignment.TopStart).offset(x = 4.dp, y = 22.dp)
        )
        FloatingChip(
            icon = Icons.Rounded.CheckCircle,
            label = "رزرو شد",
            iconTint = SuccessColor,
            phaseMillis = 700,
            modifier = Modifier.align(Alignment.BottomEnd).offset(x = (-2).dp, y = (-24).dp)
        )
        FloatingChip(
            icon = Icons.Rounded.QrCode2,
            phaseMillis = 1300,
            modifier = Modifier.align(Alignment.TopEnd).offset(x = (-14).dp, y = 44.dp)
        )
    }
}

// ── Page 3: reminders ───────────────────────────────────────────────────────

/** A bell that rings now and then, with the reminder it just sent. */
@Composable
internal fun ReminderIllustration() {
    val transition = rememberInfiniteTransition(label = "bell")
    val swing by transition.animateFloat(
        initialValue = 0f,
        targetValue = 0f,
        animationSpec = infiniteRepeatable(
            keyframes {
                durationMillis = 2600
                0f at 0
                16f at 140
                -13f at 290
                10f at 440
                -6f at 590
                3f at 740
                0f at 880
            }
        ),
        label = "swing"
    )
    val badge by transition.animateFloat(
        initialValue = 1f,
        targetValue = 1.35f,
        animationSpec = infiniteRepeatable(tween(650, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "badge"
    )
    IllustrationStage {
        Box(modifier = Modifier.size(70.dp), contentAlignment = Alignment.Center) {
            Icon(
                imageVector = Icons.Rounded.NotificationsActive,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier
                    .size(64.dp)
                    .graphicsLayer {
                        rotationZ = swing
                        // Swings from the bell's crown, not its middle.
                        transformOrigin = TransformOrigin(0.5f, 0.12f)
                    }
            )
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .size(14.dp)
                    .graphicsLayer {
                        scaleX = badge
                        scaleY = badge
                    }
                    .background(BrandAmber, CircleShape)
            )
        }
        FloatingChip(
            icon = Icons.Rounded.Sms,
            label = "یادآوری نوبت فردا",
            modifier = Modifier.align(Alignment.TopEnd).offset(x = 0.dp, y = 18.dp)
        )
        FloatingChip(
            icon = Icons.Rounded.Schedule,
            label = "۲ ساعت قبل",
            phaseMillis = 900,
            modifier = Modifier.align(Alignment.BottomStart).offset(x = 6.dp, y = (-22).dp)
        )
    }
}

// ── Page 4: dashboard ───────────────────────────────────────────────────────

/** Bars rising one after another, the day's numbers floating beside them. */
@Composable
internal fun StatsIllustration() {
    val clock by rememberInfiniteTransition(label = "bars").animateFloat(
        initialValue = 0f,
        targetValue = 3600f,
        animationSpec = infiniteRepeatable(tween(3600, easing = LinearEasing)),
        label = "clock"
    )
    val heights = remember { listOf(0.42f, 0.7f, 0.55f, 0.92f, 0.76f) }
    IllustrationStage {
        Canvas(modifier = Modifier.size(width = 76.dp, height = 62.dp)) {
            val n = heights.size
            val barWidth = size.width / (n * 1.7f)
            val gap = (size.width - barWidth * n) / (n - 1)
            heights.forEachIndexed { i, h ->
                val grow = FastOutSlowInEasing.transform(((clock - i * 130f) / 650f).coerceIn(0f, 1f))
                val fall = 1f - FastOutSlowInEasing.transform(((clock - 3000f - i * 50f) / 450f).coerceIn(0f, 1f))
                val barHeight = size.height * h * minOf(grow, fall)
                drawRoundRect(
                    color = Color.White,
                    topLeft = Offset(i * (barWidth + gap), size.height - barHeight),
                    size = Size(barWidth, barHeight),
                    cornerRadius = CornerRadius(barWidth / 2)
                )
            }
        }
        FloatingChip(
            icon = Icons.Rounded.TaskAlt,
            label = "۱۲ انجام‌شده",
            iconTint = SuccessColor,
            modifier = Modifier.align(Alignment.TopStart).offset(x = 2.dp, y = 26.dp)
        )
        FloatingChip(
            icon = Icons.Rounded.Groups,
            label = "۳ در صف",
            phaseMillis = 800,
            modifier = Modifier.align(Alignment.BottomEnd).offset(x = (-6).dp, y = (-22).dp)
        )
    }
}

/** Runs [block] once per time [settled] turns true, after [delayMillis]. */
@Composable
internal fun OnSettled(settled: Boolean, delayMillis: Long = 0, block: suspend () -> Unit) {
    LaunchedEffect(settled) {
        if (settled) {
            delay(delayMillis)
            block()
        }
    }
}
