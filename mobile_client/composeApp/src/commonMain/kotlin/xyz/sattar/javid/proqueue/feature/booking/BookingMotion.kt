package xyz.sattar.javid.proqueue.feature.booking

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay

/** Set once at the top of the booking screen from the OS setting. */
internal val LocalReducedMotion = staticCompositionLocalOf { false }

/** The selection spring: a little overshoot, so a pick feels like it landed. */
internal fun <T> selectSpring(reduced: Boolean): AnimationSpec<T> =
    if (reduced) snap() else spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium)

/** Shrinks a control while a finger is on it: the visual half of a haptic press. */
@Composable
internal fun Modifier.pressScale(interaction: InteractionSource, pressedScale: Float = 0.96f): Modifier {
    val reduced = LocalReducedMotion.current
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(
        if (pressed && !reduced) pressedScale else 1f,
        spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessHigh),
        label = "pressScale",
    )
    return graphicsLayer { scaleX = scale; scaleY = scale }
}

/**
 * Fades and lifts content in when it first composes, [index] steps after its
 * siblings, so a list or a summary card assembles instead of popping in.
 */
@Composable
internal fun Modifier.enterStagger(index: Int, key: Any? = Unit, stepMillis: Int = 40): Modifier {
    val reduced = LocalReducedMotion.current
    val progress = remember(key) { Animatable(if (reduced) 1f else 0f) }
    LaunchedEffect(key) {
        if (reduced) return@LaunchedEffect
        delay((index * stepMillis).coerceAtMost(360).toLong())
        progress.animateTo(1f, tween(280, easing = FastOutSlowInEasing))
    }
    val lift = with(LocalDensity.current) { 12.dp.toPx() }
    return graphicsLayer {
        alpha = progress.value
        translationY = (1f - progress.value) * lift
    }
}

/**
 * Forward slides the next step in from the reading end: from the left in
 * RTL, from the right in LTR. Backward mirrors it. Reduced motion keeps only
 * a short crossfade.
 */
internal fun AnimatedContentTransitionScope<BookingStep>.stepTransition(
    layoutDirection: LayoutDirection,
    reduced: Boolean,
): ContentTransform {
    if (reduced) return fadeIn(tween(150)) togetherWith fadeOut(tween(150))
    val forward = isForward(initialState, targetState)
    // +1 enters from the right edge. RTL reads right to left, so the next
    // screen comes in from the left.
    val rtl = if (layoutDirection == LayoutDirection.Rtl) -1 else 1
    val sign = if (forward) rtl else -rtl
    val slide = spring<androidx.compose.ui.unit.IntOffset>(
        dampingRatio = Spring.DampingRatioNoBouncy,
        stiffness = Spring.StiffnessMediumLow,
    )
    return (slideInHorizontally(slide) { w -> sign * w / 3 } + fadeIn(tween(220, delayMillis = 60))) togetherWith
        (slideOutHorizontally(slide) { w -> -sign * w / 4 } + fadeOut(tween(140)))
}
