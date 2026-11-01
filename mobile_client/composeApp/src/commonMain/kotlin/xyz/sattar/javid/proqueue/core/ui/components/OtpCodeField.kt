package xyz.sattar.javid.proqueue.core.ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import xyz.sattar.javid.proqueue.core.utils.toEnglishDigits
import xyz.sattar.javid.proqueue.core.utils.toPersianDigits
import xyz.sattar.javid.proqueue.feature.booking.LocalReducedMotion

/**
 * One box per digit, like the owner app's code field, with motion:
 * the active box lifts and shows a blinking caret, each digit drops in with a
 * spring, a wrong code (a full code cleared by the caller) shakes the row, and
 * a complete code ripples a small wave across the boxes.
 *
 * One hidden text field sits over the whole row, so typing, deleting, pasting
 * and SMS autofill all work as on a single field.
 */
@Composable
fun OtpCodeField(
    code: String,
    onCodeChange: (String) -> Unit,
    length: Int,
    modifier: Modifier = Modifier,
    focusRequester: FocusRequester = remember { FocusRequester() },
    isError: Boolean = false,
    onDone: () -> Unit = {},
) {
    val reduced = LocalReducedMotion.current
    var focused by remember { mutableStateOf(false) }
    val density = LocalDensity.current

    // A full code that comes back empty means the server rejected it.
    var previous by remember { mutableStateOf(code) }
    val shake = remember { Animatable(0f) }
    var rejected by remember { mutableStateOf(false) }
    LaunchedEffect(code) {
        val wasFull = previous.length == length
        previous = code
        if (wasFull && code.isEmpty()) {
            rejected = true
            if (!reduced) {
                shake.animateTo(0f, keyframes {
                    durationMillis = 420
                    -14f at 60; 12f at 120; -9f at 180; 7f at 240; -4f at 300; 2f at 360
                })
            }
            delay(700)
            rejected = false
        } else if (code.isNotEmpty()) {
            rejected = false
        }
    }
    val showError = isError || rejected

    // The completion wave: each box bumps a little, one after another.
    val complete = code.length == length
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
        Box(
            modifier
                .fillMaxWidth()
                .graphicsLayer { translationX = shake.value * density.density },
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                repeat(length) { index ->
                    OtpBox(
                        digit = code.getOrNull(index),
                        active = focused && (index == code.length || (complete && index == length - 1)),
                        error = showError,
                        waveDelay = if (complete) index * 45 else -1,
                        reduced = reduced,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
            BasicTextField(
                value = TextFieldValue(code, selection = TextRange(code.length)),
                onValueChange = { v ->
                    val digits = v.text.toEnglishDigits().filter(Char::isDigit).take(length)
                    if (digits != code) onCodeChange(digits)
                },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword, imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { onDone() }),
                singleLine = true,
                cursorBrush = SolidColor(Color.Transparent),
                textStyle = MaterialTheme.typography.bodyLarge.copy(color = Color.Transparent),
                interactionSource = remember { MutableInteractionSource() },
                modifier = Modifier
                    .matchParentSize()
                    .alpha(0f)
                    .focusRequester(focusRequester)
                    .onFocusChanged { focused = it.isFocused },
            )
        }
    }
}

@Composable
private fun OtpBox(
    digit: Char?,
    active: Boolean,
    error: Boolean,
    waveDelay: Int,
    reduced: Boolean,
    modifier: Modifier,
) {
    val scheme = MaterialTheme.colorScheme
    val filled = digit != null
    val shape = RoundedCornerShape(16.dp)

    val borderColor by animateColorAsState(
        when {
            error -> scheme.error
            active -> scheme.primary
            filled -> scheme.primary.copy(alpha = 0.45f)
            else -> scheme.outlineVariant
        },
        tween(220), label = "otpBorder",
    )
    val fill by animateColorAsState(
        when {
            error -> scheme.errorContainer.copy(alpha = 0.55f)
            active -> scheme.primary.copy(alpha = 0.08f)
            filled -> scheme.primaryContainer.copy(alpha = 0.45f)
            else -> scheme.surfaceVariant.copy(alpha = 0.45f)
        },
        tween(220), label = "otpFill",
    )
    val borderWidth by animateDpAsState(if (active || error) 2.dp else 1.dp, tween(180), label = "otpBorderW")
    val lift by animateFloatAsState(
        if (active && !reduced) 1.06f else 1f,
        spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium),
        label = "otpLift",
    )
    val elevation by animateDpAsState(if (active) 6.dp else 0.dp, tween(200), label = "otpElev")

    val wave = remember { Animatable(1f) }
    LaunchedEffect(waveDelay) {
        if (waveDelay < 0 || reduced) return@LaunchedEffect
        delay(waveDelay.toLong())
        wave.animateTo(1.12f, tween(110))
        wave.animateTo(1f, spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium))
    }

    Box(
        modifier
            .aspectRatio(0.86f)
            .graphicsLayer {
                val s = lift * wave.value
                scaleX = s; scaleY = s
            }
            .shadow(elevation, shape, ambientColor = scheme.primary, spotColor = scheme.primary)
            // Opaque, or the coloured shadow shows through the tinted fill.
            .background(fill.compositeOver(scheme.surface), shape)
            .border(borderWidth, borderColor, shape),
        contentAlignment = Alignment.Center,
    ) {
        AnimatedContent(
            targetState = digit,
            transitionSpec = {
                if (reduced) fadeIn(tween(90)) togetherWith fadeOut(tween(90))
                else if (targetState != null) {
                    (slideInVertically(spring(dampingRatio = 0.55f, stiffness = Spring.StiffnessMediumLow)) { it / 2 } +
                        scaleIn(spring(dampingRatio = 0.5f, stiffness = Spring.StiffnessMedium), initialScale = 0.4f) +
                        fadeIn(tween(120))) togetherWith fadeOut(tween(80))
                } else {
                    fadeIn(tween(120)) togetherWith
                        (scaleOut(tween(140), targetScale = 0.4f) + slideOutVertically(tween(140)) { -it / 3 } + fadeOut(tween(140)))
                }
            },
            label = "otpDigit",
        ) { d ->
            if (d != null) {
                Text(
                    d.toString().toPersianDigits(),
                    style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold, fontSize = 26.sp),
                    color = if (error) scheme.error else scheme.onSurface,
                )
            } else if (active) {
                Caret(reduced)
            }
        }
    }
}

@Composable
private fun Caret(reduced: Boolean) {
    val alpha = if (reduced) 1f else {
        val t = rememberInfiniteTransition(label = "caret")
        t.animateFloat(
            1f, 0f,
            infiniteRepeatable(tween(530, delayMillis = 200), RepeatMode.Reverse),
            label = "caretAlpha",
        ).value
    }
    Box(
        Modifier
            .width(2.dp)
            .height(26.dp)
            .alpha(alpha)
            .background(MaterialTheme.colorScheme.primary, RoundedCornerShape(1.dp)),
    )
}
