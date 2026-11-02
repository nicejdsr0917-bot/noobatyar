package xyz.sattar.javid.proqueue.feature.home

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.animateIntAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
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
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import xyz.sattar.javid.proqueue.core.utils.toPersianDigits

/**
 * The few pieces every Home card is built from, so the cards share one
 * rhythm: the same corner radius, the same 16dp inner padding, and a header
 * row whose icon, title and trailing action sit on one line in every card.
 * The old cards each picked their own (20, 22 and 24dp corners, tinted fills,
 * headers with and without icons), which is most of why the screen looked
 * misaligned.
 */
object HomeDimens {
    val CardRadius = 20.dp
    val CardPadding = 16.dp
    val SectionGap = 12.dp
    val HeaderIcon = 32.dp
}

/**
 * A Home card: flat surface, hairline border, and a small press-in when it is
 * tappable. No tint per card; colour is kept for the data inside.
 */
@Composable
fun HomeCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    contentPadding: Dp = HomeDimens.CardPadding,
    content: @Composable ColumnScope.() -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .pressScale(interaction, enabled = onClick != null),
        shape = RoundedCornerShape(HomeDimens.CardRadius),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f)),
    ) {
        Column(
            modifier = Modifier
                .then(
                    if (onClick != null) Modifier.clickable(
                        interactionSource = interaction,
                        indication = androidx.compose.material3.ripple(),
                        onClick = onClick,
                    ) else Modifier
                )
                .padding(contentPadding),
            content = content,
        )
    }
}

/** Icon, title and an optional trailing slot on one centred line. */
@Composable
fun HomeCardHeader(
    icon: ImageVector,
    title: String,
    modifier: Modifier = Modifier,
    trailing: @Composable () -> Unit = {},
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(HomeDimens.HeaderIcon)
                .clip(RoundedCornerShape(10.dp))
                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
        }
        Spacer(Modifier.size(10.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f),
            maxLines = 1,
        )
        trailing()
    }
}

/** A number that counts to its new value instead of jumping to it. */
@Composable
fun AnimatedCount(
    value: Int,
    style: TextStyle,
    color: Color = MaterialTheme.colorScheme.onSurface,
    modifier: Modifier = Modifier,
    fontWeight: FontWeight = FontWeight.Black,
) {
    val shown by animateIntAsState(
        targetValue = value,
        animationSpec = tween(durationMillis = 700, easing = FastOutSlowInEasing),
        label = "count",
    )
    Text(
        text = shown.toString().toPersianDigits(),
        style = style,
        fontWeight = fontWeight,
        color = color,
        modifier = modifier,
        maxLines = 1,
    )
}

/** A soft pulsing dot: "this number is live". */
@Composable
fun LiveDot(color: Color, modifier: Modifier = Modifier, size: Dp = 8.dp) {
    val transition = rememberInfiniteTransition(label = "live")
    val halo by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(1400, easing = FastOutSlowInEasing), RepeatMode.Restart),
        label = "halo",
    )
    Box(modifier = modifier.size(size * 2), contentAlignment = Alignment.Center) {
        Box(
            Modifier
                .size(size * 2)
                .graphicsLayer {
                    scaleX = 0.5f + halo * 0.5f
                    scaleY = 0.5f + halo * 0.5f
                    alpha = (1f - halo) * 0.5f
                }
                .clip(CircleShape)
                .background(color)
        )
        Box(Modifier.size(size).clip(CircleShape).background(color))
    }
}

/** A rounded progress bar that fills from empty when it first shows. */
@Composable
fun HomeProgressBar(
    fraction: Float,
    color: Color,
    modifier: Modifier = Modifier,
    trackColor: Color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.07f),
) {
    val anim = remember { Animatable(0f) }
    LaunchedEffect(fraction) {
        anim.animateTo(fraction.coerceIn(0f, 1f), tween(900, easing = FastOutSlowInEasing))
    }
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(6.dp)
            .clip(CircleShape)
            .background(trackColor),
    ) {
        Box(
            Modifier
                .fillMaxWidth(anim.value)
                .height(6.dp)
                .clip(CircleShape)
                .background(color)
        )
    }
}

/** Shrinks a touch while pressed, springing back on release. */
fun Modifier.pressScale(interaction: MutableInteractionSource, enabled: Boolean = true): Modifier =
    if (!enabled) this else this.composed {
        val pressed by interaction.collectIsPressedAsState()
        val scale by animateFloatAsState(
            targetValue = if (pressed) 0.975f else 1f,
            animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium),
            label = "press",
        )
        graphicsLayer { scaleX = scale; scaleY = scale }
    }

/**
 * Staggered entrance for a Home block: fades in and rises a few dp on a soft
 * spring, [index] steps after the first. Done in the draw layer, so the list's
 * layout never moves while it plays (the old AnimatedVisibility grew each
 * block from zero height and pushed everything under it down).
 */
@Composable
fun HomeReveal(index: Int, content: @Composable () -> Unit) {
    val alpha = remember { Animatable(0f) }
    val rise = remember { Animatable(1f) }
    val offsetPx = with(LocalDensity.current) { 18.dp.toPx() }
    LaunchedEffect(Unit) {
        delay(70L * index)
        launch { alpha.animateTo(1f, tween(420, easing = FastOutSlowInEasing)) }
        rise.animateTo(0f, spring(dampingRatio = 0.8f, stiffness = Spring.StiffnessLow))
    }
    Box(
        Modifier.graphicsLayer {
            this.alpha = alpha.value
            translationY = rise.value * offsetPx
        }
    ) { content() }
}

/** One cell of a stat strip: coloured dot and label over an animated number. */
@Composable
fun StatCell(
    label: String,
    value: Int,
    accent: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(7.dp).clip(CircleShape).background(accent))
            Spacer(Modifier.size(6.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
            )
        }
        AnimatedCount(value = value, style = MaterialTheme.typography.titleLarge)
    }
}
