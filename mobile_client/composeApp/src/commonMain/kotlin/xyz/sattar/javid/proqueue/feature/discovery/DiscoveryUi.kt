package xyz.sattar.javid.proqueue.feature.discovery

import org.koin.compose.koinInject
import xyz.sattar.javid.proqueue.feature.stories.StoryCenter
import kotlin.time.Clock
import androidx.compose.ui.graphics.Brush
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.runtime.collectAsState

import xyz.sattar.javid.proqueue.ui.theme.AccentSoftDark
import xyz.sattar.javid.proqueue.ui.theme.AccentSoft
import xyz.sattar.javid.proqueue.feature.booking.pressScale
import xyz.sattar.javid.proqueue.core.utils.rememberReducedMotion
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.draw.drawBehind
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.material3.Button
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Animatable
import com.composables.icons.lucide.RefreshCw
import xyz.sattar.javid.proqueue.domain.model.business.BusinessCategoryGroup
import xyz.sattar.javid.proqueue.domain.model.business.BusinessCategory
import com.composables.icons.lucide.Store
import com.composables.icons.lucide.GraduationCap
import com.composables.icons.lucide.HeartPulse
import com.composables.icons.lucide.Check
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.ArrowRight
import com.composables.icons.lucide.BadgeCheck
import com.composables.icons.lucide.Bookmark
import com.composables.icons.lucide.Brain
import com.composables.icons.lucide.Briefcase
import com.composables.icons.lucide.ChevronLeft
import com.composables.icons.lucide.Dumbbell
import com.composables.icons.lucide.LayoutGrid
import com.composables.icons.lucide.Scissors
import com.composables.icons.lucide.Stethoscope
import xyz.sattar.javid.proqueue.core.ui.icons.filled
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import xyz.sattar.javid.proqueue.core.utils.AppConfig
import xyz.sattar.javid.proqueue.domain.model.business.Business
import xyz.sattar.javid.proqueue.ui.theme.ErrorColor
import xyz.sattar.javid.proqueue.ui.theme.ErrorColorDark
import xyz.sattar.javid.proqueue.ui.theme.LocalIsDarkTheme
import xyz.sattar.javid.proqueue.domain.model.business.acceptsInsurance
import xyz.sattar.javid.proqueue.ui.theme.SuccessColor
import xyz.sattar.javid.proqueue.ui.theme.SuccessColorDark
import xyz.sattar.javid.proqueue.ui.theme.WarningColor
import kotlin.math.roundToInt

// ── Design tokens ────────────────────────────────────────────────────────────
//
// One grid, two radii. Anything not on these values is a bug, not a style.

object Space {
    val xs = 4.dp
    val sm = 8.dp
    val md = 12.dp
    val lg = 16.dp
    val xl = 24.dp
    /** Horizontal page margin. */
    val page = 16.dp
}

object Radius {
    val sm = RoundedCornerShape(8.dp)
    val md = RoundedCornerShape(12.dp)
}

fun logoUrl(business: Business): String? = business.logoPath.takeIf { it.isNotBlank() }?.let {
    if (it.startsWith("http")) it else "${AppConfig.baseUrl}$it"
}

fun bookingUrl(business: Business): String? =
    business.uniqueCode?.takeIf { it.isNotBlank() }?.let { "${AppConfig.bookingBaseUrl}/b/Noobatyar-$it" }

/** A claimed business with booking switched on — the only kind that can take an appointment. */
val Business.canBookOnline: Boolean get() = isClaimed && bookingEnabled

// ── Categories ───────────────────────────────────────────────────────────────

/**
 * Client-side mirror of the backend's CATEGORY_GROUPS keys — the list endpoint
 * accepts a group key and expands it server-side. Icons are all one outlined
 * family and all one colour; the label carries the meaning.
 */
enum class CategoryGroup(
    val key: String?,
    val label: String,
    /** One word, for the explore bar where all groups share a single row. */
    val shortLabel: String,
    val icon: ImageVector,
    /** Whether the insurance filter means anything here: a salon or a school has none. */
    val supportsInsurance: Boolean = false,
) {
    ALL(null, "همه", "همه", Lucide.LayoutGrid, supportsInsurance = true),
    MEDICAL("MEDICAL", "پزشک", "پزشک", Lucide.Stethoscope, supportsInsurance = true),
    DENTAL("DENTAL", "دندان‌پزشک", "دندان", ToothOutlined, supportsInsurance = true),
    BEAUTY("BEAUTY", "زیبایی", "زیبایی", Lucide.Scissors),
    HEALTH("HEALTH", "سلامت و مشاوره", "مشاوره", Lucide.Brain, supportsInsurance = true),
    PROFESSIONAL("PROFESSIONAL", "خدمات حرفه‌ای", "خدمات", Lucide.Briefcase),
    EDUCATION("EDUCATION", "آموزش و ورزش", "آموزش", Lucide.Dumbbell);

    companion object {
        fun fromKey(key: String?): CategoryGroup = entries.firstOrNull { it.key == key } ?: ALL
    }
}

// ── Formatting ───────────────────────────────────────────────────────────────

private const val PERSIAN_DIGITS = "۰۱۲۳۴۵۶۷۸۹"

fun String.toPersianDigits(): String = map { c ->
    if (c in '0'..'9') PERSIAN_DIGITS[c - '0'] else c
}.joinToString("")

fun Int.toPersianDigits(): String = toString().toPersianDigits()

fun formatDistance(km: Double): String = when {
    km < 1.0 -> "${((km * 1000) / 10).roundToInt() * 10} متر".toPersianDigits()
    km < 10.0 -> {
        val tenths = (km * 10).roundToInt()
        "${tenths / 10}٫${tenths % 10} کیلومتر".toPersianDigits()
    }
    else -> "${km.roundToInt()} کیلومتر".toPersianDigits()
}

fun Business.areaLine(): String =
    listOf(neighborhood, city).filter { it.isNotBlank() }.distinct().joinToString("، ")

/** Words that open most listing names and say nothing about *which* one it is. */
private val GENERIC_PREFIXES = setOf(
    "مطب", "کلینیک", "مرکز", "سالن", "آرایشگاه", "دندان‌پزشکی", "دندانپزشکی", "دکتر", "تخصصی",
    "فوق", "آزمایشگاه", "داروخانه", "موسسه", "دفتر", "آموزشگاه", "باشگاه", "زیبایی", "پزشکی",
)

/** The letter that identifies a listing: "مطب دکتر احمدی" → "ا", not "م". */
fun Business.monogram(): String {
    val words = title.split(' ', '‌').filter { it.isNotBlank() }
    val doctorIdx = words.indexOf("دکتر")
    val pick = when {
        doctorIdx >= 0 && doctorIdx + 1 < words.size -> words[doctorIdx + 1]
        else -> words.lastOrNull { it !in GENERIC_PREFIXES } ?: words.firstOrNull().orEmpty()
    }
    return pick.take(1)
}

// ── Atoms ────────────────────────────────────────────────────────────────────

/** Instagram-style ring around a business with a story up; tapping it opens the story. */
private val StoryRing = Brush.sweepGradient(listOf(Color(0xFFA78BFA), Color(0xFF7C3AED), Color(0xFFC026D3), Color(0xFFA78BFA)))

/**
 * [corner] is the outer corner radius; callers that clip the avatar to a
 * rounder frame pass theirs so the story ring follows it. Inside the ring the
 * photo uses the concentric radius, so the gap stays even at the corners.
 */
@Composable
fun BusinessAvatar(business: Business, size: Dp, corner: Dp = 12.dp) {
    val stories = koinInject<StoryCenter>()
    val active by stories.activeIds.collectAsState()
    LaunchedEffect(Unit) { stories.refresh(Clock.System.now().toEpochMilliseconds()) }
    if (business.id in active) {
        val ring = if (size >= 48.dp) 3.dp else 2.dp
        val inset = ring + 2.dp
        val outer = RoundedCornerShape(corner)
        Box(
            Modifier
                .size(size)
                .clip(outer)
                .border(ring, StoryRing, outer)
                .clickable { stories.open(business.id) }
                .padding(inset),
            contentAlignment = Alignment.Center
        ) { PlainBusinessAvatar(business, size - inset * 2, (corner - inset).coerceAtLeast(4.dp)) }
    } else {
        PlainBusinessAvatar(business, size, corner)
    }
}

@Composable
private fun PlainBusinessAvatar(business: Business, size: Dp, corner: Dp) {
    val url = logoUrl(business)
    val shape = RoundedCornerShape(corner)
    val claimed = business.isClaimed
    Box(
        modifier = Modifier
            .size(size)
            .clip(shape)
            .background(
                if (claimed) MaterialTheme.colorScheme.primaryContainer
                else MaterialTheme.colorScheme.surfaceVariant
            ),
        contentAlignment = Alignment.Center
    ) {
        if (url != null) {
            AsyncImage(
                model = url,
                contentDescription = business.title,
                contentScale = ContentScale.Crop,
                modifier = Modifier.size(size)
            )
        } else {
            // No logo: the category's icon (the same ones as the map pins and
            // filter chips) says more than a first letter.
            Icon(
                business.categoryIcon(),
                contentDescription = null,
                tint = if (claimed) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(size * 0.46f)
            )
        }
    }
}

/** One outlined icon per category group, shared by avatars without a logo. */
fun Business.categoryIcon(): ImageVector = when (BusinessCategory.fromString(category).group) {
    BusinessCategoryGroup.MEDICAL -> Lucide.Stethoscope
    BusinessCategoryGroup.DENTAL -> ToothOutlined
    BusinessCategoryGroup.HEALTH -> Lucide.HeartPulse
    BusinessCategoryGroup.BEAUTY -> Lucide.Scissors
    BusinessCategoryGroup.PROFESSIONAL -> Lucide.Briefcase
    BusinessCategoryGroup.EDUCATION -> Lucide.GraduationCap
    BusinessCategoryGroup.TRADES, BusinessCategoryGroup.OTHER -> Lucide.Store
}

@Composable
fun statusColor(tone: OpenTone): Color {
    val dark = LocalIsDarkTheme.current
    return when (tone) {
        OpenTone.OPEN -> if (dark) SuccessColorDark else SuccessColor
        OpenTone.CLOSING_SOON -> WarningColor
        OpenTone.CLOSED -> if (dark) ErrorColorDark else ErrorColor
    }
}

/** "باز است" in status colour, the rest in secondary text — colour only where it carries meaning. */
@Composable
fun OpenStatusText(status: OpenStatus, modifier: Modifier = Modifier) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        Text(
            status.headline,
            color = statusColor(status.tone),
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.Medium
        )
        if (status.detail.isNotBlank()) {
            Text(
                " · ${status.detail}",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodySmall
            )
        }
    }
}

@Composable
fun OnlineBadge() {
    Text(
        "نوبت آنلاین",
        color = MaterialTheme.colorScheme.primary,
        fontSize = 11.sp,
        fontWeight = FontWeight.Bold,
        modifier = Modifier
            .clip(Radius.sm)
            .background(MaterialTheme.colorScheme.primaryContainer)
            .padding(horizontal = 6.dp, vertical = 2.dp)
    )
}

@Composable
fun VerifiedMark(size: Dp = 16.dp) {
    Icon(
        Lucide.BadgeCheck,
        contentDescription = "ثبت‌شده در نوبت‌یار",
        tint = MaterialTheme.colorScheme.primary,
        modifier = Modifier.size(size)
    )
}

@Composable
fun SaveButton(saved: Boolean, onToggle: () -> Unit) {
    val tint by animateColorAsState(
        if (saved) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
    )
    IconButton(onClick = onToggle) {
        Icon(
            if (saved) Lucide.Bookmark.filled() else Lucide.Bookmark,
            contentDescription = if (saved) "حذف از نشان‌شده‌ها" else "نشان کردن",
            tint = tint
        )
    }
}

val BackIcon = Lucide.ArrowRight
/** "Go into" chevron — AutoMirrored, so it points left in RTL. */
val ChevronIcon = Lucide.ChevronLeft

@Composable
fun Hairline(modifier: Modifier = Modifier) {
    HorizontalDivider(modifier = modifier, thickness = 1.dp, color = MaterialTheme.colorScheme.outlineVariant)
}

/** The one chip style in the app. Selected = filled accent; otherwise a quiet outline. */
@Composable
fun FilterChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    icon: ImageVector? = null,
    trailingIcon: ImageVector? = null,
) {
    val bg by animateColorAsState(if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface)
    val fg by animateColorAsState(if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface)
    Surface(
        onClick = onClick,
        shape = CircleShape,
        color = bg,
        border = if (selected) null else BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
    ) {
        Row(
            modifier = Modifier.height(36.dp).padding(horizontal = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (icon != null) {
                Icon(icon, contentDescription = null, tint = fg, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
            }
            Text(label, color = fg, style = MaterialTheme.typography.labelLarge, maxLines = 1)
            if (trailingIcon != null) {
                Spacer(Modifier.width(2.dp))
                Icon(trailingIcon, contentDescription = null, tint = fg, modifier = Modifier.size(18.dp))
            }
        }
    }
}

@Composable
fun SectionTitle(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    action: (@Composable () -> Unit)? = null,
) {
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            if (subtitle != null) {
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        action?.invoke()
    }
}

// ── Rows & cards ─────────────────────────────────────────────────────────────

/**
 * A result in the list. Flat, divider-separated rows rather than floating
 * cards — the density people expect from a directory (Doctolib, Google Maps).
 */
@Composable
fun BusinessRow(
    business: Business,
    saved: Boolean,
    onToggleSave: () -> Unit,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    /** The row whose pin is selected on the map. */
    highlighted: Boolean = false,
) {
    val status = remember(business) { business.openStatus() }
    val bg by animateColorAsState(
        if (highlighted) MaterialTheme.colorScheme.primary.copy(alpha = 0.07f)
            .compositeOver(MaterialTheme.colorScheme.background)
        else MaterialTheme.colorScheme.background
    )
    Surface(onClick = onClick, color = bg, modifier = modifier.fillMaxWidth()) {
        Row(
            Modifier.padding(start = Space.page, end = Space.xs, top = Space.md, bottom = Space.md),
            verticalAlignment = Alignment.Top
        ) {
            BusinessAvatar(business, 56.dp)
            Spacer(Modifier.width(Space.md))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        business.title,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    if (business.isClaimed) {
                        Spacer(Modifier.width(Space.xs))
                        VerifiedMark()
                    }
                }
                Spacer(Modifier.height(2.dp))
                val area = business.areaLine()
                Text(
                    if (area.isNotBlank()) "${business.categoryLabel} · $area" else business.categoryLabel,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.height(6.dp))
                MetaLine(business, status)
                val freeSlot = remember(business.firstFreeSlot) { formatFirstFreeSlot(business.firstFreeSlot) }
                if (freeSlot != null) {
                    Spacer(Modifier.height(2.dp))
                    Text(
                        "اولین نوبت خالی: $freeSlot",
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
            SaveButton(saved, onToggleSave)
        }
    }
}

/** The user's own insurance (a slug), for the «بیمه شما را قبول می‌کند» marks; set once at the nav host. */
val LocalMyInsuranceSlug = androidx.compose.runtime.compositionLocalOf<String?> { null }

/** distance · open status · insurances, with the online badge leading when bookable. */
@Composable
private fun MetaLine(business: Business, status: OpenStatus?) {
    val secondary = MaterialTheme.colorScheme.onSurfaceVariant
    val accepts = business.acceptsInsurance(LocalMyInsuranceSlug.current)
    val success = if (LocalIsDarkTheme.current) SuccessColorDark else SuccessColor
    Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            if (business.canBookOnline) OnlineBadge()
            val parts = buildList {
                business.distanceKm?.let { add(formatDistance(it)) }
                if (business.insurances.isNotEmpty() && !accepts) add("${business.insurances.size.toPersianDigits()} بیمه")
            }
            if (status != null) {
                OpenStatusText(status)
                if (parts.isNotEmpty()) Text("·", color = secondary, style = MaterialTheme.typography.bodySmall)
            }
            if (parts.isNotEmpty()) {
                Text(
                    parts.joinToString(" · "),
                    color = secondary,
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
        // The user's own insurance is accepted: its own line, so it never crowds the status.
        if (accepts) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                Icon(Lucide.Check, contentDescription = null, tint = success, modifier = Modifier.size(14.dp))
                Text("بیمه شما قبول است", color = success, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Medium, maxLines = 1)
            }
        }
    }
}

/** Compact card for the horizontal "book online nearby" strip. */
@Composable
fun BookableCard(business: Business, onClick: () -> Unit) {
    val status = remember(business) { business.openStatus() }
    Surface(
        onClick = onClick,
        shape = Radius.md,
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        modifier = Modifier.width(248.dp)
    ) {
        Row(Modifier.padding(Space.md), verticalAlignment = Alignment.CenterVertically) {
            BusinessAvatar(business, 48.dp)
            Spacer(Modifier.width(Space.md))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        business.title,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    Spacer(Modifier.width(Space.xs))
                    VerifiedMark(14.dp)
                }
                Text(
                    business.categoryLabel,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1
                )
                Spacer(Modifier.height(Space.xs))
                if (status != null) OpenStatusText(status)
                else business.distanceKm?.let {
                    Text(formatDistance(it), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

/** Plain bordered row inviting owners in. No gradient: an invitation, not an ad. */
@Composable
fun OwnerInviteRow(onClick: () -> Unit, modifier: Modifier = Modifier) {
    Surface(
        onClick = onClick,
        shape = Radius.md,
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        modifier = modifier.fillMaxWidth()
    ) {
        Row(Modifier.padding(Space.lg), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("صاحب کسب‌وکار هستید؟", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(2.dp))
                Text(
                    "مرکزتان را ثبت کنید و آنلاین نوبت بدهید.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(Modifier.width(Space.sm))
            Icon(ChevronIcon, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/**
 * Icon · title · body · one action, for empty, error and permission states.
 * Deliberately quiet: a small muted glyph and plain text, the way a directory
 * app says "nothing here" without dressing it up.
 */
@Composable
fun StateMessage(
    icon: ImageVector,
    title: String,
    body: String,
    actionLabel: String? = null,
    onAction: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    Column(
        modifier.fillMaxWidth().padding(horizontal = 40.dp, vertical = Space.xl),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(28.dp))
        Spacer(Modifier.height(Space.md))
        Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
        Spacer(Modifier.height(Space.xs))
        Text(
            body,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
        if (actionLabel != null) {
            Spacer(Modifier.height(Space.lg))
            OutlinedButton(
                onClick = onAction,
                shape = Radius.md,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                contentPadding = PaddingValues(horizontal = Space.xl, vertical = Space.sm)
            ) {
                Text(actionLabel, color = MaterialTheme.colorScheme.onSurface)
            }
        }
    }
}

/** Settings-style row: outlined icon, label, optional value, chevron. */
@Composable
fun ListRow(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
    value: String? = null,
    showChevron: Boolean = true,
) {
    Surface(onClick = onClick, color = Color.Transparent, modifier = Modifier.fillMaxWidth()) {
        Row(
            Modifier.height(56.dp).padding(horizontal = Space.page),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(22.dp))
            Spacer(Modifier.width(Space.lg))
            Text(label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
            if (value != null) {
                Text(value, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.width(Space.xs))
            }
            if (showChevron) {
                Icon(ChevronIcon, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(20.dp))
            }
        }
    }
}
