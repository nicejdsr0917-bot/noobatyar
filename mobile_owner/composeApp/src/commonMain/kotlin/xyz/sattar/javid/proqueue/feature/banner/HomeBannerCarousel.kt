package xyz.sattar.javid.proqueue.feature.banner

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import kotlinx.coroutines.delay
import org.koin.compose.viewmodel.koinViewModel
import xyz.sattar.javid.proqueue.core.i18n.Str
import xyz.sattar.javid.proqueue.core.utils.mediaUrl
import xyz.sattar.javid.proqueue.core.utils.openUrl

/** How long each banner stays before the carousel moves on. */
const val BANNER_INTERVAL_MILLIS = 5_000L

/** Banner image proportions: the admin only accepts exactly 1200x400. */
const val BANNER_ASPECT_RATIO = 3f

/**
 * Rotating 3:1 banner strip for the home screen. Draws nothing — not even a
 * placeholder — when there are no banners or the call failed, so an owner
 * without any never sees an empty slot.
 */
@Composable
fun HomeBannerCarousel(
    businessId: Long?,
    modifier: Modifier = Modifier,
    viewModel: BannerViewModel = koinViewModel()
) {
    val state by viewModel.uiState.collectAsState()

    LaunchedEffect(businessId) {
        if (businessId != null) viewModel.sendIntent(BannerIntent.Load(businessId))
    }

    if (!state.visible) return
    BannerCarousel(
        imageUrls = state.banners.map { it.image },
        onClick = state.linkUrl.takeIf { it.isNotBlank() }?.let { url -> { openUrl(url) } },
        modifier = modifier
    )
}

@Composable
fun BannerCarousel(
    imageUrls: List<String>,
    onClick: (() -> Unit)?,
    modifier: Modifier = Modifier
) {
    val pagerState = rememberPagerState(pageCount = { imageUrls.size })

    // Auto-advance. Restarts after every swipe because the delay is keyed on
    // the settled page, so a banner someone just dragged to gets its full 5s.
    if (imageUrls.size > 1) {
        LaunchedEffect(pagerState.currentPage, imageUrls.size) {
            delay(BANNER_INTERVAL_MILLIS)
            if (!pagerState.isScrollInProgress) {
                pagerState.animateScrollToPage((pagerState.currentPage + 1) % imageUrls.size)
            }
        }
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .aspectRatio(BANNER_ASPECT_RATIO)
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
    ) {
        HorizontalPager(state = pagerState, modifier = Modifier.fillMaxWidth()) { page ->
            AsyncImage(
                model = mediaUrl(imageUrls[page]),
                contentDescription = Str.home_banner_description,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(BANNER_ASPECT_RATIO)
                    .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            )
        }

        if (imageUrls.size > 1) {
            Row(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                repeat(imageUrls.size) { index ->
                    val selected = pagerState.currentPage == index
                    Box(
                        modifier = Modifier
                            .padding(horizontal = 3.dp)
                            .clip(CircleShape)
                            .background(if (selected) Color.White else Color.White.copy(alpha = 0.5f))
                            .width(if (selected) 14.dp else 6.dp)
                            .height(6.dp)
                    )
                }
            }
        }
    }
}
