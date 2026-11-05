package xyz.sattar.javid.proqueue.feature.businessDetail

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.UserCheck
import com.composables.icons.lucide.UserPlus
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import xyz.sattar.javid.proqueue.feature.booking.LocalReducedMotion
import xyz.sattar.javid.proqueue.feature.booking.pressScale
import xyz.sattar.javid.proqueue.feature.discovery.Space

/** Same violet-to-fuchsia as the story ring, so following reads as part of stories. */
private val FollowGradient = Brush.horizontalGradient(listOf(Color(0xFF7C3AED), Color(0xFFC026D3)))

/**
 * The follow call to action. Not following: a gradient pill that a soft shine
 * crosses every few seconds. Tapped: the label swaps with a pop, a ring bursts
 * out of the pill, and it settles into a quiet outlined "following".
 * [onImage] is for the story viewer, where the quiet state is white on the photo.
 */
@Composable
internal fun FollowButton(
    following: Boolean,
    busy: Boolean,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier,
    onImage: Boolean = false,
) {
    val reduced = LocalReducedMotion.current
    val interaction = remember { MutableInteractionSource() }
    val corner = 12.dp
    val shape = RoundedCornerShape(corner)

    val fill by animateFloatAsState(if (following) 0f else 1f, tween(240), label = "followFill")
    val quiet = if (onImage) Color.White else MaterialTheme.colorScheme.onSurface
    val content by animateColorAsState(if (following) quiet else Color.White, tween(240), label = "followFg")
    val outline = if (onImage) Color.White.copy(alpha = 0.7f) else MaterialTheme.colorScheme.outline
    val burstColor = if (onImage) Color.White else MaterialTheme.colorScheme.primary

    // The shine: a light band sweeping across while the button still asks to be tapped.
    val shine = remember { Animatable(-1f) }
    LaunchedEffect(following, reduced) {
        shine.snapTo(-1f)
        if (following || reduced) return@LaunchedEffect
        delay(700)
        while (true) {
            shine.animateTo(2f, tween(1000, easing = FastOutSlowInEasing))
            shine.snapTo(-1f)
            delay(2800)
        }
    }

    // Pop and burst on each change, but not when the screen first shows.
    val pop = remember { Animatable(1f) }
    val burst = remember { Animatable(1f) }
    var last by remember { mutableStateOf(following) }
    LaunchedEffect(following) {
        if (following != last && !reduced) {
            pop.snapTo(0.88f)
            coroutineScope {
                if (following) launch {
                    burst.snapTo(0f)
                    burst.animateTo(1f, tween(560, easing = LinearOutSlowInEasing))
                }
                pop.animateTo(1f, spring(dampingRatio = if (following) 0.4f else 0.6f, stiffness = Spring.StiffnessMedium))
            }
        }
        last = following
    }

    Box(
        modifier
            .drawBehind {
                val b = burst.value
                if (b < 1f) {
                    val grow = 14.dp.toPx() * b
                    drawRoundRect(
                        color = burstColor,
                        topLeft = Offset(-grow, -grow),
                        size = Size(size.width + grow * 2, size.height + grow * 2),
                        cornerRadius = CornerRadius(corner.toPx() + grow),
                        alpha = 0.55f * (1f - b),
                        style = Stroke(width = 2.dp.toPx() * (1f - b) + 0.5f),
                    )
                }
            }
            .pressScale(interaction, 0.94f)
            .graphicsLayer { scaleX = pop.value; scaleY = pop.value }
            .height(40.dp)
            .clip(shape)
            .drawBehind {
                if (fill > 0f) {
                    drawRect(FollowGradient, alpha = fill)
                    val s = shine.value
                    if (s > -1f && s < 2f) {
                        val x = size.width * s
                        drawRect(
                            Brush.linearGradient(
                                listOf(Color.Transparent, Color.White.copy(alpha = 0.38f), Color.Transparent),
                                start = Offset(x - size.height, 0f),
                                end = Offset(x + size.height, size.height),
                            ),
                            alpha = fill,
                        )
                    }
                }
            }
            .border(1.dp, outline.copy(alpha = outline.alpha * (1f - fill)), shape)
            .clickable(interaction, indication = null, enabled = !busy, role = Role.Button, onClick = onToggle)
            .animateContentSize(spring(stiffness = Spring.StiffnessMediumLow))
            .padding(horizontal = Space.lg)
            .graphicsLayer { alpha = if (busy) 0.7f else 1f },
        contentAlignment = Alignment.Center,
    ) {
        AnimatedContent(
            following,
            transitionSpec = {
                (scaleIn(spring(dampingRatio = 0.5f, stiffness = Spring.StiffnessMedium), initialScale = 0.5f) + fadeIn(tween(160)))
                    .togetherWith(scaleOut(tween(120), targetScale = 0.5f) + fadeOut(tween(100)))
                    .using(SizeTransform(clip = false))
            },
            label = "followLabel",
        ) { isFollowing ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    if (isFollowing) Lucide.UserCheck else Lucide.UserPlus,
                    contentDescription = null,
                    tint = content,
                    modifier = Modifier.size(16.dp),
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    if (isFollowing) "دنبال می‌کنید" else "دنبال کردن",
                    color = content,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                )
            }
        }
    }
}
