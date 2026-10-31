package xyz.sattar.javid.proqueue.feature.explore

import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.Briefcase
import com.composables.icons.lucide.GraduationCap
import com.composables.icons.lucide.HeartPulse
import com.composables.icons.lucide.LocateFixed
import com.composables.icons.lucide.MapPinPen
import com.composables.icons.lucide.Scissors
import com.composables.icons.lucide.Stethoscope
import com.composables.icons.lucide.Store
import com.composables.icons.lucide.X
import com.composables.icons.lucide.Zap
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import ovh.plrapps.mapcompose.api.scale
import xyz.sattar.javid.proqueue.domain.model.business.Business
import xyz.sattar.javid.proqueue.domain.model.business.BusinessCategory
import xyz.sattar.javid.proqueue.domain.model.business.BusinessCategoryGroup
import xyz.sattar.javid.proqueue.feature.discovery.ToothFilled
import xyz.sattar.javid.proqueue.feature.discovery.canBookOnline
import xyz.sattar.javid.proqueue.feature.discovery.toPersianDigits

// ── Category look ────────────────────────────────────────────────────────────
//
// One hue per group so a glance at the map tells clinics from salons. Mid-tone
// colours that hold white icons and read on both the light and the dark map.


internal data class PinStyle(val color: Color, val icon: ImageVector)

internal fun Business.pinStyle(): PinStyle = when (BusinessCategory.fromString(category).group) {
    BusinessCategoryGroup.MEDICAL -> PinStyle(Color(0xFF0D9488), Lucide.Stethoscope)
    BusinessCategoryGroup.DENTAL -> PinStyle(Color(0xFF2563EB), ToothFilled)
    BusinessCategoryGroup.HEALTH -> PinStyle(Color(0xFF16A34A), Lucide.HeartPulse)
    BusinessCategoryGroup.BEAUTY -> PinStyle(Color(0xFFDB2777), Lucide.Scissors)
    BusinessCategoryGroup.PROFESSIONAL -> PinStyle(Color(0xFFD97706), Lucide.Briefcase)
    BusinessCategoryGroup.EDUCATION -> PinStyle(Color(0xFF7C3AED), Lucide.GraduationCap)
    BusinessCategoryGroup.TRADES, BusinessCategoryGroup.OTHER -> PinStyle(Color(0xFF475569), Lucide.Store)
}

// ── Pins & overlays ──────────────────────────────────────────────────────────


/**
 * A rounded body with a pointer at the bottom centre, drawn as one contour so
 * the shadow and the white ring follow the whole outline without a seam.
 * With a square body and [cornerRadius] at half its side it's the round pin;
 * with a wide body it's the speech-bubble label of the selected pin.
 */
internal class BubbleShape(private val cornerRadius: Dp, private val tail: Dp) : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline {
        val t = with(density) { tail.toPx() }
        val h = size.height - t
        val r = minOf(with(density) { cornerRadius.toPx() }, h / 2, size.width / 2)
        val cx = size.width / 2
        val path = Path().apply {
            moveTo(r, 0f)
            lineTo(size.width - r, 0f)
            arcTo(Rect(size.width - 2 * r, 0f, size.width, 2 * r), -90f, 90f, false)
            lineTo(size.width, h - r)
            arcTo(Rect(size.width - 2 * r, h - 2 * r, size.width, h), 0f, 90f, false)
            lineTo(cx + t * 0.85f, h)
            quadraticTo(cx + t * 0.25f, h, cx, size.height)
            quadraticTo(cx - t * 0.25f, h, cx - t * 0.85f, h)
            lineTo(r, h)
            arcTo(Rect(0f, h - 2 * r, 2 * r, h), 90f, 90f, false)
            lineTo(0f, r)
            arcTo(Rect(0f, 0f, 2 * r, 2 * r), 180f, 90f, false)
            close()
        }
        return Outline.Generic(path)
    }
}

internal val PinTail = 6.dp

/**
 * Three looks, one shape family:
 * - bookable: a solid category-coloured pin with a white glyph — the product;
 * - directory-only: smaller, white, glyph in the category colour — present but quiet;
 * - selected: grows into a bubble carrying the name, on top of its neighbours.
 */
@Composable
internal fun BusinessPin(business: Business, selected: Boolean) {
    val style = business.pinStyle()
    val bookable = business.canBookOnline
    val ring = MaterialTheme.colorScheme.surface
    val pop by animateFloatAsState(
        if (selected) 1f else 0f,
        spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMediumLow)
    )

    if (selected) {
        val shape = BubbleShape(cornerRadius = 22.dp, tail = PinTail)
        Row(
            Modifier
                .scale(0.85f + 0.15f * pop)
                .shadow(10.dp, shape, ambientColor = style.color, spotColor = style.color)
                .background(ring, shape)
                .padding(start = 4.dp, end = 14.dp, top = 4.dp, bottom = 4.dp + PinTail),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(Modifier.size(32.dp).background(style.color, CircleShape), contentAlignment = Alignment.Center) {
                Icon(style.icon, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
            }
            Spacer(Modifier.width(8.dp))
            Text(
                business.title,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.widthIn(max = 168.dp)
            )
            if (bookable) {
                Spacer(Modifier.width(4.dp))
                Icon(Lucide.Zap, contentDescription = "نوبت آنلاین", tint = style.color, modifier = Modifier.size(16.dp))
            }
        }
        return
    }

    val body = if (bookable) 34.dp else 26.dp
    val shape = BubbleShape(cornerRadius = body / 2, tail = PinTail)
    Box(
        Modifier
            .size(width = body, height = body + PinTail)
            .shadow(if (bookable) 5.dp else 2.dp, shape, ambientColor = style.color, spotColor = style.color)
            .background(if (bookable) style.color else ring, shape)
            .border(if (bookable) 2.dp else 1.5.dp, if (bookable) ring else style.color.copy(alpha = 0.7f), shape),
        contentAlignment = Alignment.TopCenter
    ) {
        Icon(
            style.icon,
            contentDescription = business.title,
            tint = if (bookable) Color.White else style.color,
            modifier = Modifier.padding(top = body * 0.22f).size(body * 0.56f)
        )
    }
}

/** Several pins too close to tell apart: a count in the brand colour, sized by how many. */
@Composable
internal fun ClusterBubble(count: Int) {
    val primary = MaterialTheme.colorScheme.primary
    val size = when {
        count < 10 -> 40.dp
        count < 50 -> 46.dp
        else -> 52.dp
    }
    Box(
        Modifier.size(size + 12.dp).background(primary.copy(alpha = 0.16f), CircleShape),
        contentAlignment = Alignment.Center
    ) {
        Box(
            Modifier
                .size(size)
                .shadow(6.dp, CircleShape, ambientColor = primary, spotColor = primary)
                .background(primary, CircleShape)
                .border(2.5.dp, Color.White, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Text(
                count.toPersianDigits(),
                color = Color.White,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
internal fun MyLocationDot() {
    val accent = Color(0xFF2F80ED)
    Box(Modifier.size(30.dp).background(accent.copy(alpha = 0.18f), CircleShape), contentAlignment = Alignment.Center) {
        Box(
            Modifier
                .size(16.dp)
                .shadow(3.dp, CircleShape)
                .background(Color.White, CircleShape)
                .padding(3.dp)
                .background(accent, CircleShape)
        )
    }
}

/** Marks the centre of a "search this area" / picked search. */
@Composable
internal fun PickedAreaDot() {
    val primary = MaterialTheme.colorScheme.primary
    Box(Modifier.size(22.dp).background(primary.copy(alpha = 0.2f), CircleShape), contentAlignment = Alignment.Center) {
        Box(Modifier.size(10.dp).border(2.dp, Color.White, CircleShape).background(primary, CircleShape))
    }
}

/** Distance from the top of [CenterPin] to its tip, when resting. */
internal val CenterPinTip = 54.dp

/** The aiming pin of pick mode. Lifts off its shadow while the map moves. */
@Composable
internal fun CenterPin(lifted: Boolean, modifier: Modifier = Modifier) {
    val primary = MaterialTheme.colorScheme.primary
    val lift by animateDpAsState(if (lifted) 12.dp else 0.dp, spring(stiffness = Spring.StiffnessMediumLow))
    val shape = BubbleShape(cornerRadius = 22.dp, tail = 13.dp)
    Box(modifier.size(width = 44.dp, height = CenterPinTip + 6.dp)) {
        // The shadow stays on the ground and shrinks as the pin rises.
        Box(
            Modifier
                .align(Alignment.BottomCenter)
                .size(width = 14.dp - lift / 3, height = 5.dp)
                .background(Color.Black.copy(alpha = 0.28f - lift.value / 100f), CircleShape)
        )
        Box(
            Modifier
                .align(Alignment.TopCenter)
                .offset(y = -lift)
                .size(width = 44.dp, height = CenterPinTip)
                .shadow(8.dp, shape)
                .background(primary, shape)
                .border(2.dp, Color.White, shape),
            contentAlignment = Alignment.TopCenter
        ) {
            Box(Modifier.padding(top = 15.dp).size(14.dp).background(Color.White, CircleShape))
        }
    }
}

@Composable
internal fun MapPill(text: String, icon: ImageVector?, onClick: (() -> Unit)?) {
    val content: @Composable () -> Unit = {
        Row(Modifier.padding(horizontal = 16.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            if (icon != null) {
                Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
            }
            Text(text, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
        }
    }
    if (onClick != null) {
        Surface(onClick = onClick, shape = CircleShape, color = MaterialTheme.colorScheme.surface, shadowElevation = 6.dp) { content() }
    } else {
        Surface(shape = CircleShape, color = MaterialTheme.colorScheme.surface, shadowElevation = 6.dp) { content() }
    }
}

/**
 * The map's own controls, grouped in one card the way Google and Apple Maps
 * do: "choose on map" on top, "my location" under it. The active pick mode
 * fills its button with the accent so the mode is never ambiguous.
 */
@Composable
internal fun MapControls(
    pickMode: Boolean,
    locating: Boolean,
    onTogglePickMode: () -> Unit,
    onLocate: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surface,
        shadowElevation = 6.dp,
        modifier = modifier.width(48.dp)
    ) {
        Column {
            val activeBg by animateColorAsState(if (pickMode) MaterialTheme.colorScheme.primary else Color.Transparent)
            val activeFg by animateColorAsState(
                if (pickMode) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface
            )
            ControlButton(onClick = onTogglePickMode, background = activeBg) {
                Icon(
                    if (pickMode) Lucide.X else Lucide.MapPinPen,
                    contentDescription = if (pickMode) "لغو انتخاب روی نقشه" else "انتخاب محل روی نقشه",
                    tint = activeFg,
                    modifier = Modifier.size(22.dp)
                )
            }
            Box(Modifier.fillMaxWidth().padding(horizontal = 10.dp).height(1.dp).background(MaterialTheme.colorScheme.outlineVariant))
            ControlButton(onClick = onLocate) {
                if (locating) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                else Icon(Lucide.LocateFixed, contentDescription = "موقعیت من", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(22.dp))
            }
        }
    }
}

@Composable
internal fun ControlButton(onClick: () -> Unit, background: Color = Color.Transparent, content: @Composable () -> Unit) {
    Surface(onClick = onClick, color = background, modifier = Modifier.fillMaxWidth().height(48.dp)) {
        Box(contentAlignment = Alignment.Center) { content() }
    }
}
