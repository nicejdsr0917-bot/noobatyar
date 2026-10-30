package xyz.sattar.javid.proqueue.feature.booking

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AddPhotoAlternate
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import xyz.sattar.javid.proqueue.core.utils.rememberImagePicker
import xyz.sattar.javid.proqueue.core.utils.toImageBitmapOrNull
import xyz.sattar.javid.proqueue.feature.discovery.Radius
import xyz.sattar.javid.proqueue.feature.discovery.Space
import xyz.sattar.javid.proqueue.feature.discovery.toPersianDigits

/**
 * Photo of a card-transfer receipt: a dashed drop zone until one is picked,
 * then a preview that can be changed or removed. While [progress] is set the
 * preview dims under a ring that fills with the sent fraction.
 */
@Composable
fun ReceiptPicker(
    receipt: ByteArray?,
    onPick: (ByteArray) -> Unit,
    onRemove: () -> Unit,
    progress: Float?,
    modifier: Modifier = Modifier,
) {
    val haptic = LocalHapticFeedback.current
    val picker = rememberImagePicker(onSingleImagePicked = {
        haptic.performHapticFeedback(HapticFeedbackType.Confirm)
        onPick(it)
    })
    val uploading = progress != null
    AnimatedContent(
        targetState = receipt,
        contentKey = { it != null },
        transitionSpec = {
            (scaleIn(initialScale = 0.92f, animationSpec = spring(Spring.DampingRatioMediumBouncy, Spring.StiffnessMediumLow)) + fadeIn()) togetherWith
                (scaleOut(targetScale = 0.96f) + fadeOut(tween(120)))
        },
        modifier = modifier.fillMaxWidth(),
        label = "receipt",
    ) { picked ->
        if (picked == null) {
            EmptyReceipt(onClick = { picker.launch() })
        } else {
            ReceiptPreview(
                picked,
                progress = progress,
                onChange = { if (!uploading) picker.launch() },
                onRemove = {
                    haptic.performHapticFeedback(HapticFeedbackType.ToggleOff)
                    onRemove()
                },
            )
        }
    }
}

@Composable
private fun EmptyReceipt(onClick: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val outline = MaterialTheme.colorScheme.outline
    Surface(
        onClick = onClick,
        shape = Radius.md,
        color = MaterialTheme.colorScheme.surface,
        interactionSource = interaction,
        modifier = Modifier
            .fillMaxWidth()
            .pressScale(interaction, 0.98f)
            // Over the content: the Surface paints its own background.
            .drawWithContent {
                drawContent()
                drawRoundRect(
                    color = outline,
                    cornerRadius = CornerRadius(12.dp.toPx()),
                    style = Stroke(width = 1.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 8f))),
                )
            },
    ) {
        Row(Modifier.padding(Space.lg), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(44.dp).background(MaterialTheme.colorScheme.primary.copy(alpha = 0.08f), Radius.md),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Outlined.AddPhotoAlternate, null, tint = MaterialTheme.colorScheme.primary)
            }
            Spacer(Modifier.width(Space.md))
            Column {
                Text("افزودن عکس رسید", style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Bold)
                Text(
                    "با عکس رسید، شمارهٔ پیگیری اختیاری است",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun ReceiptPreview(bytes: ByteArray, progress: Float?, onChange: () -> Unit, onRemove: () -> Unit) {
    val reduced = LocalReducedMotion.current
    val bitmap = remember(bytes) { bytes.toImageBitmapOrNull() }
    val uploading = progress != null
    val scrim by animateFloatAsState(if (uploading) 0.5f else 0f, label = "receiptScrim")
    val shown by animateFloatAsState(
        progress ?: 0f,
        if (reduced) tween(0) else spring(stiffness = Spring.StiffnessLow),
        label = "receiptProgress",
    )
    Box(
        Modifier
            .fillMaxWidth()
            .height(180.dp)
            .clip(Radius.md)
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .semantics { contentDescription = "عکس رسید" },
    ) {
        if (bitmap != null) {
            Image(bitmap, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        }
        Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = scrim)))
        AnimatedVisibility(
            visible = uploading,
            enter = fadeIn() + scaleIn(initialScale = 0.8f),
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.Center),
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(Space.sm)) {
                Box(contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(
                        progress = { shown },
                        modifier = Modifier.size(56.dp),
                        color = Color.White,
                        trackColor = Color.White.copy(alpha = 0.25f),
                        strokeWidth = 4.dp,
                    )
                    Text(
                        "${(shown * 100).toInt().toPersianDigits()}٪",
                        color = Color.White,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                    )
                }
                Text("در حال ارسال رسید", color = Color.White, style = MaterialTheme.typography.labelMedium)
            }
        }
        AnimatedVisibility(
            visible = !uploading,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.fillMaxSize(),
        ) {
            Box(Modifier.fillMaxSize().padding(Space.sm)) {
                Surface(
                    onClick = onRemove,
                    shape = CircleShape,
                    color = Color.Black.copy(alpha = 0.55f),
                    contentColor = Color.White,
                    modifier = Modifier.align(Alignment.TopEnd).size(32.dp),
                ) {
                    Box(contentAlignment = Alignment.Center) { Icon(Icons.Rounded.Close, "حذف عکس", Modifier.size(18.dp)) }
                }
                Surface(
                    onClick = onChange,
                    shape = CircleShape,
                    color = Color.Black.copy(alpha = 0.55f),
                    contentColor = Color.White,
                    modifier = Modifier.align(Alignment.BottomStart),
                ) {
                    Text(
                        "تغییر عکس",
                        style = MaterialTheme.typography.labelMedium,
                        modifier = Modifier.padding(horizontal = Space.md, vertical = 6.dp),
                    )
                }
            }
        }
    }
}
