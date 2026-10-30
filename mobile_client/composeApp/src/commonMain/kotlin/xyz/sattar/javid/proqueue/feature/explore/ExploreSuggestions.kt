package xyz.sattar.javid.proqueue.feature.explore

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.TrendingUp
import androidx.compose.material.icons.outlined.History
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import xyz.sattar.javid.proqueue.core.ui.components.ShimmerBox
import xyz.sattar.javid.proqueue.feature.discovery.Hairline
import xyz.sattar.javid.proqueue.feature.discovery.Radius
import xyz.sattar.javid.proqueue.feature.discovery.Space

@Composable
internal fun RowShimmer() {
    Row(Modifier.padding(horizontal = Space.page, vertical = Space.md)) {
        ShimmerBox(Modifier.size(56.dp), Radius.md)
        Spacer(Modifier.width(Space.md))
        Column(Modifier.weight(1f)) {
            ShimmerBox(Modifier.fillMaxWidth(0.55f).height(14.dp), Radius.sm)
            Spacer(Modifier.height(Space.sm))
            ShimmerBox(Modifier.fillMaxWidth(0.4f).height(12.dp), Radius.sm)
            Spacer(Modifier.height(Space.sm))
            ShimmerBox(Modifier.fillMaxWidth(0.3f).height(12.dp), Radius.sm)
        }
    }
}

/** Common searches shown before the user types — a nudge, not a taxonomy. */
internal val popularSearches = listOf(
    "دندان‌پزشک", "متخصص پوست", "آرایشگاه زنانه", "ارتودنسی",
    "روان‌شناس", "فیزیوتراپی", "کاشت مو", "آزمایشگاه", "متخصص اطفال",
)

@Composable
internal fun SearchSuggestions(recent: List<String>, onPick: (String) -> Unit, onClearRecent: () -> Unit) {
    Column(Modifier.padding(vertical = Space.sm)) {
        if (recent.isNotEmpty()) {
            Row(Modifier.padding(start = Space.page, end = Space.sm), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "جستجوهای اخیر",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f)
                )
                TextButton(onClick = onClearRecent) { Text("پاک کردن") }
            }
            recent.forEach { q -> SuggestionRow(Icons.Outlined.History, q) { onPick(q) } }
            Spacer(Modifier.height(Space.md))
        }
        Text(
            "پرجستجوها",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = Space.page, vertical = Space.sm)
        )
        popularSearches.forEach { q -> SuggestionRow(Icons.AutoMirrored.Outlined.TrendingUp, q) { onPick(q) } }
        Hairline(Modifier.padding(top = Space.sm))
    }
}

@Composable
internal fun SuggestionRow(icon: ImageVector, text: String, onClick: () -> Unit) {
    Surface(onClick = onClick, color = Color.Transparent, modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.height(44.dp).padding(horizontal = Space.page), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(Space.lg))
            Text(text, style = MaterialTheme.typography.bodyLarge)
        }
    }
}
