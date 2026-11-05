package xyz.sattar.javid.proqueue.feature.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil3.compose.AsyncImage
import org.koin.compose.koinInject
import xyz.sattar.javid.proqueue.core.i18n.Str
import xyz.sattar.javid.proqueue.core.network.ApiResponse
import xyz.sattar.javid.proqueue.core.utils.toPersianDigits
import xyz.sattar.javid.proqueue.data.remoteDataSource.showcase.ShowcaseApi
import xyz.sattar.javid.proqueue.data.remoteDataSource.showcase.model.FollowerDto

private const val PAGE_SIZE = 30

/**
 * Everyone following the business, newest first, full screen. First name and
 * photo only: the server never sends a follower's phone number, and the page
 * says so. Loads the next page as the list nears its end.
 */
@Composable
fun FollowersListDialog(businessId: Long, onDismiss: () -> Unit) {
    val api = koinInject<ShowcaseApi>()
    val followers = remember(businessId) { mutableStateListOf<FollowerDto>() }
    var total by remember(businessId) { mutableIntStateOf(0) }
    var nextPage by remember(businessId) { mutableIntStateOf(1) }
    var lastPage by remember(businessId) { mutableIntStateOf(1) }
    var loading by remember(businessId) { mutableStateOf(true) }
    val listState = rememberLazyListState()
    val nearEnd by remember {
        derivedStateOf {
            val last = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
            last >= followers.size - 5
        }
    }

    LaunchedEffect(businessId, nearEnd, nextPage) {
        if (nextPage > lastPage || (!nearEnd && nextPage > 1)) return@LaunchedEffect
        loading = true
        val page = runCatching { api.getFollowersPage(businessId, nextPage, PAGE_SIZE) }.getOrNull()
        if (page is ApiResponse.Success) {
            followers.addAll(page.data.results)
            total = page.data.count
            lastPage = page.data.totalPages
            nextPage++
        } else {
            lastPage = 0 // stop; the list keeps what it has
        }
        loading = false
    }

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            Column(Modifier.windowInsetsPadding(WindowInsets.safeDrawing)) {
                Row(Modifier.fillMaxWidth().padding(4.dp), verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = null)
                    }
                    Text(Str.followers_list_title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.width(8.dp))
                    if (total > 0) {
                        Text(
                            total.toString().toPersianDigits(),
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                }
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Rounded.Lock, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(14.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(Str.followers_list_privacy, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                when {
                    followers.isEmpty() && loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                    followers.isEmpty() -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(Str.followers_list_empty, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    else -> LazyColumn(state = listState, modifier = Modifier.fillMaxSize()) {
                        itemsIndexed(followers) { i, follower ->
                            FollowerRow(follower)
                            if (i < followers.lastIndex) {
                                HorizontalDivider(Modifier.padding(start = 76.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                            }
                        }
                        if (loading) item {
                            Box(Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
                                CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.dp)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun FollowerRow(follower: FollowerDto) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Start,
    ) {
        Box(
            Modifier.size(44.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primaryContainer),
            contentAlignment = Alignment.Center,
        ) {
            Text(follower.name.take(1), style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onPrimaryContainer)
            if (!follower.avatar.isNullOrBlank()) {
                AsyncImage(
                    model = follower.avatar, contentDescription = follower.name,
                    contentScale = ContentScale.Crop, modifier = Modifier.size(44.dp).clip(CircleShape),
                )
            }
        }
        Spacer(Modifier.width(12.dp))
        Text(follower.name, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
    }
}
