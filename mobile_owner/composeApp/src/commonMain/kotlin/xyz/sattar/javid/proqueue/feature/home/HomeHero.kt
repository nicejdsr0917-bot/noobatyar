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
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.foundation.layout.wrapContentHeight
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
 * down and a tap into another tab. Both now live here, above everything.
 *
 * Scrolled, the whole head (date and the two full-width actions) folds away
 * and the same two actions appear as icons in the top bar instead
 * ([HomeTopBarActions]). An earlier version folded to a second row of small
 * icons under the top bar's own: two rows of round buttons, one of them a
 * duplicate calendar, and none of the space saved. Driven by
 * [collapseFraction] — a 0..1 value the caller derives from list scroll — so
 * there is one source of truth for the animation and no second scroll listener.
 */
/** How far the hero folds: all of it, the date row and the action row. */
val HomeHeroCollapseRange = 108.dp

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
    // Dropped from composition once folded rather than left at zero height.
    if (expanded <= 0.01f) return

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .height(HomeHeroCollapseRange * expanded)
            .clipToBounds(),
        color = MaterialTheme.colorScheme.background,
    ) {
        Column(
            modifier = Modifier
                .wrapContentHeight(Alignment.Bottom, unbounded = true)
                .alpha(expanded)
                .padding(horizontal = 16.dp)
        ) {
            Text(
                text = Str.today(formattedDate).toPersianDigits(),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.height(40.dp).wrapContentHeight(Alignment.CenterVertically),
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp),
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

/**
 * The hero's two actions as top-bar icons. They grow in as the hero folds, so
 * neither action is ever off screen and the bar holds no duplicate while the
 * full-width buttons are showing.
 */
@Composable
fun HomeTopBarActions(collapseFraction: Float, onOpenCalendar: () -> Unit, onOpenShare: () -> Unit) {
    val shown = collapseFraction.coerceIn(0f, 1f)
    if (shown <= 0.01f) return
    Row(
        modifier = Modifier.graphicsLayer {
            alpha = shown
            scaleX = 0.7f + 0.3f * shown
            scaleY = 0.7f + 0.3f * shown
        },
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        CompactAction(icon = Icons.Rounded.CalendarMonth, onClick = onOpenCalendar)
        CompactAction(icon = Icons.Rounded.Share, onClick = onOpenShare, highlighted = true)
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
            .size(36.dp)
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
            modifier = Modifier.size(18.dp),
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
