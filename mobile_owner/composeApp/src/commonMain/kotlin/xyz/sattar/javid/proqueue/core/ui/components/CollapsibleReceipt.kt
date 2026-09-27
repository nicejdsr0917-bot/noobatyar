package xyz.sattar.javid.proqueue.core.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.automirrored.rounded.ReceiptLong
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import org.jetbrains.compose.resources.stringResource
import proqueue.composeapp.generated.resources.Res
import proqueue.composeapp.generated.resources.click_to_view_full
import proqueue.composeapp.generated.resources.payment_receipt
import xyz.sattar.javid.proqueue.core.utils.AppConfig

/**
 * A client's uploaded payment slip, folded into one line on a list card: a
 * thumbnail, the label, and a chevron. Tapping it drops the full preview
 * open inline; tapping the preview opens the original.
 *
 * It used to be a 180dp image on every card that had one, which on a list of
 * pending-verification bookings meant one booking per screen — the owner
 * scrolled past pictures to reach the approve buttons. The thumbnail is
 * enough to see that a slip is there; the full image is one tap away when
 * it's actually being checked.
 */
@Composable
fun CollapsibleReceipt(
    receipt: String,
    modifier: Modifier = Modifier,
    initiallyExpanded: Boolean = false
) {
    val uriHandler = LocalUriHandler.current
    val receiptUrl = if (receipt.startsWith("http")) receipt else "${AppConfig.baseUrl}$receipt"
    var expanded by rememberSaveable(receipt) { mutableStateOf(initiallyExpanded) }
    val chevron by animateFloatAsState(if (expanded) 180f else 0f)
    val label = stringResource(Res.string.payment_receipt)

    Column(modifier = modifier.fillMaxWidth()) {
        Surface(
            shape = RoundedCornerShape(12.dp),
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .clickable { expanded = !expanded }
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                AsyncImage(
                    model = receiptUrl,
                    contentDescription = null,
                    modifier = Modifier.size(30.dp).clip(RoundedCornerShape(6.dp)),
                    contentScale = ContentScale.Crop,
                    // A slip's header (bank, amount) is at the top; the
                    // centre of a crop is usually blank white paper.
                    alignment = Alignment.TopCenter
                )
                Icon(
                    Icons.AutoMirrored.Rounded.ReceiptLong,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f)
                )
                Icon(
                    Icons.Rounded.ExpandMore,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp).rotate(chevron),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        AnimatedVisibility(
            visible = expanded,
            enter = expandVertically() + fadeIn(),
            exit = shrinkVertically() + fadeOut()
        ) {
            Column {
                Spacer(modifier = Modifier.height(6.dp))
                AsyncImage(
                    model = receiptUrl,
                    contentDescription = label,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(180.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .clickable { uriHandler.openUri(receiptUrl) },
                    contentScale = ContentScale.Crop
                )
                Text(
                    text = stringResource(Res.string.click_to_view_full),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
        }
    }
}
