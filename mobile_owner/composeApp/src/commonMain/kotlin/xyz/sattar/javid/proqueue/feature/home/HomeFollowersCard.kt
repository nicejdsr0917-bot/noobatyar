package xyz.sattar.javid.proqueue.feature.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.PersonAdd
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import xyz.sattar.javid.proqueue.core.i18n.Str
import xyz.sattar.javid.proqueue.core.utils.toPersianDigits
import xyz.sattar.javid.proqueue.data.remoteDataSource.showcase.model.FollowerDto
import xyz.sattar.javid.proqueue.data.remoteDataSource.showcase.model.FollowersPageDto

/** «دنبال‌کننده‌های تازه»: the newest followers' photos and first names, and the total; opens the full list. */
@Composable
fun NewFollowersCard(followers: FollowersPageDto, onClick: () -> Unit) {
    HomeCard(onClick = onClick) {
        HomeCardHeader(icon = Icons.Rounded.PersonAdd, title = Str.home_new_followers_title)
        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            followers.results.forEach { FollowerChip(it) }
        }
        Spacer(Modifier.height(10.dp))
        Text(
            Str.home_followers_total(followers.count).toPersianDigits(),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun FollowerChip(follower: FollowerDto) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.width(52.dp)) {
        Box(
            Modifier.size(44.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primaryContainer),
            contentAlignment = Alignment.Center
        ) {
            Text(
                follower.name.take(1),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onPrimaryContainer
            )
            if (!follower.avatar.isNullOrBlank()) {
                AsyncImage(
                    model = follower.avatar,
                    contentDescription = follower.name,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.size(44.dp).clip(CircleShape)
                )
            }
        }
        Text(
            follower.name,
            style = MaterialTheme.typography.labelSmall,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 4.dp)
        )
    }
}
