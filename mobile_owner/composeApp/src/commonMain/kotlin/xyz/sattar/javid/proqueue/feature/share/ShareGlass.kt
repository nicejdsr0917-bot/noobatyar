package xyz.sattar.javid.proqueue.feature.share

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.BlurredEdgeTreatment
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * The glass-and-neon surface the share cards are built from.
 *
 * Three stacked layers, in draw order:
 *
 *  1. *Neon* — the same rounded outline as the panel, drawn thick in a
 *     cyan→violet→pink ramp and blurred outwards. Blurring it `Unbounded`
 *     rather than clipped is the whole trick: a glow that stops at the
 *     panel's own edge is just a fat border, while one allowed to bleed past
 *     it reads as light coming off the surface.
 *  2. *Glass* — a low-alpha white fill. Not a blur of what is behind it:
 *     these cards are exported as standalone images, so there is no live
 *     backdrop to sample, and a real backdrop blur (haze) would also make the
 *     export depend on what happened to be on screen at capture time.
 *  3. *Rim* — a one-pixel gradient border, bright at the top-leading corner
 *     and nearly gone at the bottom-trailing one, which is what tells the eye
 *     the surface is a lit sheet rather than a flat translucent rectangle.
 *
 * On Android below API 31 `Modifier.blur` is a no-op; the neon layer then
 * renders as a crisp second outline slightly outside the rim. Dimmer than
 * intended, still deliberate-looking — which is the right way for this to
 * degrade.
 */
internal val NeonRamp = listOf(
    Color(0xFF22D3EE), // cyan
    Color(0xFFA855F7), // violet
    Color(0xFFF472B6), // pink
)

@Composable
internal fun NeonGlass(
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(22.dp),
    fill: Color = Color.White.copy(alpha = 0.13f),
    glow: List<Color> = NeonRamp,
    glowRadius: Dp = 12.dp,
    glowWidth: Dp = 3.dp,
    rimAlpha: Float = 0.55f,
    content: @Composable BoxScope.() -> Unit,
) {
    Box(modifier) {
        Box(
            Modifier
                .matchParentSize()
                .blur(glowRadius, BlurredEdgeTreatment.Unbounded)
                .border(glowWidth, Brush.linearGradient(glow), shape)
        )
        Box(
            Modifier
                .matchParentSize()
                .clip(shape)
                .background(fill)
                .border(
                    width = 1.dp,
                    brush = Brush.linearGradient(
                        listOf(
                            Color.White.copy(alpha = rimAlpha),
                            Color.White.copy(alpha = rimAlpha * 0.18f),
                        )
                    ),
                    shape = shape,
                )
        )
        content()
    }
}

/** Neon halo around an opaque panel — the QR板 and the guide card stay solid
 *  white for contrast, and only borrow the glow. */
@Composable
internal fun NeonHalo(
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(26.dp),
    glow: List<Color> = NeonRamp,
    glowRadius: Dp = 16.dp,
    content: @Composable BoxScope.() -> Unit,
) {
    Box(modifier) {
        Box(
            Modifier
                .matchParentSize()
                .padding(2.dp)
                .blur(glowRadius, BlurredEdgeTreatment.Unbounded)
                .border(4.dp, Brush.linearGradient(glow), shape)
        )
        content()
    }
}
