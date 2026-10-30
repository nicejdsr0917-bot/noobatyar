package xyz.sattar.javid.proqueue.core.ui.components

import xyz.sattar.javid.proqueue.core.i18n.Str
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import xyz.sattar.javid.proqueue.core.utils.cropImageToRect
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * Fixed-aspect rectangular counterpart of [ImageCropperDialog], for portfolio
 * photos (1:1) and certificates (4:5).
 *
 * Same gesture model — the photo covers a window that is panned and pinched —
 * but the window is `aspectRatio` (width / height) wide instead of a square,
 * and the result is handed to [cropImageToRect] at exactly
 * [outputWidth] x [outputHeight], which is what the server wants.
 *
 * Pan is held in units of the window's *height* on both axes (the window is
 * `aspectRatio` x 1 in those units), which keeps the crop independent of how
 * large the window happens to be drawn.
 */
@Composable
fun RectImageCropperDialog(
    source: ByteArray,
    bitmap: ImageBitmap,
    aspectRatio: Float,
    outputWidth: Int,
    outputHeight: Int,
    onDismiss: () -> Unit,
    onCropped: (ByteArray) -> Unit
) {
    val scope = rememberCoroutineScope()
    var isWorking by remember { mutableStateOf(false) }
    var zoom by remember { mutableStateOf(1f) }
    var pan by remember { mutableStateOf(Offset.Zero) }

    Dialog(
        onDismissRequest = { if (!isWorking) onDismiss() },
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.95f))
        ) {
            // Respect both dimensions (see ImageCropperDialog) and keep the
            // header and footer chrome clear of the window.
            val chromeReserved = 200.dp
            val windowHeight = minOf(maxHeight - chromeReserved, (maxWidth - 48.dp) / aspectRatio)
                .coerceIn(120.dp, 640.dp)
            val windowWidth = windowHeight * aspectRatio

            Box(
                modifier = Modifier
                    .align(Alignment.Center)
                    .size(windowWidth, windowHeight)
                    .clipToBounds()
                    .pointerInput(bitmap, aspectRatio) {
                        detectTransformGestures { _, dragged, gestureZoom, _ ->
                            if (size.height <= 0) return@detectTransformGestures
                            val unit = size.height.toFloat()
                            val newZoom = (zoom * gestureZoom).coerceIn(1f, 6f)
                            zoom = newZoom
                            pan = clampRectPan(
                                candidate = pan + Offset(dragged.x / unit, dragged.y / unit),
                                bitmap = bitmap,
                                aspectRatio = aspectRatio,
                                zoom = newZoom
                            )
                        }
                    }
            ) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val unit = size.height
                    val scale = max(size.width / bitmap.width, size.height / bitmap.height) * zoom
                    val shownW = bitmap.width * scale
                    val shownH = bitmap.height * scale

                    drawImage(
                        image = bitmap,
                        srcOffset = IntOffset.Zero,
                        srcSize = IntSize(bitmap.width, bitmap.height),
                        dstOffset = IntOffset(
                            (center.x - shownW / 2f + pan.x * unit).roundToInt(),
                            (center.y - shownH / 2f + pan.y * unit).roundToInt()
                        ),
                        dstSize = IntSize(shownW.roundToInt(), shownH.roundToInt())
                    )
                    drawRoundRect(
                        color = Color.White.copy(alpha = 0.9f),
                        topLeft = Offset.Zero,
                        size = Size(size.width, size.height),
                        cornerRadius = CornerRadius(4f, 4f),
                        style = Stroke(width = 2f)
                    )
                }
            }

            Row(
                modifier = Modifier
                    .widthIn(max = 480.dp)
                    .align(Alignment.TopCenter)
                    .statusBarsPadding()
                    .padding(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onDismiss, enabled = !isWorking) {
                    Icon(Icons.Rounded.Close, contentDescription = Str.close, tint = Color.White)
                }
                Text(
                    text = Str.showcase_crop_title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
            }

            Column(
                modifier = Modifier
                    .widthIn(max = 480.dp)
                    .align(Alignment.BottomCenter)
                    .navigationBarsPadding()
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = Str.drag_to_move_pinch_to,
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.White.copy(alpha = 0.75f)
                )
                Spacer(modifier = Modifier.height(16.dp))
                AppButton(
                    text = Str.confirm_and_crop,
                    onClick = {
                        if (isWorking) return@AppButton
                        isWorking = true
                        val window = rectCropWindow(bitmap, aspectRatio, zoom, pan)
                        scope.launch {
                            val result = withContext(Dispatchers.Default) {
                                cropImageToRect(
                                    source = source,
                                    leftFraction = window[0],
                                    topFraction = window[1],
                                    widthFraction = window[2],
                                    heightFraction = window[3],
                                    outputWidth = outputWidth,
                                    outputHeight = outputHeight
                                )
                            }
                            isWorking = false
                            if (result != null) onCropped(result) else onDismiss()
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    isLoading = isWorking
                )
            }
        }
    }
}

/**
 * Cover scale in window-height units per source pixel: the smaller of the two
 * fits the window exactly, so the photo always fills it.
 */
private fun coverScale(bitmap: ImageBitmap, aspectRatio: Float, zoom: Float): Float =
    max(aspectRatio / bitmap.width, 1f / bitmap.height) * zoom

/** Keeps the photo covering the window so the crop can never include empty space. */
internal fun clampRectPan(candidate: Offset, bitmap: ImageBitmap, aspectRatio: Float, zoom: Float): Offset {
    val cover = coverScale(bitmap, aspectRatio, zoom)
    val maxX = max(0f, (bitmap.width * cover - aspectRatio) / 2f)
    val maxY = max(0f, (bitmap.height * cover - 1f) / 2f)
    return Offset(candidate.x.coerceIn(-maxX, maxX), candidate.y.coerceIn(-maxY, maxY))
}

/** The current transform as `[left, top, width, height]` fractions of the source image. */
internal fun rectCropWindow(bitmap: ImageBitmap, aspectRatio: Float, zoom: Float, pan: Offset): FloatArray {
    val w = bitmap.width.toFloat()
    val h = bitmap.height.toFloat()
    val cover = coverScale(bitmap, aspectRatio, zoom)
    val srcW = aspectRatio / cover
    val srcH = 1f / cover

    val left = (((w - srcW) / 2f - pan.x / cover) / w).coerceIn(0f, 1f)
    val top = (((h - srcH) / 2f - pan.y / cover) / h).coerceIn(0f, 1f)
    return floatArrayOf(
        left,
        top,
        (srcW / w).coerceIn(0f, 1f - left),
        (srcH / h).coerceIn(0f, 1f - top)
    )
}
