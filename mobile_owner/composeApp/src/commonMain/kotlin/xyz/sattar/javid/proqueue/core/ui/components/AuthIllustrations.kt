package xyz.sattar.javid.proqueue.core.ui.components

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Login
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.LockOpen
import androidx.compose.material.icons.rounded.PersonAddAlt1
import androidx.compose.material.icons.rounded.PhoneIphone
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.Sms
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import xyz.sattar.javid.proqueue.ui.theme.SuccessColor

/*
 * The headers of the sign-in screens — login, register, the password-reset
 * code and the new password — in the same style as the first-run
 * introduction (see BrandIllustration.kt). Shown small on these screens, so
 * the floating cards carry icons only, no text.
 *
 * Wrap in [BrandIllustration] to size them.
 */

/** Size of the picture at the top of each sign-in screen. */
val AuthHeaderIllustrationSize = 150.dp

// ── Login ───────────────────────────────────────────────────────────────────

/**
 * The "enter" icon held still on the disc — the motion is all around it: the
 * orbiting dots, and the two things the form asks for, phone and password,
 * floating beside it.
 */
@Composable
fun LoginIllustration() {
    IllustrationStage {
        OrbitDots()
        Icon(
            imageVector = Icons.AutoMirrored.Rounded.Login,
            contentDescription = null,
            tint = Color.White,
            modifier = Modifier.size(64.dp)
        )
        FloatingChip(
            icon = Icons.Rounded.PhoneIphone,
            modifier = Modifier.align(Alignment.TopStart).offset(x = 22.dp, y = 30.dp)
        )
        FloatingChip(
            icon = Icons.Rounded.Lock,
            phaseMillis = 900,
            modifier = Modifier.align(Alignment.BottomEnd).offset(x = (-22).dp, y = (-30).dp)
        )
    }
}

// ── Register ────────────────────────────────────────────────────────────────

/**
 * A ring drawing itself around "new account", then the check landing on it.
 * Plays once each time [settled] turns true — always true on the register
 * screen; the introduction's last page passes whether it is in view.
 */
@Composable
fun NewAccountIllustration(settled: Boolean = true) {
    val ringColor = MaterialTheme.colorScheme.primary
    val ring = remember { Animatable(0f) }
    val pop = remember { Animatable(0f) }
    val check = remember { Animatable(0f) }
    LaunchedEffect(settled) {
        if (!settled) return@LaunchedEffect
        ring.snapTo(0f)
        pop.snapTo(0f)
        check.snapTo(0f)
        pop.animateTo(1f, spring(dampingRatio = 0.45f, stiffness = Spring.StiffnessLow))
        ring.animateTo(1f, tween(900, easing = FastOutSlowInEasing))
        check.animateTo(1f, spring(dampingRatio = 0.4f, stiffness = Spring.StiffnessMediumLow))
    }
    IllustrationStage {
        Canvas(modifier = Modifier.size(IllustrationDiscSize + 22.dp)) {
            drawArc(
                color = ringColor,
                startAngle = -90f,
                sweepAngle = 360f * ring.value,
                useCenter = false,
                style = Stroke(width = 4.dp.toPx(), cap = StrokeCap.Round)
            )
        }
        Icon(
            imageVector = Icons.Rounded.PersonAddAlt1,
            contentDescription = null,
            tint = Color.White,
            modifier = Modifier
                .size(62.dp)
                .graphicsLayer {
                    scaleX = pop.value
                    scaleY = pop.value
                }
        )
        SuccessBadge(
            scale = check.value,
            modifier = Modifier.offset(x = 50.dp, y = 50.dp)
        )
    }
}

// ── Password reset: the code ────────────────────────────────────────────────

/**
 * A message arriving: the bubble gives a small hop, and the code under it
 * fills in digit by digit, then starts over.
 */
@Composable
fun VerifyCodeIllustration() {
    val transition = rememberInfiniteTransition(label = "code")
    val clock by transition.animateFloat(
        initialValue = 0f,
        targetValue = 3000f,
        animationSpec = infiniteRepeatable(tween(3000, easing = LinearEasing)),
        label = "clock"
    )
    val hop by transition.animateFloat(
        initialValue = 0f,
        targetValue = 0f,
        animationSpec = infiniteRepeatable(
            keyframes {
                durationMillis = 3000
                0f at 0
                -10f at 160 using FastOutSlowInEasing
                0f at 380
                0f at 3000
            }
        ),
        label = "hop"
    )
    val dots = 4
    IllustrationStage {
        Icon(
            imageVector = Icons.Rounded.Sms,
            contentDescription = null,
            tint = Color.White,
            modifier = Modifier
                .size(58.dp)
                .offset(y = (-10).dp)
                .graphicsLayer { translationY = hop.dp.toPx() }
        )
        // The code, sitting on the lower edge of the disc.
        Surface(
            shape = RoundedCornerShape(12.dp),
            color = Color.White,
            shadowElevation = 4.dp,
            modifier = Modifier.offset(y = 52.dp)
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(7.dp)
            ) {
                repeat(dots) { i ->
                    val filled = clock > 450f + i * 330f && clock < 2700f
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .background(
                                if (filled) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.primary.copy(alpha = 0.18f),
                                CircleShape
                            )
                    )
                }
            }
        }
        FloatingChip(
            icon = Icons.Rounded.Schedule,
            phaseMillis = 600,
            modifier = Modifier.align(Alignment.TopEnd).offset(x = (-22).dp, y = 30.dp)
        )
    }
}

// ── Password reset: the new password ────────────────────────────────────────

/**
 * The lock closing on the new password — open, then shut with a bounce and a
 * check — inside a ring that keeps turning, the "reset" in reset-password.
 */
@Composable
fun NewPasswordIllustration() {
    val ringColor = MaterialTheme.colorScheme.primary
    val spin by rememberInfiniteTransition(label = "reset").animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(5200, easing = LinearEasing), RepeatMode.Restart),
        label = "spin"
    )
    var locked by remember { mutableStateOf(false) }
    val bounce = remember { Animatable(1f) }
    val check = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        delay(700)
        locked = true
        bounce.snapTo(1.22f)
        bounce.animateTo(1f, spring(dampingRatio = 0.35f, stiffness = Spring.StiffnessMediumLow))
        check.animateTo(1f, spring(dampingRatio = 0.4f, stiffness = Spring.StiffnessMediumLow))
    }
    IllustrationStage {
        Canvas(
            modifier = Modifier
                .size(IllustrationDiscSize + 24.dp)
                .graphicsLayer { rotationZ = spin }
        ) {
            val stroke = Stroke(width = 4.dp.toPx(), cap = StrokeCap.Round)
            drawArc(ringColor, startAngle = 0f, sweepAngle = 70f, useCenter = false, style = stroke)
            drawArc(ringColor.copy(alpha = 0.45f), startAngle = 180f, sweepAngle = 70f, useCenter = false, style = stroke)
        }
        Crossfade(targetState = locked, animationSpec = tween(180), label = "lock") { isLocked ->
            Icon(
                imageVector = if (isLocked) Icons.Rounded.Lock else Icons.Rounded.LockOpen,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier
                    .size(60.dp)
                    .graphicsLayer {
                        scaleX = bounce.value
                        scaleY = bounce.value
                        transformOrigin = TransformOrigin(0.5f, 0.6f)
                    }
            )
        }
        SuccessBadge(
            scale = check.value,
            modifier = Modifier.offset(x = 50.dp, y = 50.dp)
        )
    }
}

/** The green check that lands on a picture once its action is done. */
@Composable
private fun SuccessBadge(scale: Float, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .size(36.dp)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .background(SuccessColor, CircleShape),
        contentAlignment = Alignment.Center
    ) {
        Icon(Icons.Rounded.Check, contentDescription = null, tint = Color.White, modifier = Modifier.size(22.dp))
    }
}
