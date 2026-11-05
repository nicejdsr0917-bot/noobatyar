package xyz.sattar.javid.proqueue.feature.following

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.UserPlus
import org.koin.compose.viewmodel.koinViewModel
import xyz.sattar.javid.proqueue.core.ui.components.ShimmerBox
import xyz.sattar.javid.proqueue.domain.model.business.Business
import xyz.sattar.javid.proqueue.feature.businessDetail.TopBar
import xyz.sattar.javid.proqueue.feature.discovery.BusinessAvatar
import xyz.sattar.javid.proqueue.feature.discovery.Hairline
import xyz.sattar.javid.proqueue.feature.discovery.Radius
import xyz.sattar.javid.proqueue.feature.discovery.Space
import xyz.sattar.javid.proqueue.feature.discovery.StateMessage
import xyz.sattar.javid.proqueue.feature.discovery.areaLine
import xyz.sattar.javid.proqueue.feature.discovery.toPersianDigits

/** «دنبال‌شده‌ها»: the business pages the visitor follows, newest first. */
@Composable
fun FollowingScreen(
    onNavigateBack: () -> Unit,
    onOpenBusiness: (Long) -> Unit,
    viewModel: FollowingViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsState()
    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        TopBar(title = "دنبال‌شده‌ها", showDivider = true, onBack = onNavigateBack)
        LazyColumn(contentPadding = PaddingValues(bottom = Space.xl)) {
            when {
                state.isLoading -> items(4) {
                    ShimmerBox(Modifier.padding(horizontal = Space.page, vertical = Space.md).fillMaxWidth().height(64.dp), Radius.md)
                }
                state.businesses.isEmpty() -> item {
                    StateMessage(
                        icon = Lucide.UserPlus,
                        title = state.error ?: "هنوز صفحه‌ای را دنبال نمی‌کنید",
                        body = if (state.error != null) "دوباره تلاش کنید." else "در صفحه هر مرکز «دنبال کردن» را بزنید تا اینجا بیاید.",
                        actionLabel = if (state.error != null) "تلاش دوباره" else null,
                        onAction = viewModel::load,
                        modifier = Modifier.padding(top = 60.dp),
                    )
                }
                else -> {
                    item {
                        Text(
                            "${state.businesses.size.toPersianDigits()} صفحه",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = Space.page, vertical = Space.md),
                        )
                    }
                    itemsIndexed(state.businesses, key = { _, b -> b.id }) { index, business ->
                        FollowedRow(business, onOpen = { onOpenBusiness(business.id) }, onUnfollow = { viewModel.unfollow(business.id) })
                        if (index != state.businesses.lastIndex) Hairline(Modifier.padding(start = 84.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun FollowedRow(business: Business, onOpen: () -> Unit, onUnfollow: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onOpen).padding(horizontal = Space.page, vertical = Space.md),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        BusinessAvatar(business, 52.dp)
        Spacer(Modifier.width(Space.md))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(business.title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            val line = listOf(business.categoryLabel, business.areaLine()).filter { it.isNotBlank() }.joinToString(" · ")
            Text(line, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Spacer(Modifier.width(Space.sm))
        OutlinedButton(onClick = onUnfollow, shape = Radius.md, contentPadding = PaddingValues(horizontal = Space.md), modifier = Modifier.height(34.dp)) {
            Text("دنبال می‌کنید", style = MaterialTheme.typography.labelMedium)
        }
    }
}
