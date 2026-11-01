package xyz.sattar.javid.proqueue.feature.businessDetail

import com.composables.icons.lucide.Bookmark
import com.composables.icons.lucide.Clock
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.MapPin
import com.composables.icons.lucide.MessageSquare
import com.composables.icons.lucide.Navigation
import com.composables.icons.lucide.Phone
import com.composables.icons.lucide.Share2
import com.composables.icons.lucide.ShieldPlus
import com.composables.icons.lucide.Star
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.ui.graphics.BlurEffect
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.foundation.ScrollState
import androidx.compose.runtime.Stable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import kotlin.math.roundToInt
import xyz.sattar.javid.proqueue.feature.booking.LocalReducedMotion
import coil3.compose.AsyncImage
import xyz.sattar.javid.proqueue.feature.discovery.logoUrl
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import xyz.sattar.javid.proqueue.core.location.rememberDirections
import xyz.sattar.javid.proqueue.core.ui.components.ShimmerBox
import xyz.sattar.javid.proqueue.core.ui.icons.filled
import xyz.sattar.javid.proqueue.core.utils.openPhoneDial
import xyz.sattar.javid.proqueue.data.remoteDataSource.booking.model.TimeSlotDto
import xyz.sattar.javid.proqueue.data.remoteDataSource.social.model.ReviewSummaryDto
import xyz.sattar.javid.proqueue.domain.model.business.Business
import xyz.sattar.javid.proqueue.feature.booking.DayStrip
import xyz.sattar.javid.proqueue.feature.discovery.BackIcon
import xyz.sattar.javid.proqueue.feature.discovery.BusinessAvatar
import xyz.sattar.javid.proqueue.feature.discovery.Space
import xyz.sattar.javid.proqueue.feature.discovery.areaLine
import xyz.sattar.javid.proqueue.feature.discovery.formatDistance
import xyz.sattar.javid.proqueue.feature.discovery.toPersianDigits
import xyz.sattar.javid.proqueue.ui.theme.AccentSoft
import xyz.sattar.javid.proqueue.ui.theme.AccentSoftDark
import xyz.sattar.javid.proqueue.ui.theme.LocalIsDarkTheme

// Brand gradient (docs/BRAND_GUIDE_FOR_AI.md): light violet to the deep one,
// diagonal. Dark theme drops it a step so white text keeps its contrast.
private val HeroLight = listOf(Color(0xFFA78BFA), Color(0xFF8B5CF6), Color(0xFF7C3AED))
private val HeroDark = listOf(Color(0xFF7C3AED), Color(0xFF6D28D9), Color(0xFF4C1D95))

/** The brand's amber, for the rating star only. */
private val StarAmber = Color(0xFFFBBF24)

private val HeroCorner = 32.dp

/** Height of the pinned bar's row, under the status bar. */
internal val PinnedBarHeight = 56.dp

/**
 * How far the hero has tucked under the pinned bar, read straight from the
 * page's scroll inside draw/layer blocks so scrolling never recomposes the
 * page: 0 fully open, 1 collapsed. [range] is the hero's height minus the bar.
 */
@Stable
internal class HeroCollapse(private val scroll: ScrollState) {
    var range by mutableFloatStateOf(1f)
    val offset: Float get() = scroll.value.toFloat()
    val progress: Float get() = (offset / range.coerceAtLeast(1f)).coerceIn(0f, 1f)

    // Where the name sits in the open hero (root coordinates, scroll taken
    // out) and in the pinned bar: the start edge and the vertical centre. The
    // hero's name flies from one to the other as the page collapses.
    var heroTitleStart by mutableFloatStateOf(0f)
    var heroTitleCenterY by mutableFloatStateOf(0f)
    var barTitleStart by mutableFloatStateOf(0f)
    var barTitleCenterY by mutableFloatStateOf(0f)
    val titlesPlaced: Boolean get() = heroTitleCenterY > 0f && barTitleCenterY > 0f

    /** The flight's eased position: quick to leave, settling softly into the bar. */
    val flight: Float get() = FastOutSlowInEasing.transform(progress)
}

private fun mix(a: Float, b: Float, t: Float) = a + (b - a) * t

/** Overshoots a touch past 1 before settling: the small avatar's pop. */
private val BackOvershoot = CubicBezierEasing(0.34f, 1.56f, 0.64f, 1f)

/** The start edge of a node in root coordinates: its right edge in RTL. */
private fun LayoutCoordinates.startEdge(rtl: Boolean): Float =
    positionInRoot().x + if (rtl) size.width else 0

/** The hero's rounded bottom straightens as it folds into the bar. */
private fun Modifier.collapsingClip(collapse: HeroCollapse): Modifier = graphicsLayer {
    val r = HeroCorner.toPx() * (1f - collapse.window(0.5f, 1f))
    shape = RoundedCornerShape(bottomStart = r, bottomEnd = r)
    clip = true
}

/** [progress] remapped so a part animates only over its own slice of the collapse. */
private fun HeroCollapse.window(from: Float, to: Float): Float =
    ((progress - from) / (to - from)).coerceIn(0f, 1f)

/**
 * The page header: the centre's photo full bleed when it has one, otherwise
 * its logo on the brand gradient; its name and rating over it, and call /
 * directions as round buttons — like a profile card, so the page reads as
 * "this place" before any detail.
 *
 * On scroll it collapses under [PinnedDetailBar]: the photo drifts at half
 * speed and deepens to the brand violet, the name and buttons shrink and fade
 * as the bar's own title takes over.
 */
@Composable
internal fun DetailHero(
    business: Business,
    rating: ReviewSummaryDto?,
    collapse: HeroCollapse,
    modifier: Modifier = Modifier,
) {
    val photo = logoUrl(business)
    val colors = if (LocalIsDarkTheme.current) HeroDark else HeroLight
    val reduced = LocalReducedMotion.current
    if (photo != null) {
        Box(
            modifier
                .fillMaxWidth()
                .height(PhotoHeroHeight)
                .collapsingClip(collapse)
                .background(colors.last())
        ) {
            AsyncImage(
                model = photo,
                contentDescription = business.title,
                contentScale = ContentScale.Crop,
                modifier = Modifier.matchParentSize().graphicsLayer {
                    if (!reduced) {
                        // Parallax: the photo moves at half the page's speed and
                        // leans in a little, so the header feels deep, not pasted on.
                        translationY = collapse.offset * 0.5f
                        val zoom = 1f + 0.12f * collapse.progress
                        scaleX = zoom
                        scaleY = zoom
                        // Goes soft as it leaves, so the name flying over it stays crisp.
                        val blur = 18f * collapse.window(0.25f, 1f)
                        renderEffect = if (blur > 0.5f) BlurEffect(blur, blur) else null
                    }
                },
            )
            Box(
                Modifier.matchParentSize().background(
                    Brush.verticalGradient(
                        0f to Color.Black.copy(alpha = 0.35f),
                        0.3f to Color.Transparent,
                        0.6f to colors.last().copy(alpha = 0.35f),
                        1f to colors.last().copy(alpha = 0.95f),
                    )
                )
            )
            // Deepens to the brand violet as it tucks away, so the pinned bar
            // grows out of a colour, not out of half a photo.
            Box(
                Modifier
                    .matchParentSize()
                    .graphicsLayer { alpha = collapse.window(0.2f, 1f) * 0.9f }
                    .background(colors.last())
            )
            Column(Modifier.matchParentSize().windowInsetsPadding(WindowInsets.statusBars).padding(bottom = Space.xl + 28.dp)) {
                // The pinned bar's buttons float here.
                Spacer(Modifier.height(PinnedBarHeight))
                Spacer(Modifier.weight(1f))
                HeroInfo(business, rating, collapse)
            }
        }
    } else {
        Box(
            modifier
                .fillMaxWidth()
                .collapsingClip(collapse)
                .background(Brush.linearGradient(colors))
                .windowInsetsPadding(WindowInsets.statusBars)
        ) {
            Column(Modifier.fillMaxWidth().padding(bottom = Space.xl + 28.dp)) {
                Spacer(Modifier.height(PinnedBarHeight))
                Box(
                    Modifier
                        .fillMaxWidth()
                        .padding(top = Space.sm)
                        .graphicsLayer {
                            // The logo sinks back and fades first; it reappears
                            // small in the pinned bar.
                            val q = collapse.window(0f, 0.6f)
                            alpha = 1f - q
                            if (!reduced) {
                                val s = 1f - 0.4f * q
                                scaleX = s
                                scaleY = s
                                translationY = collapse.offset * 0.35f
                            }
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    Box(
                        Modifier
                            .shadow(16.dp, RoundedCornerShape(28.dp), ambientColor = Color(0xFF4C1D95), spotColor = Color(0xFF4C1D95))
                            .background(Color.White, RoundedCornerShape(28.dp))
                            .padding(6.dp)
                    ) {
                        Box(Modifier.clip(RoundedCornerShape(22.dp))) { BusinessAvatar(business, 104.dp) }
                    }
                }
                Spacer(Modifier.height(Space.lg))
                HeroInfo(business, rating, collapse)
            }
        }
    }
}

private val PhotoHeroHeight = 440.dp

/** Name, category, rating and area on the start side; call and directions on the end. */
@Composable
private fun HeroInfo(business: Business, rating: ReviewSummaryDto?, collapse: HeroCollapse) {
    val directions = rememberDirections()
    val reduced = LocalReducedMotion.current
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    val lat = business.latitude
    val lng = business.longitude
    val lift = with(LocalDensity.current) { 20.dp.toPx() }
    val heroStyle = MaterialTheme.typography.headlineSmall
    // The bar's title is titleMedium: the name shrinks to exactly that size on the way.
    val landingScale = MaterialTheme.typography.titleMedium.fontSize.value / heroStyle.fontSize.value
    Row(Modifier.fillMaxWidth().padding(horizontal = Space.page), verticalAlignment = Alignment.Bottom) {
        Column(Modifier.weight(1f)) {
            Text(
                business.title,
                style = heroStyle,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    // Measured outside the layer below, so the flight never
                    // feeds back into its own starting point.
                    .onGloballyPositioned { c ->
                        collapse.heroTitleStart = c.startEdge(rtl)
                        collapse.heroTitleCenterY = c.positionInRoot().y + c.size.height / 2f + collapse.offset
                    }
                    .graphicsLayer {
                        if (reduced || !collapse.titlesPlaced) {
                            alpha = 1f - collapse.window(0.05f, 0.5f)
                            return@graphicsLayer
                        }
                        // Flies to the bar: shrinks about its start edge, slides
                        // across to the bar's title, and climbs against the
                        // scroll so it lands exactly where the bar's name sits.
                        val e = collapse.flight
                        transformOrigin = TransformOrigin(if (rtl) 1f else 0f, 0.5f)
                        val s = mix(1f, landingScale, e)
                        scaleX = s
                        scaleY = s
                        translationX = (collapse.barTitleStart - collapse.heroTitleStart) * e
                        val nowY = collapse.heroTitleCenterY - collapse.offset
                        translationY = mix(collapse.heroTitleCenterY, collapse.barTitleCenterY, e) - nowY
                        // Hands over to the bar's own theme-coloured name as the bar fills.
                        alpha = 1f - collapse.window(0.72f, 0.95f)
                    },
            )
            Spacer(Modifier.height(Space.xs))
            Column(
                Modifier.graphicsLayer {
                    // Category and area ride along under the flying name (the
                    // page scrolls faster than the name climbs, so left alone
                    // they'd slide over it) and fade out on the way.
                    val q = collapse.window(0f, 0.35f)
                    alpha = 1f - q
                    if (reduced) return@graphicsLayer
                    if (collapse.titlesPlaced) {
                        val e = collapse.flight
                        transformOrigin = TransformOrigin(if (rtl) 1f else 0f, 0f)
                        val s = mix(1f, 0.85f, e)
                        scaleX = s
                        scaleY = s
                        translationX = (collapse.barTitleStart - collapse.heroTitleStart) * e
                        translationY = collapse.offset + (collapse.barTitleCenterY - collapse.heroTitleCenterY) * e
                    } else {
                        translationY = -lift * q
                    }
                }
            ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    business.categoryLabel,
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.White.copy(alpha = 0.85f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                if (rating != null && rating.count > 0) {
                    Spacer(Modifier.width(Space.sm))
                    RatingChip(rating.avg)
                }
            }
            val area = business.areaLine()
            if (area.isNotBlank()) {
                Text(area, style = MaterialTheme.typography.bodySmall, color = Color.White.copy(alpha = 0.7f), maxLines = 1)
            }
            }
        }
        // The round buttons pop away first; the sticky bar keeps call and directions at hand.
        val buttons = Modifier.graphicsLayer {
            val q = collapse.window(0f, 0.35f)
            alpha = 1f - q
            if (!reduced) {
                val s = 1f - 0.5f * q
                scaleX = s
                scaleY = s
            }
        }
        if (business.phone.isNotBlank()) {
            Spacer(Modifier.width(Space.sm))
            RoundAction(Lucide.Phone, "تماس", buttons) { openPhoneDial(business.phone) }
        }
        if (lat != null && lng != null) {
            Spacer(Modifier.width(Space.sm))
            RoundAction(Lucide.Navigation, "مسیریابی", buttons) { directions(lat, lng, business.title) }
        }
    }
}

/**
 * Back, share and save, pinned over the page. Over the open hero they are
 * glass circles with white icons; as the hero collapses the bar fills with
 * the theme's surface (white or near-black), the circles melt into plain
 * icons in the text colour, and the centre's avatar and name rise into the middle.
 */
@Composable
internal fun PinnedDetailBar(
    business: Business,
    collapse: HeroCollapse,
    isSaved: Boolean,
    onBack: () -> Unit,
    onShare: () -> Unit,
    onToggleSave: () -> Unit,
) {
    val surface = MaterialTheme.colorScheme.surface
    val onSurface = MaterialTheme.colorScheme.onSurface
    val hairline = MaterialTheme.colorScheme.outlineVariant
    val hasPhoto = remember(business) { logoUrl(business) != null }
    // Dark glass over a photo (it may be bright), light glass on the gradient.
    val glass = if (hasPhoto) Color.Black.copy(alpha = 0.3f) else Color.White.copy(alpha = 0.2f)
    // Only the buttons' colours need composition; quantised so a fling
    // recomposes the bar a few dozen times, not every frame.
    val fill by remember(collapse) { derivedStateOf { (collapse.window(0.7f, 1f) * 20).roundToInt() / 20f } }
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    Column(
        Modifier
            .fillMaxWidth()
            .drawBehind {
                val a = collapse.window(0.7f, 1f)
                drawRect(surface.copy(alpha = a))
                if (a >= 1f) drawRect(hairline, topLeft = Offset(0f, size.height - 1f), size = Size(size.width, 1f))
            }
            .windowInsetsPadding(WindowInsets.statusBars)
    ) {
        Row(
            Modifier.fillMaxWidth().height(PinnedBarHeight).padding(horizontal = Space.page),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            MorphButton(BackIcon, "بازگشت", glass, onSurface, fill, onBack)
            Spacer(Modifier.width(Space.md))
            Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
                // The avatar pops in as the name lands beside it.
                Box(
                    Modifier
                        .size(32.dp)
                        .graphicsLayer {
                            val q = collapse.window(0.7f, 1f)
                            alpha = q
                            val s = mix(0.4f, 1f, BackOvershoot.transform(q))
                            scaleX = s
                            scaleY = s
                        }
                        .clip(RoundedCornerShape(10.dp))
                ) { BusinessAvatar(business, 32.dp) }
                Spacer(Modifier.width(Space.sm))
                Text(
                    business.title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        // Where the hero's flying name aims; measured before the
                        // fade, which never moves it.
                        .onGloballyPositioned { c ->
                            collapse.barTitleStart = c.startEdge(rtl)
                            collapse.barTitleCenterY = c.positionInRoot().y + c.size.height / 2f
                        }
                        .graphicsLayer {
                            // Takes over from the hero's white name at the same spot.
                            alpha = if (collapse.titlesPlaced) collapse.window(0.72f, 0.95f) else collapse.window(0.8f, 1f)
                        },
                )
            }
            Spacer(Modifier.width(Space.sm))
            MorphButton(Lucide.Share2, "اشتراک‌گذاری", glass, onSurface, fill, onShare)
            Spacer(Modifier.width(Space.sm))
            MorphButton(
                if (isSaved) Lucide.Bookmark.filled() else Lucide.Bookmark,
                if (isSaved) "حذف از نشان‌شده‌ها" else "نشان کردن",
                glass,
                if (isSaved) MaterialTheme.colorScheme.primary else onSurface,
                fill,
                onToggleSave,
            )
        }
    }
}

/** A glass circle with a white icon at [fill] 0, a bare icon in [solidTint] at 1. */
@Composable
private fun MorphButton(icon: ImageVector, label: String, glass: Color, solidTint: Color, fill: Float, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = CircleShape,
        color = lerp(glass, glass.copy(alpha = 0f), fill),
        modifier = Modifier.size(40.dp),
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(icon, contentDescription = label, tint = lerp(Color.White, solidTint, fill), modifier = Modifier.size(20.dp))
        }
    }
}

@Composable
private fun RoundAction(icon: ImageVector, label: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Surface(onClick = onClick, shape = CircleShape, color = Color.White, modifier = modifier.size(48.dp)) {
        Box(contentAlignment = Alignment.Center) {
            Icon(icon, contentDescription = label, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
        }
    }
}

@Composable
private fun RatingChip(avg: Double) {
    Row(
        Modifier.background(Color.White.copy(alpha = 0.2f), CircleShape).padding(horizontal = Space.sm, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Lucide.Star.filled(), contentDescription = null, tint = StarAmber, modifier = Modifier.size(14.dp))
        Spacer(Modifier.width(Space.xs))
        Text(formatRating(avg), style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, color = Color.White)
    }
}

private fun formatRating(avg: Double): String {
    // Rounded, like the reviews summary (4.57 reads 4.6 in both places).
    val tenths = (avg * 10).roundToInt()
    return "${tenths / 10}.${tenths % 10}".toPersianDigits()
}

/**
 * Three facts at a glance, on a card that overlaps the hero's lower edge:
 * rating, working hours, and distance (or insurers, or services).
 */
@Composable
internal fun StatsRow(business: Business, rating: ReviewSummaryDto?) {
    val stats = remember(business, rating) {
        buildList {
            if (rating != null && rating.count > 0) {
                add(Stat(Lucide.Star, formatRating(rating.avg), "${rating.count.toPersianDigits()} نظر"))
            } else {
                add(Stat(Lucide.MessageSquare, "تازه", "بدون نظر"))
            }
            if (business.isClaimed) {
                add(Stat(Lucide.Clock, hoursLabel(business), "ساعت کاری"))
            }
            val km = business.distanceKm
            when {
                km != null -> add(Stat(Lucide.MapPin, formatDistance(km), "تا شما"))
                business.insurances.isNotEmpty() -> add(Stat(Lucide.ShieldPlus, business.insurances.size.toPersianDigits(), "بیمه"))
                business.services.isNotEmpty() -> add(Stat(Lucide.Star, business.services.size.toPersianDigits(), "خدمت"))
            }
        }
    }
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surface,
        shadowElevation = 6.dp,
        modifier = Modifier
            .fillMaxWidth()
            .offset(y = (-28).dp)
            .padding(horizontal = Space.page),
    ) {
        Row(Modifier.padding(vertical = Space.lg, horizontal = Space.sm)) {
            stats.forEach { StatItem(it, Modifier.weight(1f)) }
        }
    }
}

private data class Stat(val icon: ImageVector, val value: String, val label: String)

private fun hoursLabel(business: Business): String {
    fun t(h: Int, m: Int) = (if (m == 0) "$h" else "$h:${m.toString().padStart(2, '0')}")
    return "${t(business.workStartHour, business.workStartMinute)} تا ${t(business.workEndHour, business.workEndMinute)}".toPersianDigits()
}

@Composable
private fun StatItem(stat: Stat, modifier: Modifier) {
    val soft = if (LocalIsDarkTheme.current) AccentSoftDark else AccentSoft
    Row(modifier.padding(horizontal = Space.xs), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(36.dp).background(soft, CircleShape), contentAlignment = Alignment.Center) {
            Icon(stat.icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
        }
        Spacer(Modifier.width(6.dp))
        Column {
            Text(stat.value, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary, maxLines = 1)
            Text(stat.label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
        }
    }
}

/**
 * Day and time right on the page: the booking flow's day strip, then the
 * picked day's free times as a row of pills. Picking one carries it into the
 * booking flow, which opens on it.
 */
@Composable
internal fun QuickPickSection(
    state: ClientBusinessDetailState,
    index: Int,
    onSelectDay: (Int) -> Unit,
    onSelectSlot: (TimeSlotDto) -> Unit,
) {
    GroupCard(title = "انتخاب روز و ساعت", index = index, bleed = {
        DayStrip(state.days, state.selectedDay, onSelectDay)
        Spacer(Modifier.height(Space.lg))
        val free = state.slots.filter { it.available }
        val day = state.days.getOrNull(state.selectedDay)
        when {
            state.slotsLoading -> Row(
                Modifier.padding(horizontal = Space.page),
                horizontalArrangement = Arrangement.spacedBy(Space.sm),
            ) { repeat(4) { ShimmerBox(Modifier.width(84.dp).height(40.dp), CircleShape) } }
            day?.closed == true -> EmptyLine("این روز تعطیل است")
            free.isEmpty() -> EmptyLine("نوبت خالی در این روز نیست")
            else -> LazyRow(
                contentPadding = PaddingValues(horizontal = Space.page),
                horizontalArrangement = Arrangement.spacedBy(Space.sm),
            ) {
                items(free, key = { it.timestamp }) { slot ->
                    TimePill(slot, slot == state.selectedSlot) { onSelectSlot(slot) }
                }
            }
        }
    })
}

@Composable
private fun EmptyLine(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = Space.page),
    )
}

@Composable
private fun TimePill(slot: TimeSlotDto, selected: Boolean, onClick: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    val soft = if (LocalIsDarkTheme.current) AccentSoftDark else AccentSoft
    val bg by animateColorAsState(if (selected) scheme.primary else soft, label = "pillBg")
    val fg by animateColorAsState(if (selected) scheme.onPrimary else scheme.primary, label = "pillFg")
    Surface(
        onClick = onClick,
        shape = CircleShape,
        color = bg,
        border = if (selected) null else BorderStroke(1.dp, scheme.primary.copy(alpha = 0.15f)),
        modifier = Modifier.height(40.dp),
    ) {
        Row(Modifier.padding(horizontal = Space.lg), verticalAlignment = Alignment.CenterVertically) {
            Icon(Lucide.Clock, contentDescription = null, tint = fg, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(6.dp))
            Text(
                slot.time.toPersianDigits(),
                style = MaterialTheme.typography.labelLarge,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                color = fg,
            )
        }
    }
}
