package xyz.sattar.javid.proqueue.feature.promo

import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.Tag
import com.composables.icons.lucide.X
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.layout
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import org.koin.compose.viewmodel.koinViewModel
import xyz.sattar.javid.proqueue.core.utils.openUrl
import xyz.sattar.javid.proqueue.core.utils.rememberReducedMotion
import xyz.sattar.javid.proqueue.data.remoteDataSource.promo.PromoDto
import xyz.sattar.javid.proqueue.data.remoteDataSource.promo.PromoPlacement
import xyz.sattar.javid.proqueue.feature.discovery.Space

/**
 * The admin's promo line for [placement], or nothing. [overlap] is how far
 * the content below slides up over the strip's bottom edge (the strip pads
 * itself so its text stays clear), like an offer bar tucked behind a sheet.
 */
@Composable
fun PromoStrip(
    placement: PromoPlacement,
    onOpenBusiness: (Long) -> Unit,
    shape: Shape,
    modifier: Modifier = Modifier,
    overlap: Dp = 0.dp,
    viewModel: PromoViewModel = koinViewModel(key = "promo-${placement.name}"),
) {
    LaunchedEffect(placement) { viewModel.load(placement) }
    val promo by viewModel.current.collectAsState()
    AnimatedVisibility(
        visible = promo != null,
        enter = expandVertically(tween(380, easing = FastOutSlowInEasing)) + fadeIn(),
        exit = shrinkVertically() + fadeOut(),
        modifier = modifier.overlapBottom(overlap),
    ) {
        // Keeps showing the last promo while it collapses away.
        val shown = remember { promo }
        val p = promo ?: shown ?: return@AnimatedVisibility
        StripBody(
            p,
            shape = shape,
            bottomPadding = overlap,
            onClick = {
                val business = p.businessId
                when {
                    business != null -> onOpenBusiness(business)
                    p.linkUrl.isNotBlank() -> openUrl(p.linkUrl)
                }
            },
            onClose = viewModel::dismiss,
        )
    }
}

/** Reports [overlap] less height than it draws, so the next sibling covers that much of it. */
private fun Modifier.overlapBottom(overlap: Dp): Modifier = layout { measurable, constraints ->
    val placeable = measurable.measure(constraints)
    val cut = overlap.roundToPx().coerceAtMost(placeable.height)
    layout(placeable.width, placeable.height - cut) { placeable.place(0, 0) }
}

@Composable
private fun StripBody(promo: PromoDto, shape: Shape, bottomPadding: Dp, onClick: () -> Unit, onClose: () -> Unit) {
    val primary = MaterialTheme.colorScheme.primary
    val reduced = rememberReducedMotion()
    // A slow light sweep, so the strip reads as an offer rather than a notice.
    val sweep = rememberInfiniteTransition(label = "promoSweep")
    val shine by sweep.animateFloat(
        initialValue = -0.3f,
        targetValue = 1.3f,
        animationSpec = infiniteRepeatable(tween(2600, delayMillis = 1800), RepeatMode.Restart),
        label = "promoShine",
    )
    Box(
        Modifier
            .fillMaxWidth()
            .background(
                Brush.horizontalGradient(listOf(primary.copy(alpha = 0.20f), primary.copy(alpha = 0.10f))),
                shape,
            )
            .background(
                if (reduced) Brush.horizontalGradient(listOf(primary.copy(alpha = 0f), primary.copy(alpha = 0f)))
                else Brush.horizontalGradient(
                    0f to primary.copy(alpha = 0f),
                    (shine - 0.15f).coerceIn(0f, 1f) to primary.copy(alpha = 0f),
                    shine.coerceIn(0f, 1f) to MaterialTheme.colorScheme.surface.copy(alpha = 0.35f),
                    (shine + 0.15f).coerceIn(0f, 1f) to primary.copy(alpha = 0f),
                    1f to primary.copy(alpha = 0f),
                ),
                shape,
            )
            .clickable(onClick = onClick)
            .padding(bottom = bottomPadding)
    ) {
        Row(
            Modifier.fillMaxWidth().heightIn(min = 44.dp).padding(start = Space.lg, end = Space.xs),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Lucide.Tag, contentDescription = null, tint = primary, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(Space.sm))
            Text(
                promo.text,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            if (promo.actionLabel.isNotBlank()) {
                Spacer(Modifier.width(Space.sm))
                Text(
                    promo.actionLabel,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.background(primary, CircleShape).padding(horizontal = Space.md, vertical = 4.dp),
                )
            }
            Box(
                Modifier
                    .size(36.dp)
                    .clickable(onClick = onClose)
                    .graphicsLayer { alpha = 0.7f },
                contentAlignment = Alignment.Center,
            ) {
                Icon(Lucide.X, contentDescription = "بستن", modifier = Modifier.size(16.dp))
            }
        }
    }
}
