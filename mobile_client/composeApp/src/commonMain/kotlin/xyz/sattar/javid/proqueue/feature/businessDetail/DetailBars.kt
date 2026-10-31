package xyz.sattar.javid.proqueue.feature.businessDetail

import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.Navigation
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import xyz.sattar.javid.proqueue.core.ui.components.ShimmerBox
import xyz.sattar.javid.proqueue.core.utils.openMap
import xyz.sattar.javid.proqueue.core.utils.openPhoneDial
import xyz.sattar.javid.proqueue.domain.model.business.Business
import xyz.sattar.javid.proqueue.feature.discovery.BackIcon
import xyz.sattar.javid.proqueue.feature.discovery.Hairline
import xyz.sattar.javid.proqueue.feature.discovery.Radius
import xyz.sattar.javid.proqueue.feature.discovery.Space
import xyz.sattar.javid.proqueue.feature.discovery.canBookOnline

@Composable
internal fun TopBar(
    title: String?,
    showDivider: Boolean,
    onBack: () -> Unit,
    actions: @Composable () -> Unit = {},
) {
    Column(
        Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.background)
            .windowInsetsPadding(WindowInsets.statusBars)
    ) {
        Row(Modifier.height(56.dp).padding(horizontal = Space.xs), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(BackIcon, contentDescription = "بازگشت") }
            // Kept composed and faded rather than added/removed, so the bar's
            // layout never jumps when the title appears on scroll.
            val titleAlpha by animateFloatAsState(if (title != null) 1f else 0f, label = "topBarTitle")
            Text(
                title.orEmpty(),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f).graphicsLayer { alpha = titleAlpha }
            )
            actions()
        }
        if (showDivider) Hairline() else Spacer(Modifier.height(1.dp))
    }
}

/**
 * The one thing a visitor came to do, always under the thumb: book when the
 * business takes online bookings, otherwise call — with directions beside it.
 */
@Composable
internal fun androidx.compose.foundation.layout.BoxScope.StickyActionBar(business: Business, onBook: () -> Unit, signedIn: Boolean = true) {
    val canBook = business.canBookOnline
    val canCall = business.phone.isNotBlank()
    if (!canBook && !canCall) return
    val lat = business.latitude
    val lng = business.longitude
    Column(
        Modifier
            .align(Alignment.BottomCenter)
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.background)
    ) {
        Hairline()
        Row(
            Modifier
                .windowInsetsPadding(WindowInsets.navigationBars)
                .padding(horizontal = Space.page, vertical = Space.md),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Button(
                onClick = { if (canBook) onBook() else openPhoneDial(business.phone) },
                shape = Radius.md,
                colors = ButtonDefaults.buttonColors(),
                modifier = Modifier.weight(1f).height(48.dp)
            ) {
                Text(if (canBook) (if (signedIn) "رزرو نوبت" else "ورود و رزرو نوبت") else "تماس با مرکز", fontWeight = FontWeight.Bold)
            }
            if (lat != null && lng != null) {
                Spacer(Modifier.width(Space.sm))
                OutlinedButton(
                    onClick = { openMap(lat, lng, business.title) },
                    shape = Radius.md,
                    contentPadding = PaddingValues(0.dp),
                    modifier = Modifier.size(48.dp)
                ) {
                    Icon(Lucide.Navigation, contentDescription = "مسیریابی")
                }
            }
        }
    }
}

@Composable
internal fun DetailShimmer(onBack: () -> Unit) {
    Column {
        TopBar(title = null, showDivider = false, onBack = onBack)
        Row(Modifier.padding(Space.page), verticalAlignment = Alignment.CenterVertically) {
            ShimmerBox(Modifier.size(72.dp), Radius.md)
            Spacer(Modifier.width(Space.lg))
            Column {
                ShimmerBox(Modifier.width(180.dp).height(18.dp), Radius.sm)
                Spacer(Modifier.height(Space.sm))
                ShimmerBox(Modifier.width(120.dp).height(14.dp), Radius.sm)
            }
        }
        Row(Modifier.padding(horizontal = Space.page), horizontalArrangement = Arrangement.spacedBy(Space.sm)) {
            repeat(3) { ShimmerBox(Modifier.weight(1f).height(44.dp), Radius.md) }
        }
        Spacer(Modifier.height(Space.xl))
        Column(Modifier.padding(horizontal = Space.page), verticalArrangement = Arrangement.spacedBy(Space.md)) {
            ShimmerBox(Modifier.fillMaxWidth().height(14.dp), Radius.sm)
            ShimmerBox(Modifier.fillMaxWidth(0.7f).height(14.dp), Radius.sm)
            ShimmerBox(Modifier.fillMaxWidth(0.85f).height(14.dp), Radius.sm)
        }
    }
}

@Composable
internal fun ReportDialog(
    isSubmitting: Boolean,
    onDismiss: () -> Unit,
    onSubmit: (reason: String, detail: String) -> Unit,
) {
    var reason by remember { mutableStateOf(reportReasons.first().first) }
    var detail by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("گزارش این مرکز") },
        text = {
            Column {
                reportReasons.forEach { (code, label) ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .selectable(selected = reason == code, role = Role.RadioButton, onClick = { reason = code })
                            .padding(vertical = Space.xs),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(selected = reason == code, onClick = null)
                        Spacer(Modifier.width(Space.sm))
                        Text(label, style = MaterialTheme.typography.bodyMedium)
                    }
                }
                Spacer(Modifier.height(Space.sm))
                OutlinedTextField(
                    value = detail,
                    onValueChange = { if (it.length <= 500) detail = it },
                    label = { Text("توضیح (اختیاری)") },
                    minLines = 2,
                    shape = Radius.md,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onSubmit(reason, detail.trim()) }, enabled = !isSubmitting) {
                Text(if (isSubmitting) "در حال ارسال…" else "ارسال گزارش")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("انصراف") } }
    )
}
