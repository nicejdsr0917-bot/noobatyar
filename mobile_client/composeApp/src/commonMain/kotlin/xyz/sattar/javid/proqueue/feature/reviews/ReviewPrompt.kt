package xyz.sattar.javid.proqueue.feature.reviews

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.Star
import com.composables.icons.lucide.Store
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.koin.compose.koinInject
import xyz.sattar.javid.proqueue.core.network.ApiResponse
import xyz.sattar.javid.proqueue.core.prefs.PreferencesManager
import xyz.sattar.javid.proqueue.core.ui.icons.filled
import xyz.sattar.javid.proqueue.core.utils.AppConfig
import xyz.sattar.javid.proqueue.data.remoteDataSource.social.BusinessSocialApi
import xyz.sattar.javid.proqueue.data.remoteDataSource.social.model.PendingReviewDto
import xyz.sattar.javid.proqueue.feature.booking.LocalReducedMotion
import xyz.sattar.javid.proqueue.feature.discovery.Space
import xyz.sattar.javid.proqueue.feature.discovery.toPersianDigits
import xyz.sattar.javid.proqueue.ui.theme.WarningColor

/**
 * "How was your visit?": after the owner marks an appointment done, the next
 * time a signed-in visitor is on a main tab this sheet asks them to rate it.
 * Tapping a star opens the business page's review form with those stars;
 * either way the prompt is not shown again for that appointment.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReviewPrompt(onRate: (businessId: Long, stars: Int) -> Unit) {
    val api = koinInject<BusinessSocialApi>()
    val token by PreferencesManager.visitorToken.collectAsState(initial = null)
    var prompt by remember { mutableStateOf<PendingReviewDto?>(null) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(token) {
        val t = token ?: return@LaunchedEffect
        delay(1_500) // let the first screen settle before asking anything
        val pending = api.pendingReviews(t) as? ApiResponse.Success ?: return@LaunchedEffect
        val dismissed = PreferencesManager.dismissedReviewPrompts()
        prompt = pending.data.firstOrNull { it.appointmentId !in dismissed }
    }

    val current = prompt ?: return
    val sheet = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val close: (Int) -> Unit = { stars ->
        scope.launch {
            PreferencesManager.dismissReviewPrompt(current.appointmentId)
            sheet.hide()
            prompt = null
            if (stars > 0) onRate(current.businessId, stars)
        }
    }
    ModalBottomSheet(
        onDismissRequest = { close(0) },
        sheetState = sheet,
        containerColor = MaterialTheme.colorScheme.surface,
    ) {
        Column(
            Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = Space.page).padding(bottom = Space.lg),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Logo(current.businessLogo)
            Spacer(Modifier.height(Space.md))
            Text("نوبتتان چطور بود؟", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(Space.xs))
            Text(
                "به «${current.businessTitle}» امتیاز بدهید",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(Space.lg))
            Stars(onPick = close)
            Spacer(Modifier.height(Space.md))
            TextButton(onClick = { close(0) }) { Text("الان نه") }
        }
    }
}

@Composable
private fun Logo(path: String) {
    val url = path.takeIf { it.isNotBlank() }?.let { if (it.startsWith("http")) it else "${AppConfig.baseUrl}$it" }
    Box(
        Modifier.size(64.dp).clip(RoundedCornerShape(18.dp)).background(MaterialTheme.colorScheme.primaryContainer),
        contentAlignment = Alignment.Center,
    ) {
        if (url != null) {
            AsyncImage(model = url, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.size(64.dp))
        } else {
            Icon(Lucide.Store, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(30.dp))
        }
    }
}

/** Five stars that pop in one after another; a tap fills up to it, pops it, then goes on. */
@Composable
private fun Stars(onPick: (Int) -> Unit) {
    val reduced = LocalReducedMotion.current
    var picked by remember { mutableStateOf(0) }
    val scales = remember { List(5) { Animatable(if (reduced) 1f else 0f) } }
    LaunchedEffect(Unit) {
        scales.forEach { s ->
            launch { s.animateTo(1f, spring(dampingRatio = 0.45f, stiffness = Spring.StiffnessMedium)) }
            delay(60)
        }
    }
    val scope = rememberCoroutineScope()
    Row(horizontalArrangement = Arrangement.spacedBy(Space.sm)) {
        for (star in 1..5) {
            val scale = scales[star - 1]
            Icon(
                if (star <= picked) Lucide.Star.filled() else Lucide.Star,
                contentDescription = "${star.toPersianDigits()} ستاره",
                tint = WarningColor,
                modifier = Modifier
                    .size(40.dp)
                    .graphicsLayer { scaleX = scale.value; scaleY = scale.value }
                    .clickable(remember { MutableInteractionSource() }, indication = null) {
                        if (picked != 0) return@clickable
                        picked = star
                        scope.launch {
                            if (!reduced) {
                                scale.snapTo(1.35f)
                                scale.animateTo(1f, spring(dampingRatio = 0.4f, stiffness = Spring.StiffnessMedium))
                            }
                            delay(150)
                            onPick(star)
                        }
                    },
            )
        }
    }
}
