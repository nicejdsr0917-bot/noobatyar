package xyz.sattar.javid.proqueue.core.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer

/**
 * Renders its content once into an offscreen [GraphicsLayer] as well as to the
 * screen, so the exact pixels the owner is looking at can be handed back as an
 * [ImageBitmap] — which is what "share this card as a picture" needs.
 *
 * Done with Compose's own graphics layer rather than by reaching for the
 * Android View hierarchy (PixelCopy / View.draw), for two reasons: that route
 * has no iOS equivalent, and it captures the *window*, so anything overlapping
 * the card — a scrollbar, the share sheet animating in, the status bar —
 * lands in the exported image. This captures the composable and nothing else.
 *
 * The capture is the layer's own recording, so the card does not have to be
 * fully on screen, at a particular scale, or unobstructed to export cleanly.
 */
class CaptureController internal constructor(
    internal val layer: GraphicsLayer,
) {
    suspend fun capture(): ImageBitmap = layer.toImageBitmap()
}

@Composable
fun rememberCaptureController(): CaptureController {
    val layer = rememberGraphicsLayer()
    return remember(layer) { CaptureController(layer) }
}

@Composable
fun Capturable(
    controller: CaptureController,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Box(
        modifier = modifier.drawWithContent {
            controller.layer.record { this@drawWithContent.drawContent() }
            drawLayer(controller.layer)
        }
    ) {
        content()
    }
}
