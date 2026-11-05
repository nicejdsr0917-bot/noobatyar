package xyz.sattar.javid.proqueue.core.ui.components

import xyz.sattar.javid.proqueue.core.i18n.Str
import xyz.sattar.javid.proqueue.core.utils.toPersianDigits
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import xyz.sattar.javid.proqueue.domain.model.appointment.AppointmentSource

/**
 * Small "where did this booking come from" chip. Draws nothing for a missing
 * or UNKNOWN source, so old rows and old servers look exactly as before.
 */
@Composable
fun SourceBadge(source: String?, modifier: Modifier = Modifier) {
    val label = AppointmentSource.labelFor(source) ?: return
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f),
        modifier = modifier
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
        )
    }
}

/**
 * «مشتری تازه · ٪۲۰ تخفیف»: the client's first visit at a business that gives
 * a first-visit discount, so the owner remembers to apply it at the counter.
 */
@Composable
fun FirstVisitBadge(percent: Int, modifier: Modifier = Modifier) {
    if (percent <= 0) return
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = Color(0xFFF59E0B).copy(alpha = 0.16f),
        modifier = modifier
    ) {
        Text(
            text = Str.first_visit_badge(percent).toPersianDigits(),
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            color = Color(0xFFB45309),
            maxLines = 1,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
        )
    }
}
