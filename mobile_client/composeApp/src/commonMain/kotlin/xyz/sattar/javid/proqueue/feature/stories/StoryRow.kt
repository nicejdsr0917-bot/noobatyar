package xyz.sattar.javid.proqueue.feature.stories

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.Store
import kotlinx.coroutines.delay
import kotlin.time.Clock
import org.jetbrains.compose.resources.painterResource
import org.koin.compose.koinInject
import proqueue.composeapp.generated.resources.Res
import proqueue.composeapp.generated.resources.main_icon
import xyz.sattar.javid.proqueue.core.prefs.PreferencesManager
import xyz.sattar.javid.proqueue.feature.booking.LocalReducedMotion

private val Ring = Brush.sweepGradient(listOf(Color(0xFFA78BFA), Color(0xFF7C3AED), Color(0xFFC026D3), Color(0xFFA78BFA)))
private val Size = 46.dp
private const val Corner = 14

/**
 * The explore screen's story row, beside the city picker: Noobatyar first,
 * then followed businesses, then the rest of the city. Rings watched this
 * session turn grey and move to the end. Tapping one plays it and then every
 * unwatched ring after it. Hidden while there is nothing to show.
 */
@Composable
fun StoryRow(cityCode: Int?, modifier: Modifier = Modifier) {
    val center = koinInject<StoryCenter>()
    val rings by center.rings.collectAsState()
    val seen by center.seen.collectAsState()
    val token by PreferencesManager.visitorToken.collectAsState(initial = null)
    LaunchedEffect(cityCode, token) { center.refreshFeed(cityCode, Clock.System.now().toEpochMilliseconds()) }

    val ordered = remember(rings, seen) { rings.sortedBy { it.businessId in seen } }
    AnimatedVisibility(ordered.isNotEmpty(), modifier, enter = fadeIn() + expandHorizontally()) {
        LazyRow(
            contentPadding = PaddingValues(horizontal = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            itemsIndexed(ordered, key = { _, ring -> ring.businessId }) { i, ring ->
                val watched = ring.businessId in seen
                Bubble(ring, watched, i, Modifier.animateItem()) {
                    val then = ordered.drop(i + 1).filter { it.businessId !in seen }.map { it.businessId }
                    center.open(ring.businessId, then)
                }
            }
        }
    }
}

@Composable
private fun Bubble(ring: StoryRingDto, watched: Boolean, index: Int, modifier: Modifier, onClick: () -> Unit) {
    // Pops in one after another the first time the row shows.
    val reduced = LocalReducedMotion.current
    val scale = remember { Animatable(if (reduced) 1f else 0f) }
    LaunchedEffect(Unit) {
        delay(70L * index.coerceAtMost(6))
        scale.animateTo(1f, spring(dampingRatio = 0.5f, stiffness = Spring.StiffnessMediumLow))
    }
    val outer = RoundedCornerShape(Corner.dp)
    val inner = RoundedCornerShape((Corner - 4).dp)
    Box(
        modifier
            .size(Size)
            .graphicsLayer { scaleX = scale.value; scaleY = scale.value }
            .clip(outer)
            .then(
                if (watched) Modifier.border(1.5.dp, MaterialTheme.colorScheme.outlineVariant, outer)
                else Modifier.border(2.5.dp, Ring, outer)
            )
            .clickable(onClick = onClick)
            .padding(4.dp)
            .clip(inner)
            .background(MaterialTheme.colorScheme.primaryContainer)
            .semantics { contentDescription = "استوری ${ring.title}" },
        contentAlignment = Alignment.Center,
    ) {
        when {
            ring.platform -> Image(
                painterResource(Res.drawable.main_icon), contentDescription = null,
                contentScale = ContentScale.Crop, modifier = Modifier.size(Size),
            )
            ring.logo != null -> AsyncImage(
                model = ring.logo, contentDescription = null,
                contentScale = ContentScale.Crop, modifier = Modifier.size(Size),
            )
            else -> Icon(Lucide.Store, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
        }
    }
}
