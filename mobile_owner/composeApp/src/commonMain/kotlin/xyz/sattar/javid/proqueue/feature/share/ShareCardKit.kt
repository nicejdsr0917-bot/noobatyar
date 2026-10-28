package xyz.sattar.javid.proqueue.feature.share

import xyz.sattar.javid.proqueue.core.i18n.Str
import androidx.compose.foundation.background
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Storefront
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.Image
import coil3.compose.AsyncImage
import org.jetbrains.compose.resources.painterResource
import proqueue.composeapp.generated.resources.Res
import proqueue.composeapp.generated.resources.main_icon
import xyz.sattar.javid.proqueue.core.ui.Capturable
import xyz.sattar.javid.proqueue.core.ui.CaptureController
import xyz.sattar.javid.proqueue.core.utils.AppConfig
import xyz.sattar.javid.proqueue.core.utils.encodePng
import xyz.sattar.javid.proqueue.core.utils.withPngDpi
import androidx.compose.ui.graphics.ImageBitmap
import xyz.sattar.javid.proqueue.domain.model.business.Business

/**
 * The pieces every exported share card is built from — geometry, palette,
 * backdrop, avatar, signature, and the scaffolding that renders a card at a
 * fixed export size while previewing it scaled to fit.
 *
 * Split out of ShareBusinessScreen so the appointment-reminder card can be
 * the same object: same proportions, same surface, same signature. A second
 * card built from its own copies of these would drift within a release.
 */

// ── Card geometry ────────────────────────────────────────────────────────────
//
// Every exported card is a 14 × 21 cm portrait sheet at 300 dpi — 1654 × 2480
// px, a 2:3 ratio — because owners print them for the counter, on a
// plexiglass table stand of that size. The same file still posts fine to
// Instagram or a chat; the reverse (a 1080px social image blown up to print
// size) comes out soft.
//
// Cards are laid out in a fixed design space 360dp wide under a pinned
// density, so the export is exactly that pixel size on *every* device rather
// than whatever the handset's own density happens to give.
internal const val CARD_PRINT_DPI = 300
internal const val CARD_EXPORT_WIDTH_PX = 1654   // 140 mm at 300 dpi
internal const val CARD_EXPORT_HEIGHT_PX = 2480  // 210 mm at 300 dpi
internal const val CARD_DESIGN_WIDTH_DP = 360f
internal const val CARD_EXPORT_DENSITY = CARD_EXPORT_WIDTH_PX / CARD_DESIGN_WIDTH_DP
internal const val CARD_HEIGHT_DP = CARD_EXPORT_HEIGHT_PX / CARD_EXPORT_DENSITY // ≈ 539.8

// Margins every card keeps clear: ~10.1 mm at the sides and ~9.3 mm top and
// bottom once printed — well inside a print shop's trim and a stand's frame.
internal val CARD_SIDE_PADDING = 26.dp
internal val CARD_EDGE_PADDING = 24.dp

/** PNG of a captured card, tagged 300 dpi so print software opens it at
 *  14 × 21 cm instead of guessing 72 dpi (which reads as a 58 cm poster). */
internal fun encodeCardPng(image: ImageBitmap): ByteArray =
    encodePng(image).withPngDpi(CARD_PRINT_DPI)

// Brand violets, fixed rather than theme roles: a shared card has to look the
// same to the customer who receives it regardless of the owner's light/dark
// setting, and the theme only overrides `primary` anyway (see AboutUsScreen's
// HeroViolets for the same reasoning).
internal val ShareVioletDeep = Color(0xFF4C1D95)
internal val ShareVioletDark = Color(0xFF5B21B6)
internal val ShareViolet = Color(0xFF7C3AED)
internal val ShareVioletLight = Color(0xFFA855F7)

/**
 * The backdrop the cards are built on: a diagonal violet ramp, two off-centre
 * light sources, a pair of thin concentric rings, and a vignette along the
 * bottom edge.
 *
 * A single linear gradient reads as a coloured rectangle — fine for a screen,
 * not for something posted next to other people's content. The highlights give
 * the surface a light source, the rings give it a subject, and the vignette
 * keeps the footer text (link, signature) legible against the brightest part
 * of the ramp instead of fighting it.
 *
 * Drawn rather than composed so every measurement is a fraction of the card's
 * own size — the same code produces the 4:5 poster and the 9:16 story without
 * a second set of numbers.
 */
@Composable
internal fun CardBackdrop(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier.drawBehind {
            val w = size.width
            val h = size.height

            // Warm highlight, upper-trailing corner.
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(Color.White.copy(alpha = 0.22f), Color.Transparent),
                    center = Offset(w * 0.86f, h * 0.06f),
                    radius = w * 0.85f,
                ),
                radius = w * 0.85f,
                center = Offset(w * 0.86f, h * 0.06f),
            )
            // Cooler bloom, lower-leading corner.
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(ShareVioletLight.copy(alpha = 0.55f), Color.Transparent),
                    center = Offset(w * 0.08f, h * 0.72f),
                    radius = w * 0.95f,
                ),
                radius = w * 0.95f,
                center = Offset(w * 0.08f, h * 0.72f),
            )

            // Two hairline rings, deliberately running off the edges: a shape
            // that is fully contained reads as a sticker placed on the card,
            // one that is cropped reads as the card being a window onto
            // something larger.
            val ringCenter = Offset(w * 0.92f, h * 0.18f)
            drawCircle(
                color = Color.White.copy(alpha = 0.16f),
                radius = w * 0.46f,
                center = ringCenter,
                style = Stroke(width = 1.5.dp.toPx()),
            )
            drawCircle(
                color = Color.White.copy(alpha = 0.10f),
                radius = w * 0.66f,
                center = ringCenter,
                style = Stroke(width = 1.5.dp.toPx()),
            )

            // Vignette: the lower third carries the link and the signature.
            drawRect(
                brush = Brush.verticalGradient(
                    colors = listOf(Color.Transparent, ShareVioletDeep.copy(alpha = 0.55f)),
                    startY = h * 0.55f,
                    endY = h,
                )
            )
        }
    )
}

/** Drops the scheme — "noobatyar.ir/b/CODE" is what a person reads off a
 *  poster, and the https:// in front of it is nine characters of noise. */
internal fun String.shortForDisplay(): String =
    removePrefix("https://").removePrefix("http://")

/**
 * A URL rendered left-to-right even though the surrounding app is RTL.
 *
 * Without this the bidi algorithm reorders the URL's own segments around the
 * slashes — "10.0.2.2:3000/b/DENTDEMO" renders as "b/DENTDEMO/10.0.2.2:3000",
 * which is not just ugly but wrong: someone typing it in off a printed poster
 * gets a dead link.
 */
@Composable
internal fun LtrText(
    text: String,
    fontSize: androidx.compose.ui.unit.TextUnit,
    weight: FontWeight,
    color: Color,
    modifier: Modifier = Modifier,
    letterSpacing: androidx.compose.ui.unit.TextUnit = androidx.compose.ui.unit.TextUnit.Unspecified,
) {
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
        Text(
            text = text,
            fontSize = fontSize,
            fontWeight = weight,
            color = color,
            letterSpacing = letterSpacing,
            // One line always: these are URLs and card numbers, and a URL
            // broken across two lines is a URL nobody can retype. Ellipsis
            // rather than a silent crop so a value that doesn't fit is
            // visibly truncated instead of looking like the whole thing.
            maxLines = 1,
            softWrap = false,
            overflow = TextOverflow.Ellipsis,
            modifier = modifier,
        )
    }
}

/**
 * The business's own logo, which is what identifies the card — on every card,
 * wherever its name appears. The storefront glyph is only the fallback for a
 * business that hasn't uploaded one.
 *
 * [ringColor] exists because the disc behind a transparent logo is white: that
 * reads as a lit avatar on the violet cards, but vanishes on the light-surface
 * link card, which passes a tinted ring to give it an edge.
 */
@Composable
internal fun BusinessAvatar(
    business: Business,
    size: Int,
    ring: Boolean = false,
    ringColor: Color = Color.White.copy(alpha = 0.28f),
) {
    val inner: @Composable () -> Unit = {
        Box(
            modifier = Modifier
                .size(size.dp)
                .clip(CircleShape)
                .background(Color.White),
            contentAlignment = Alignment.Center,
        ) {
            if (business.logoPath.isNotEmpty()) {
                val url = if (business.logoPath.startsWith("http")) business.logoPath
                else "${AppConfig.baseUrl}${business.logoPath}"
                AsyncImage(
                    model = url,
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop,
                )
            } else {
                Icon(
                    Icons.Rounded.Storefront,
                    contentDescription = null,
                    tint = ShareViolet,
                    modifier = Modifier.size((size * 0.45f).dp),
                )
            }
        }
    }

    if (!ring) {
        inner()
        return
    }
    Box(
        modifier = Modifier
            .size((size + 8).dp)
            .clip(CircleShape)
            .background(ringColor),
        contentAlignment = Alignment.Center,
    ) {
        inner()
    }
}

/**
 * Noobatyar's mark at the foot of a shared card: the app icon and name on one
 * line, the domain centred under them.
 *
 * A signature, not a watermark: an app icon blown up behind the content at 10%
 * alpha doesn't read as branding, it reads as a rendering artefact sitting
 * behind the business's own logo. The card belongs to the business; this only
 * says what made it.
 */
@Composable
internal fun AppSignature(onDark: Boolean, modifier: Modifier = Modifier) {
    val tone = if (onDark) Color.White else MaterialTheme.colorScheme.onSurfaceVariant
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier.alpha(if (onDark) 0.85f else 0.6f),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Image(
                painter = painterResource(Res.drawable.main_icon),
                contentDescription = null,
                modifier = Modifier.size(16.dp),
            )
            Spacer(Modifier.width(5.dp))
            Text(
                text = Str.about_us_app_name,
                fontSize = 12.sp,
                lineHeight = 16.sp,
                fontWeight = FontWeight.Bold,
                color = tone,
                maxLines = 1,
            )
        }
        // The name alone is a brand nobody can look up. The domain under it
        // is the whole point of signing a card that will be reposted,
        // screenshotted and forwarded away from any link.
        LtrText(
            text = "noobatyar.ir",
            fontSize = 9.sp,
            weight = FontWeight.SemiBold,
            color = tone.copy(alpha = 0.9f),
        )
    }
}

/**
 * The head every card starts with: the business's logo, an optional small
 * label, and its name, stacked on the card's centre line. Shared so the cards
 * an owner flips between keep their header in exactly the same place.
 * [compact] is for a card whose subject is someone else (the reminder, which
 * leads with the customer's name) and needs the height for its own content.
 */
@Composable
internal fun CardHeader(business: Business, label: String? = null, compact: Boolean = false) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        BusinessAvatar(business, size = if (compact) 44 else 56, ring = true)
        Spacer(Modifier.height(if (compact) 8.dp else 10.dp))
        if (label != null) {
            Text(
                text = label,
                fontSize = 11.sp,
                lineHeight = 15.sp,
                color = Color.White.copy(alpha = 0.75f),
                textAlign = TextAlign.Center,
                maxLines = 1,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(2.dp))
        }
        Text(
            text = business.title,
            fontSize = if (compact) 17.sp else 20.sp,
            lineHeight = if (compact) 23.sp else 26.sp,
            fontWeight = FontWeight.Bold,
            color = Color.White,
            textAlign = TextAlign.Center,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

/**
 * The foot every card ends on: the booking link (when the card has one) and
 * the signature under it, both on the card's centre line.
 *
 * Part of the card's column, not overlaid on it. The signature used to be
 * pinned to a corner over the content, and with a long link it ran into the
 * link chip — on a printed stand that collision is permanent.
 */
@Composable
internal fun CardFooter(link: String? = null) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        if (link != null) {
            NeonGlass(
                shape = RoundedCornerShape(12.dp),
                glowRadius = 8.dp,
                glowWidth = 2.dp,
            ) {
                LtrText(
                    text = link.shortForDisplay(),
                    fontSize = 12.sp,
                    weight = FontWeight.SemiBold,
                    color = Color.White,
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 7.dp),
                )
            }
            Spacer(Modifier.height(14.dp))
        }
        AppSignature(onDark = true)
    }
}

/**
 * Lays [content] out in the fixed design space described at the top of this
 * file and scales the *preview* down to fit the screen, leaving the captured
 * pixels at full export size.
 *
 * The density override also pins `fontScale` to 1: a shared card is a picture
 * being sent to someone else, so the owner's own accessibility text size must
 * not reflow it — that is the difference between an export that always looks
 * the same and one that silently breaks on some phones.
 */
@Composable
internal fun ExportableCard(
    controller: CaptureController,
    designHeightDp: Float,
    content: @Composable () -> Unit,
) {
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val outerDensity = LocalDensity.current
        val availableWidthPx = with(outerDensity) { maxWidth.toPx() }
        val exportWidthPx = CARD_DESIGN_WIDTH_DP * CARD_EXPORT_DENSITY
        val scale = availableWidthPx / exportWidthPx
        val previewHeight =
            with(outerDensity) { (designHeightDp * CARD_EXPORT_DENSITY * scale).toDp() }

        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .height(previewHeight),
            shape = RoundedCornerShape(28.dp),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
            shadowElevation = 8.dp
        ) {
            CompositionLocalProvider(
                LocalDensity provides Density(CARD_EXPORT_DENSITY, fontScale = 1f)
            ) {
                // Scaled about its centre, which is also where the Box puts
                // an oversized child: the preview box is exactly the scaled
                // size, so the two cancel out and the card lands flush inside
                // it. (Scaling about the top-left instead leaves the card
                // offset by half the overflow — it hangs off the side.)
                Box(
                    modifier = Modifier
                        .requiredSize(CARD_DESIGN_WIDTH_DP.dp, designHeightDp.dp)
                        .graphicsLayer {
                            scaleX = scale
                            scaleY = scale
                        },
                ) {
                    Capturable(controller) { content() }
                }
            }
        }
    }
}
