package xyz.sattar.javid.proqueue.feature.businessDetail

import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.Navigation
import com.composables.icons.lucide.Phone
import com.composables.icons.lucide.Share2
import androidx.compose.foundation.layout.fillMaxWidth
import xyz.sattar.javid.proqueue.feature.discovery.toPersianDigits
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.text.InlineTextContent
import androidx.compose.foundation.text.appendInlineContent
import androidx.compose.ui.text.Placeholder
import androidx.compose.ui.text.PlaceholderVerticalAlign
import androidx.compose.ui.text.buildAnnotatedString
import xyz.sattar.javid.proqueue.core.location.rememberDirections
import xyz.sattar.javid.proqueue.core.utils.openPhoneDial
import xyz.sattar.javid.proqueue.domain.model.business.Business
import xyz.sattar.javid.proqueue.feature.discovery.BusinessAvatar
import xyz.sattar.javid.proqueue.feature.discovery.OnlineBadge
import xyz.sattar.javid.proqueue.feature.discovery.OpenStatusText
import xyz.sattar.javid.proqueue.feature.discovery.Radius
import xyz.sattar.javid.proqueue.feature.discovery.Space
import xyz.sattar.javid.proqueue.feature.discovery.VerifiedMark
import xyz.sattar.javid.proqueue.feature.discovery.areaLine
import xyz.sattar.javid.proqueue.feature.discovery.canBookOnline
import xyz.sattar.javid.proqueue.feature.discovery.formatDistance
import xyz.sattar.javid.proqueue.feature.discovery.openStatus

@Composable
internal fun Identity(business: Business) {
    val status = remember(business) { business.openStatus() }
    Column(Modifier.padding(horizontal = Space.page, vertical = Space.lg)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            BusinessAvatar(business, 72.dp)
            Spacer(Modifier.width(Space.lg))
            Column(Modifier.weight(1f)) {
                // Verified mark as inline content, so on a two-line name it sits
                // right after the last word instead of floating at the row's edge.
                Text(
                    buildAnnotatedString {
                        append(business.title)
                        if (business.isClaimed) {
                            append(" ")
                            appendInlineContent(VERIFIED_ID, "✓")
                        }
                    },
                    inlineContent = mapOf(
                        VERIFIED_ID to InlineTextContent(
                            Placeholder(20.sp, 20.sp, PlaceholderVerticalAlign.Center)
                        ) { VerifiedMark(18.dp) }
                    ),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                )
                Text(business.categoryLabel, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                val area = business.areaLine()
                if (area.isNotBlank()) {
                    Text(area, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        val distance = business.distanceKm
        if (status != null || distance != null || business.canBookOnline) {
            Spacer(Modifier.height(Space.md))
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Space.sm)) {
                if (business.canBookOnline) OnlineBadge()
                if (status != null) OpenStatusText(status)
                if (distance != null) {
                    Text(
                        (if (status != null) "· " else "") + formatDistance(distance),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
internal fun ActionRow(business: Business, onShare: () -> Unit) {
    val directions = rememberDirections()
    val lat = business.routeLatitude
    val lng = business.routeLongitude
    Row(
        Modifier.padding(start = Space.page, end = Space.page, bottom = Space.lg),
        horizontalArrangement = Arrangement.spacedBy(Space.sm)
    ) {
        ActionButton(Lucide.Phone, "تماس", business.phone.isNotBlank(), Modifier.weight(1f)) {
            openPhoneDial(business.phone)
        }
        ActionButton(Lucide.Navigation, "مسیریابی", lat != null && lng != null, Modifier.weight(1f)) {
            if (lat != null && lng != null) directions(lat, lng, business.title)
        }
        ActionButton(Lucide.Share2, "اشتراک", true, Modifier.weight(1f), onShare)
    }
}

@Composable
internal fun ActionButton(icon: ImageVector, label: String, enabled: Boolean, modifier: Modifier, onClick: () -> Unit) {
    OutlinedButton(
        onClick = onClick,
        enabled = enabled,
        shape = Radius.md,
        contentPadding = PaddingValues(horizontal = Space.sm),
        modifier = modifier.height(44.dp)
    ) {
        Icon(icon, contentDescription = null, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(6.dp))
        Text(label, style = MaterialTheme.typography.labelLarge, maxLines = 1)
    }
}

@Composable
internal fun Notice(icon: ImageVector, text: String, tint: Color = MaterialTheme.colorScheme.onSurfaceVariant) {
    Row(Modifier.padding(horizontal = Space.page, vertical = Space.lg), verticalAlignment = Alignment.Top) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(Space.md))
        Text(text, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
internal fun Tags(items: List<String>) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(Space.sm), verticalArrangement = Arrangement.spacedBy(Space.sm)) {
        items.forEach {
            Text(
                it,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier
                    .background(MaterialTheme.colorScheme.surfaceVariant, Radius.sm)
                    .padding(horizontal = Space.md, vertical = 6.dp)
            )
        }
    }
}

/** The follow button, full width under the stats card (the count is in the card). */
@Composable
internal fun FollowRow(following: Boolean, busy: Boolean, onToggle: () -> Unit) {
    FollowButton(following, busy, onToggle, Modifier.fillMaxWidth().padding(horizontal = Space.page))
}
