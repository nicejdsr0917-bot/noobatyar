package xyz.sattar.javid.proqueue.core.utils

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asSkiaBitmap
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.usePinned
import org.jetbrains.skia.EncodedImageFormat
import org.jetbrains.skia.Image
import platform.Foundation.NSData
import platform.Foundation.create
import platform.UIKit.UIActivityViewController
import platform.UIKit.UIApplication
import platform.UIKit.popoverPresentationController
import platform.UIKit.UIImage
import platform.posix.memcpy

actual fun encodePng(image: ImageBitmap): ByteArray =
    Image.makeFromBitmap(image.asSkiaBitmap())
        .encodeToData(EncodedImageFormat.PNG)
        ?.bytes
        ?: ByteArray(0)

@OptIn(ExperimentalForeignApi::class)
private fun ByteArray.toNSData(): NSData = usePinned { pinned ->
    NSData.create(bytes = pinned.addressOf(0), length = size.toULong())
}

actual fun shareImage(bytes: ByteArray, fileName: String, caption: String) {
    if (bytes.isEmpty()) return
    val root = UIApplication.sharedApplication.keyWindow?.rootViewController ?: return
    val uiImage = UIImage(data = bytes.toNSData())

    // A UIImage plus the caption string, rather than a temp file URL: iOS
    // shows the image itself in the sheet's preview this way, and every
    // target that only wants one of the two (Instagram wants the image,
    // Messages wants both) picks what it can use.
    val items = if (caption.isBlank()) listOf(uiImage) else listOf(uiImage, caption)
    val controller = UIActivityViewController(
        activityItems = items,
        applicationActivities = null,
    )
    controller.popoverPresentationController?.sourceView = root.view
    (root.presentedViewController ?: root)
        .presentViewController(controller, animated = true, completion = null)
}
