package xyz.sattar.javid.proqueue.core.ui.components

import xyz.sattar.javid.proqueue.core.i18n.Str
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import xyz.sattar.javid.proqueue.core.utils.toPersianDigits
import xyz.sattar.javid.proqueue.domain.model.business.PanelSmsSuffix
import xyz.sattar.javid.proqueue.domain.model.business.SmsSegments

private val CounterGreen = Color(0xFF10B981)
private val CounterAmber = Color(0xFFF59E0B)

/**
 * A message drawn as an incoming SMS. With [lockedSuffix], the footer and
 * لغو11 the SMS panel appends are drawn inside the bubble but visibly locked:
 * they are part of what the customer gets and what is billed, and the owner
 * cannot remove them.
 */
@Composable
fun SmsBubble(body: String, lockedSuffix: Boolean, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.10f),
        shape = RoundedCornerShape(topStart = 20.dp, topEnd = 4.dp, bottomStart = 20.dp, bottomEnd = 20.dp)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Text(
                text = body.ifBlank { "—" },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
                lineHeight = MaterialTheme.typography.bodyMedium.lineHeight * 1.35f
            )
            if (lockedSuffix) {
                Spacer(Modifier.height(10.dp))
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Rounded.Lock,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = "${PanelSmsSuffix.FOOTER}  ·  ${PanelSmsSuffix.OPT_OUT}",
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = Str.fixed_cant_be_removed,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                    )
                }
            }
        }
    }
}

/** Characters and billed parts for [text] — what the owner needs to keep a message cheap. */
@Composable
fun SmsCounter(text: String, footnote: String, modifier: Modifier = Modifier) {
    val length = SmsSegments.length(text)
    val parts = SmsSegments.parts(text)
    val left = SmsSegments.remainingInPart(text)
    val partsColor = when {
        parts <= 1 -> CounterGreen
        parts == 2 -> CounterAmber
        else -> MaterialTheme.colorScheme.error
    }
    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            CounterPill(value = length.toString().toPersianDigits(), label = Str.characters, tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.width(8.dp))
            CounterPill(value = parts.toString().toPersianDigits(), label = Str.sms, tint = partsColor)
            Spacer(Modifier.width(10.dp))
            Text(
                text = Str.characters_until_next_sms(left.toString().toPersianDigits()),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f)
            )
        }
        Spacer(Modifier.height(6.dp))
        Text(
            text = footnote,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun CounterPill(value: String, label: String, tint: Color) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(tint.copy(alpha = 0.12f))
            .padding(horizontal = 10.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(value, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold, color = tint)
        Spacer(Modifier.width(4.dp))
        Text(label, style = MaterialTheme.typography.labelSmall, color = tint)
    }
}
