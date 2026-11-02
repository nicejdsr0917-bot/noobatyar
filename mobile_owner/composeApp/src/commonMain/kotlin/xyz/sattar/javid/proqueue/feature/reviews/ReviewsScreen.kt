package xyz.sattar.javid.proqueue.feature.reviews

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.RateReview
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.StarBorder
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import org.koin.compose.viewmodel.koinViewModel
import xyz.sattar.javid.proqueue.core.i18n.Str
import xyz.sattar.javid.proqueue.core.ui.components.EmptyState
import xyz.sattar.javid.proqueue.core.ui.components.ListItemShimmer
import xyz.sattar.javid.proqueue.core.ui.components.ToastyHost
import xyz.sattar.javid.proqueue.core.ui.components.adaptivePagePadding
import xyz.sattar.javid.proqueue.core.ui.components.showToasty
import xyz.sattar.javid.proqueue.core.utils.DateTimeUtils
import xyz.sattar.javid.proqueue.core.utils.toPersianDigits
import xyz.sattar.javid.proqueue.data.remoteDataSource.showcase.model.OwnerReviewDto
import xyz.sattar.javid.proqueue.data.remoteDataSource.showcase.model.ReviewSummaryDto

private val StarColor = Color(0xFFF59E0B)

/** Read-only list of customer reviews, with the rating summary on top. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReviewsScreen(
    viewModel: ReviewsViewModel = koinViewModel(),
    onNavigateBack: () -> Unit
) {
    val state by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    val listState = rememberLazyListState()

    LaunchedEffect(Unit) { viewModel.sendIntent(ReviewsIntent.Load) }

    LaunchedEffect(state.message) {
        state.message?.let {
            snackbarHostState.showToasty(it)
            viewModel.sendIntent(ReviewsIntent.ClearMessage)
        }
    }

    // Fire once the tail comes into view.
    val shouldLoadMore by remember {
        derivedStateOf {
            val total = listState.layoutInfo.totalItemsCount
            val lastVisible = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
            state.canLoadMore && !state.isLoading && !state.isPaginating && total > 0 && lastVisible >= total - 3
        }
    }
    LaunchedEffect(shouldLoadMore) {
        if (shouldLoadMore) viewModel.sendIntent(ReviewsIntent.LoadMore)
    }

    Scaffold(
        snackbarHost = { ToastyHost(hostState = snackbarHostState) },
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        Str.reviews_title,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = Str.back)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background)
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            when {
                state.isLoading && state.reviews.isEmpty() -> Column(
                    modifier = Modifier.padding(horizontal = adaptivePagePadding()),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) { repeat(4) { ListItemShimmer(height = 96.dp) } }

                state.errorMessage != null && state.reviews.isEmpty() -> Column(
                    modifier = Modifier.fillMaxSize(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    EmptyState(
                        icon = Icons.Rounded.ErrorOutline,
                        title = Str.reviews_load_failed,
                        subtitle = state.errorMessage
                    )
                    TextButton(onClick = { viewModel.sendIntent(ReviewsIntent.Load) }) {
                        Text(Str.retry_action)
                    }
                }

                state.reviews.isEmpty() && !state.isLoading -> EmptyState(
                    modifier = Modifier.fillMaxSize(),
                    icon = Icons.Rounded.RateReview,
                    title = Str.reviews_empty,
                    subtitle = Str.reviews_empty_subtitle
                )

                else -> LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(
                        start = adaptivePagePadding(),
                        end = adaptivePagePadding(),
                        top = 4.dp,
                        bottom = 24.dp
                    ),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    state.summary?.takeIf { it.count > 0 }?.let { summary ->
                        item(key = "summary") { ReviewSummaryCard(summary) }
                    }
                    items(state.reviews, key = { it.id }) { review -> ReviewCard(review) }
                    if (state.isPaginating) {
                        item(key = "paging") { ListItemShimmer(height = 72.dp) }
                    }
                }
            }
        }
    }
}

@Composable
fun ReviewSummaryCard(summary: ReviewSummaryDto, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = formatAverage(summary.avg).toPersianDigits(),
                    style = MaterialTheme.typography.displaySmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                StarRow(rating = summary.avg.toInt().coerceIn(0, 5))
                Spacer(Modifier.height(4.dp))
                Text(
                    text = Str.reviews_count(summary.count.toString().toPersianDigits()),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                for (stars in 5 downTo 1) {
                    HistogramRow(stars = stars, count = summary.countFor(stars), total = summary.count)
                }
            }
        }
    }
}

@Composable
private fun HistogramRow(stars: Int, count: Int, total: Int) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = stars.toString().toPersianDigits(),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(14.dp)
        )
        Icon(Icons.Rounded.Star, contentDescription = null, tint = StarColor, modifier = Modifier.size(12.dp))
        Spacer(Modifier.width(6.dp))
        Box(
            modifier = Modifier
                .weight(1f)
                .height(8.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surfaceVariant)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .fillMaxWidth(histogramFraction(count, total))
                    .clip(CircleShape)
                    .background(StarColor)
            )
        }
        Text(
            text = count.toString().toPersianDigits(),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(28.dp).padding(start = 6.dp)
        )
    }
}

@Composable
fun ReviewCard(review: OwnerReviewDto, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer.copy(alpha = if (review.isHidden) 0.6f else 1f)
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = review.visitorName.ifBlank { Str.reviews_anonymous },
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    modifier = Modifier.weight(1f, fill = false)
                )
                if (review.isHidden) {
                    Spacer(Modifier.width(8.dp))
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.errorContainer
                    ) {
                        Text(
                            text = Str.reviews_hidden,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                        )
                    }
                }
                Spacer(Modifier.weight(1f))
                Text(
                    text = reviewDate(review.createdAt).toPersianDigits(),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            StarRow(rating = review.rating)
            if (review.text.isNotBlank()) {
                Text(
                    text = review.text,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
        }
    }
}

@Composable
private fun StarRow(rating: Int) {
    Row {
        for (i in 1..5) {
            Icon(
                imageVector = if (i <= rating) Icons.Rounded.Star else Icons.Rounded.StarBorder,
                contentDescription = null,
                tint = StarColor,
                modifier = Modifier.size(16.dp)
            )
        }
    }
}

/** One decimal, with the trailing ".0" kept so 5 reads "5.0" like every other score. */
internal fun formatAverage(avg: Double): String {
    val tenths = (avg * 10).let { kotlin.math.round(it).toInt() }.coerceIn(0, 50)
    return "${tenths / 10}.${tenths % 10}"
}

/** Bar length as a 0..1 fraction; a zero count draws no bar. */
internal fun histogramFraction(count: Int, total: Int): Float =
    if (total <= 0 || count <= 0) 0.0001f else (count.toFloat() / total).coerceIn(0.0001f, 1f)

/** Jalali date of an ISO timestamp, or "" if it can't be parsed. */
internal fun reviewDate(createdAt: String): String {
    val millis = DateTimeUtils.parseIsoToEpochMillis(createdAt)
    return if (millis <= 0L) "" else DateTimeUtils.formatDate(millis)
}
