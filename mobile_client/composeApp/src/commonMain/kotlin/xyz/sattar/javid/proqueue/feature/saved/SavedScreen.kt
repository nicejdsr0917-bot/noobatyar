package xyz.sattar.javid.proqueue.feature.saved

import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.Bookmark
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import org.koin.compose.viewmodel.koinViewModel
import xyz.sattar.javid.proqueue.core.ui.components.ShimmerBox
import xyz.sattar.javid.proqueue.domain.model.business.Business
import xyz.sattar.javid.proqueue.feature.discovery.BusinessRow
import xyz.sattar.javid.proqueue.feature.discovery.Hairline
import xyz.sattar.javid.proqueue.feature.discovery.Radius
import xyz.sattar.javid.proqueue.feature.discovery.Space
import xyz.sattar.javid.proqueue.feature.discovery.StateMessage
import xyz.sattar.javid.proqueue.feature.discovery.toPersianDigits

@Composable
fun SavedScreen(
    onNavigateToDetail: (Long) -> Unit,
    bottomPadding: Dp,
    viewModel: SavedViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsState()
    LazyColumn(
        modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background),
        contentPadding = PaddingValues(bottom = bottomPadding + Space.xl),
    ) {
        item {
            Column(
                Modifier
                    .windowInsetsPadding(WindowInsets.statusBars)
                    .padding(horizontal = Space.page, vertical = Space.lg)
            ) {
                Text("نشان‌شده‌ها", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                if (state.businesses.isNotEmpty()) {
                    Text(
                        "${state.businesses.size.toPersianDigits()} مرکز",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            Hairline()
        }
        when {
            state.isLoading -> items(3) {
                ShimmerBox(
                    Modifier.padding(horizontal = Space.page, vertical = Space.md).fillMaxWidth().height(72.dp),
                    Radius.md
                )
            }
            state.businesses.isEmpty() -> item {
                StateMessage(
                    icon = Lucide.Bookmark,
                    title = "هنوز مرکزی نشان نکرده‌اید",
                    body = "مرکزها را با علامت نشان ذخیره کنید تا اینجا باشند.",
                    modifier = Modifier.padding(top = 60.dp)
                )
            }
            else -> itemsIndexed(state.businesses, key = { _, b -> b.id }) { index, b ->
                BusinessRow(
                    business = b,
                    saved = true,
                    onToggleSave = { viewModel.unsave(b.id) },
                    onClick = { onNavigateToDetail(b.id) },
                )
                if (index != state.businesses.lastIndex) Hairline(Modifier.padding(start = 84.dp))
            }
        }
    }
}
