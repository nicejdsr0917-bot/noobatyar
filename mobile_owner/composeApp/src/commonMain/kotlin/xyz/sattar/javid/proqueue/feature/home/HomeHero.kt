package xyz.sattar.javid.proqueue.feature.home

import xyz.sattar.javid.proqueue.core.i18n.Str
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.interaction.MutableInteractionSource
import xyz.sattar.javid.proqueue.core.utils.toPersianDigits
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.Event
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import xyz.sattar.javid.proqueue.core.utils.DateTimeUtils
import xyz.sattar.javid.proqueue.domain.model.business.Business

/**
 * The pinned, collapsing head of the Home screen.
 *
 * ── Why it is pinned rather than just the first list item ───────────────────
 *
 * Home held six stacked sections and the two things an owner reaches for most
 * — opening the calendar and sending the booking link — were the fifth item
 * down and a tap into another tab. Both now live here, above everything, and
 * the section stays on screen while the rest scrolls under it.
 *
 * Collapsing (rather than a fixed bar) is what keeps that from costing
 * permanent vertical space: at rest it shows the date and two
 * full-width actions; scrolled, it folds down to a single compact row with the
 * same two actions as icon buttons. Driven by [collapseFraction] — a 0..1
 * value the caller derives from list scroll — so there is one source of truth
 * for the animation and no second scroll listener.
 */
/** How far the hero folds: the height of its expanded action row. */
val HomeHeroCollapseRange = 64.dp

@Composable
fun HomeHero(
    collapseFraction: Float,
    business: Business?,
    onOpenCalendar: () -> Unit,
    onOpenShare: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val expanded = 1f - collapseFraction.coerceIn(0f, 1f)
    val formattedDate = remember { DateTimeUtils.formatDate(DateTimeUtils.systemCurrentMilliseconds()) }

    Surface(
        modifier = modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.background,
    ) {
        Column(modifier = Modifier.padding(horizontal = 16.dp)) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = Str.today(formattedDate).toPersianDigits(),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onBackground,
                    )
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    // The collapsed affordances: they fade in exactly as the
                    // full-width buttons below fade out, so the actions are
                    // never unavailable mid-scroll.
                    if (collapseFraction > 0.05f) {
                        Row(
                            modifier = Modifier.alpha(collapseFraction),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            CompactAction(
                                icon = Icons.Rounded.CalendarMonth,
                                onClick = onOpenCalendar,
                            )
                            CompactAction(
                                icon = Icons.Rounded.Share,
                                onClick = onOpenShare,
                                highlighted = true,
                            )
                        }
                    }
                }
            }

            // Expanded actions. Height is animated to 0 so the collapse is a
            // fold rather than a pop, and the row is dropped from composition
            // once invisible instead of being left behind at zero height.
            if (expanded > 0.02f) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(HomeHeroCollapseRange * expanded)
                        .alpha(expanded)
                        .padding(bottom = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    HeroAction(
                        icon = Icons.Rounded.CalendarMonth,
                        label = Str.calendar_booking,
                        primary = false,
                        onClick = onOpenCalendar,
                        modifier = Modifier.weight(1f),
                    )
                    HeroAction(
                        icon = Icons.Rounded.Share,
                        label = Str.share_booking_link,
                        primary = true,
                        onClick = onOpenShare,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
    }
}

@Composable
private fun CompactAction(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    onClick: () -> Unit,
    highlighted: Boolean = false,
) {
    Box(
        modifier = Modifier
            .size(34.dp)
            .clip(CircleShape)
            .background(
                if (highlighted) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.primaryContainer
            )
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = if (highlighted) Color.White
            else MaterialTheme.colorScheme.onPrimaryContainer,
            modifier = Modifier.size(17.dp),
        )
    }
}

/**
 * One of the two header actions: icon and label on a single centred line, at
 * a fixed height so both buttons match whatever their label length.
 *
 * Share opens the share screen rather than copying the link on the spot:
 * copying is only one of the things an owner does with that link, and the
 * least useful in front of a customer at the counter.
 */
@Composable
private fun HeroAction(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    primary: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val interaction = remember { MutableInteractionSource() }
    val container = if (primary) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.primaryContainer
    val content = if (primary) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onPrimaryContainer
    Surface(
        onClick = onClick,
        interactionSource = interaction,
        modifier = modifier.height(52.dp).pressScale(interaction),
        shape = RoundedCornerShape(16.dp),
        color = container,
    ) {
        Row(
            modifier = Modifier.fillMaxSize().padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
        ) {
            Icon(imageVector = icon, contentDescription = null, tint = content, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(8.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = content,
                maxLines = 1,
            )
        }
    }
}
