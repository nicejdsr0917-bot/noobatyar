package xyz.sattar.javid.proqueue.feature.discovery

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.offset
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Bookmark
import androidx.compose.material.icons.rounded.BookmarkBorder
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.BusinessCenter
import androidx.compose.material.icons.rounded.ContentCut
import androidx.compose.material.icons.rounded.FitnessCenter
import androidx.compose.material.icons.rounded.HealthAndSafety
import androidx.compose.material.icons.rounded.LocalHospital
import androidx.compose.material.icons.rounded.MedicalServices
import androidx.compose.material.icons.rounded.NearMe
import androidx.compose.material.icons.rounded.Storefront
import androidx.compose.material.icons.rounded.Verified
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import xyz.sattar.javid.proqueue.core.utils.AppConfig
import xyz.sattar.javid.proqueue.domain.model.business.Business
import xyz.sattar.javid.proqueue.ui.theme.LocalIsDarkTheme
import kotlin.math.roundToInt

// ── Brand ────────────────────────────────────────────────────────────────────

object Brand {
    val Violet900 = Color(0xFF3B1782)
    val Violet700 = Color(0xFF6D28D9)
    val Violet500 = Color(0xFF8B5CF6)
    val Violet300 = Color(0xFFC4B5FD)
    val Violet50 = Color(0xFFF5F3FF)
    val Amber = Color(0xFFF59E0B)
    val Emerald = Color(0xFF10B981)

    val HeroGradient = Brush.linearGradient(listOf(Violet900, Violet700, Violet500))
    val FeaturedGradient = Brush.linearGradient(listOf(Violet700, Color(0xFFA855F7), Color(0xFFEC4899)))
}

/** Public web page the owner app / landing lives on — the "register your business" CTA target. */
const val OWNER_LANDING_URL = "https://noobatyar.ir"

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
 * accepts a group key and expands it server-side, so this only needs the
 * label/icon/tint per group plus enough of the membership to theme a card.
 */
enum class CategoryGroup(
    val key: String?,
    val label: String,
    val icon: ImageVector,
    val tint: Color,
) {
    ALL(null, "همه", Icons.Rounded.Storefront, Color(0xFF8B5CF6)),
    MEDICAL("MEDICAL", "پزشکان", Icons.Rounded.LocalHospital, Color(0xFF0EA5E9)),
    DENTAL("DENTAL", "دندان‌پزشکی", Icons.Rounded.MedicalServices, Color(0xFF14B8A6)),
    BEAUTY("BEAUTY", "زیبایی", Icons.Rounded.ContentCut, Color(0xFFEC4899)),
    HEALTH("HEALTH", "سلامت و مشاوره", Icons.Rounded.HealthAndSafety, Color(0xFF22C55E)),
    PROFESSIONAL("PROFESSIONAL", "خدمات حرفه‌ای", Icons.Rounded.BusinessCenter, Color(0xFFF59E0B)),
    EDUCATION("EDUCATION", "آموزش و ورزش", Icons.Rounded.FitnessCenter, Color(0xFF6366F1));

    companion object {
        private val DENTAL_CODES = setOf("DENTIST", "ORTHODONTICS", "DENTAL_IMPLANT", "PEDIATRIC_DENTISTRY")
        private val BEAUTY_CODES = setOf(
            "BEAUTY_SALON", "WOMENS_SALON", "BARBERSHOP", "HAIR_SALON", "HAIR_TRANSPLANT", "MAKEUP",
            "BRIDAL", "NAIL_SALON", "EYEBROW_LASH", "SKIN_LASER", "TATTOO", "MASSAGE_SPA", "SLIMMING",
        )
        private val HEALTH_CODES = setOf(
            "PSYCHOLOGY", "PHYSIOTHERAPY", "NUTRITION", "LABORATORY", "RADIOLOGY", "OPTOMETRY",
            "SPEECH_THERAPY", "OCCUPATIONAL_THERAPY", "MIDWIFERY", "NURSING", "PHARMACY", "VETERINARY",
        )
        private val PROFESSIONAL_CODES = setOf(
            "CONSULTANT", "LAWYER", "ACCOUNTING", "REAL_ESTATE", "INSURANCE", "IMMIGRATION",
        )
        private val EDUCATION_CODES = setOf(
            "TUTORING", "LANGUAGE_SCHOOL", "MUSIC_SCHOOL", "GYM", "YOGA_PILATES", "DRIVING_SCHOOL",
        )
        private val MEDICAL_CODES = setOf(
            "DOCTOR", "GENERAL_PRACTITIONER", "INTERNAL_MEDICINE", "CARDIOLOGY", "DERMATOLOGY",
            "ORTHOPEDICS", "PEDIATRICS", "GYNECOLOGY", "ENT", "OPHTHALMOLOGY", "NEUROLOGY", "UROLOGY",
            "ENDOCRINOLOGY", "GASTROENTEROLOGY", "ONCOLOGY", "SURGERY", "PSYCHIATRY", "INFERTILITY",
        )

        fun of(categoryCode: String): CategoryGroup = when (categoryCode) {
            in MEDICAL_CODES -> MEDICAL
            in DENTAL_CODES -> DENTAL
            in BEAUTY_CODES -> BEAUTY
            in HEALTH_CODES -> HEALTH
            in PROFESSIONAL_CODES -> PROFESSIONAL
            in EDUCATION_CODES -> EDUCATION
            else -> ALL
        }

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

// ── Atoms ────────────────────────────────────────────────────────────────────

@Composable
fun BusinessAvatar(business: Business, size: Dp, corner: Dp = 16.dp) {
    val group = CategoryGroup.of(business.category)
    val url = logoUrl(business)
    Box(
        modifier = Modifier
            .size(size)
            .clip(RoundedCornerShape(corner))
            .background(
                Brush.linearGradient(listOf(group.tint.copy(alpha = 0.22f), group.tint.copy(alpha = 0.08f)))
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
            Icon(group.icon, contentDescription = null, tint = group.tint, modifier = Modifier.size(size * 0.48f))
        }
    }
}

@Composable
fun VerifiedBadge(compact: Boolean = false) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(Brand.FeaturedGradient)
            .padding(horizontal = if (compact) 6.dp else 8.dp, vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(Icons.Rounded.Bolt, contentDescription = null, tint = Color.White, modifier = Modifier.size(12.dp))
        if (!compact) {
            Spacer(Modifier.width(3.dp))
            Text("نوبت آنلاین", color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
fun Pill(
    text: String,
    icon: ImageVector? = null,
    container: Color = MaterialTheme.colorScheme.surfaceVariant,
    content: Color = MaterialTheme.colorScheme.onSurfaceVariant,
) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(container)
            .padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (icon != null) {
            Icon(icon, contentDescription = null, tint = content, modifier = Modifier.size(12.dp))
            Spacer(Modifier.width(3.dp))
        }
        Text(text, color = content, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
fun SaveToggle(saved: Boolean, onToggle: () -> Unit, onDark: Boolean = false) {
    val tint by animateColorAsState(
        when {
            saved -> Brand.Violet500
            onDark -> Color.White
            else -> MaterialTheme.colorScheme.onSurfaceVariant
        }
    )
    IconButton(onClick = onToggle) {
        Icon(
            if (saved) Icons.Rounded.Bookmark else Icons.Rounded.BookmarkBorder,
            contentDescription = if (saved) "حذف از نشان‌شده‌ها" else "نشان کردن",
            tint = tint
        )
    }
}

@Composable
fun CircleIconButton(icon: ImageVector, description: String, onClick: () -> Unit, onDark: Boolean = true) {
    Surface(
        onClick = onClick,
        shape = CircleShape,
        color = if (onDark) Color.White.copy(alpha = 0.18f) else MaterialTheme.colorScheme.surfaceVariant,
        modifier = Modifier.size(40.dp)
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(icon, contentDescription = description, tint = if (onDark) Color.White else MaterialTheme.colorScheme.onSurface)
        }
    }
}

val BackIcon = Icons.AutoMirrored.Rounded.ArrowBack

// ── Cards ────────────────────────────────────────────────────────────────────

@Composable
fun OpenStatusLabel(status: OpenStatus, onDark: Boolean = false) {
    val color = when (status.tone) {
        OpenTone.OPEN -> if (onDark) Color(0xFF86EFAC) else Brand.Emerald
        OpenTone.CLOSING_SOON -> Brand.Amber
        OpenTone.CLOSED -> if (onDark) Color(0xFFFCA5A5) else Color(0xFFEF4444)
    }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(7.dp).clip(CircleShape).background(color))
        Spacer(Modifier.width(6.dp))
        Text(status.headline, color = color, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
        if (status.detail.isNotBlank()) {
            Text(
                " · ${status.detail}",
                color = if (onDark) Color.White.copy(alpha = 0.8f) else MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.labelMedium
            )
        }
    }
}

/** Slight shrink while pressed — the tactile cue native lists give. */
@Composable
fun Modifier.pressScale(interaction: MutableInteractionSource): Modifier {
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) 0.97f else 1f, label = "pressScale")
    return this.graphicsLayer { scaleX = scale; scaleY = scale }
}

@Composable
fun BusinessListCard(
    business: Business,
    saved: Boolean,
    onToggleSave: () -> Unit,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    onBook: (() -> Unit)? = null,
) {
    val isDark = LocalIsDarkTheme.current
    val highlighted = business.canBookOnline
    val group = CategoryGroup.of(business.category)
    val status = remember(business) { business.openStatus() }
    val interaction = remember { MutableInteractionSource() }
    Surface(
        onClick = onClick,
        interactionSource = interaction,
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surface,
        shadowElevation = if (isDark) 0.dp else 2.dp,
        border = if (highlighted) BorderStroke(1.5.dp, Brand.FeaturedGradient)
        else BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = if (isDark) 0.35f else 0.5f)),
        modifier = modifier.fillMaxWidth().pressScale(interaction)
    ) {
        Column {
            Row(
                modifier = Modifier.padding(start = 14.dp, top = 14.dp, end = 4.dp),
                verticalAlignment = Alignment.Top
            ) {
                Box {
                    BusinessAvatar(business, size = 68.dp, corner = 20.dp)
                    if (business.isClaimed) {
                        Box(
                            Modifier
                                .align(Alignment.BottomEnd)
                                .offset(x = 4.dp, y = 4.dp)
                                .size(22.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.surface)
                                .padding(2.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Rounded.Verified, contentDescription = "ثبت‌شده در نوبت‌یار", tint = Brand.Violet500)
                        }
                    }
                }
                Spacer(Modifier.width(14.dp))
                Column(modifier = Modifier.weight(1f).padding(top = 2.dp)) {
                    Text(
                        business.title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        lineHeight = 22.sp
                    )
                    Spacer(Modifier.height(2.dp))
                    val area = business.areaLine()
                    Text(
                        if (area.isNotBlank()) "${business.categoryLabel} · $area" else business.categoryLabel,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (status != null) {
                        Spacer(Modifier.height(6.dp))
                        OpenStatusLabel(status)
                    }
                }
                SaveToggle(saved, onToggleSave)
            }

            Spacer(Modifier.height(12.dp))
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(1.dp)
                    .background(MaterialTheme.colorScheme.outline.copy(alpha = 0.25f))
            )
            Row(
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    modifier = Modifier.weight(1f),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    business.distanceKm?.let { Pill(formatDistance(it), Icons.Rounded.NearMe) }
                    if (business.insurances.isNotEmpty()) {
                        Pill(
                            "${business.insurances.size.toPersianDigits()} بیمه",
                            Icons.Rounded.HealthAndSafety,
                            container = Brand.Emerald.copy(alpha = 0.12f),
                            content = Brand.Emerald
                        )
                    }
                    if (business.distanceKm == null && business.insurances.isEmpty()) {
                        Pill(group.label, group.icon, container = group.tint.copy(alpha = 0.1f), content = group.tint)
                    }
                }
                if (highlighted && onBook != null) {
                    Surface(onClick = onBook, shape = RoundedCornerShape(50), color = Color.Transparent) {
                        Row(
                            Modifier
                                .background(Brand.FeaturedGradient)
                                .padding(horizontal = 14.dp, vertical = 7.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Rounded.Bolt, contentDescription = null, tint = Color.White, modifier = Modifier.size(14.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("رزرو", color = Color.White, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
                        }
                    }
                } else if (!business.isClaimed) {
                    Text(
                        "اطلاعات عمومی",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                    )
                }
            }
        }
    }
}

@Composable
fun FeaturedBusinessCard(business: Business, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(24.dp),
        color = Color.Transparent,
        modifier = Modifier.width(220.dp)
    ) {
        Column(
            modifier = Modifier
                .background(Brand.FeaturedGradient)
                .padding(14.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(16.dp))
                        .border(2.dp, Color.White.copy(alpha = 0.6f), RoundedCornerShape(16.dp))
                ) { BusinessAvatar(business, size = 52.dp) }
                Spacer(Modifier.weight(1f))
                Icon(Icons.Rounded.Verified, contentDescription = null, tint = Color.White)
            }
            Spacer(Modifier.height(12.dp))
            Text(
                business.title,
                color = Color.White,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                business.categoryLabel,
                color = Color.White.copy(alpha = 0.85f),
                style = MaterialTheme.typography.bodySmall,
                maxLines = 1
            )
            business.openStatus()?.let {
                Spacer(Modifier.height(6.dp))
                OpenStatusLabel(it, onDark = true)
            }
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Pill(
                    "رزرو آنی",
                    Icons.Rounded.Bolt,
                    container = Color.White.copy(alpha = 0.2f),
                    content = Color.White
                )
                business.distanceKm?.let {
                    Pill(formatDistance(it), Icons.Rounded.NearMe, container = Color.White.copy(alpha = 0.2f), content = Color.White)
                }
            }
        }
    }
}

@Composable
fun SectionHeader(title: String, subtitle: String? = null, modifier: Modifier = Modifier, action: (@Composable () -> Unit)? = null) {
    Row(modifier = modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            if (subtitle != null) {
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        action?.invoke()
    }
}

@Composable
fun OwnerInviteBanner(onClick: () -> Unit, modifier: Modifier = Modifier) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(24.dp),
        color = Color.Transparent,
        modifier = modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .background(Brand.HeroGradient)
                .padding(18.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text("کسب‌وکار دارید؟", color = Brand.Violet300, style = MaterialTheme.typography.labelLarge)
                Spacer(Modifier.height(4.dp))
                Text(
                    "نوبت‌دهی آنلاین را رایگان فعال کنید",
                    color = Color.White,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    "مرکز شما با نشان «نوبت آنلاین» بالای نتایج دیده می‌شود.",
                    color = Color.White.copy(alpha = 0.8f),
                    style = MaterialTheme.typography.bodySmall
                )
            }
            Spacer(Modifier.width(12.dp))
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .clip(CircleShape)
                    .background(Color.White.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Rounded.Storefront, contentDescription = null, tint = Color.White, modifier = Modifier.size(28.dp))
            }
        }
    }
}

@Composable
fun Modifier.clickableNoRipple(onClick: () -> Unit): Modifier = this.clickable(
    interactionSource = null,
    indication = null,
    onClick = onClick
)
