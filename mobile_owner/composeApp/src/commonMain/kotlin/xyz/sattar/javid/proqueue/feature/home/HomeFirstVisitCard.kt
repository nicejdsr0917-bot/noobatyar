package xyz.sattar.javid.proqueue.feature.home

import androidx.compose.foundation.layout.Box
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.LocalOffer
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import org.koin.compose.koinInject
import xyz.sattar.javid.proqueue.core.i18n.Str
import xyz.sattar.javid.proqueue.core.network.ApiResponse
import xyz.sattar.javid.proqueue.core.utils.toPersianDigits
import xyz.sattar.javid.proqueue.data.remoteDataSource.showcase.ShowcaseApi

private val Choices = listOf(0, 10, 15, 20, 25, 30)

/** The setting from the profile tab: the same card in a bottom sheet. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FirstVisitDiscountSheet(businessId: Long, onDismiss: () -> Unit) {
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = MaterialTheme.colorScheme.surface) {
        Box(Modifier.padding(start = 16.dp, end = 16.dp, bottom = 32.dp)) { FirstVisitDiscountCard(businessId) }
    }
}

/**
 * «تخفیف اولین مراجعه»: the owner picks a percent for clients' first visit
 * (or off). Saved on tap; the chip goes back if the save fails. Turning it on
 * also makes the business earn Noobatyar's new-client reward.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun FirstVisitDiscountCard(businessId: Long) {
    val api = koinInject<ShowcaseApi>()
    val scope = rememberCoroutineScope()
    var percent by remember(businessId) { mutableStateOf<Int?>(null) }
    LaunchedEffect(businessId) {
        // Offline or an old server: no card, never a crash.
        (runCatching { api.getFirstVisitDiscount(businessId) }.getOrNull() as? ApiResponse.Success)
            ?.let { percent = it.data.percent }
    }
    val current = percent ?: return
    HomeCard {
        HomeCardHeader(icon = Icons.Rounded.LocalOffer, title = Str.first_visit_title)
        Spacer(Modifier.height(8.dp))
        Text(
            Str.first_visit_body,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(12.dp))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Choices.forEach { value ->
                Choice(
                    label = if (value == 0) Str.first_visit_off else "٪$value".toPersianDigits(),
                    selected = value == current,
                ) {
                    if (value == current) return@Choice
                    val before = current
                    percent = value
                    scope.launch {
                        val saved = runCatching { api.setFirstVisitDiscount(businessId, value) }.getOrNull()
                        if (saved !is ApiResponse.Success) percent = before
                    }
                }
            }
        }
    }
}

@Composable
private fun Choice(label: String, selected: Boolean, onClick: () -> Unit) {
    val bg by animateColorAsState(
        if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface, tween(200), label = "fvBg",
    )
    val fg by animateColorAsState(
        if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface, tween(200), label = "fvFg",
    )
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(50),
        color = bg,
        border = if (selected) null else BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Text(
            label,
            color = fg,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
        )
    }
}
