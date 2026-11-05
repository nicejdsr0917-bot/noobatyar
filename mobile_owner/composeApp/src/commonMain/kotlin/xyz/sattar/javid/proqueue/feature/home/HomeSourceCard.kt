package xyz.sattar.javid.proqueue.feature.home

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import xyz.sattar.javid.proqueue.core.utils.toPersianDigits
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.TrendingUp
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import xyz.sattar.javid.proqueue.core.i18n.Str

/** «نوبت‌های آنلاین این ماه: X از اپ · Y از سایت» under the month card. A plain
 *  count of where online bookings came from, not a claim that we brought them. */
@Composable
fun SourceSummaryCard(summary: SourceSummary) {
    HomeCard {
        HomeCardHeader(icon = Icons.AutoMirrored.Rounded.TrendingUp, title = Str.source_summary_title)
        Spacer(Modifier.height(8.dp))
        Text(
            Str.source_summary_body(summary.app, summary.web).toPersianDigits(),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.fillMaxWidth().padding(start = HomeDimens.HeaderIcon + 10.dp)
        )
    }
}
