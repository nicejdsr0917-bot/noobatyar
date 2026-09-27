package xyz.sattar.javid.proqueue.core.utils

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asSkiaBitmap
import org.jetbrains.skia.EncodedImageFormat
import org.jetbrains.skia.Image
import org.khronos.webgl.Int8Array
import org.khronos.webgl.toInt8Array

// Same Skia call the iOS actual makes: Compose on wasm draws with Skia too.
actual fun encodePng(image: ImageBitmap): ByteArray =
    Image.makeFromBitmap(image.asSkiaBitmap())
        .encodeToData(EncodedImageFormat.PNG)
        ?.bytes
        ?: ByteArray(0)

/**
 * Shares the card as a real file where the browser can (iOS Safari and
 * Android Chrome support file shares), otherwise downloads it — on desktop
 * the owner then drags the saved PNG into whichever app they post from.
 */
@OptIn(kotlin.js.ExperimentalWasmJsInterop::class)
@JsFun(
    """(bytes, fileName, caption) => {
        const blob = new Blob([bytes], { type: 'image/png' });
        const file = new File([blob], fileName, { type: 'image/png' });
        if (navigator.canShare && navigator.canShare({ files: [file] })) {
            navigator.share({ files: [file], text: caption }).catch(() => {});
            return;
        }
        const url = URL.createObjectURL(blob);
        const a = document.createElement('a');
        a.href = url;
        a.download = fileName;
        document.body.appendChild(a);
        a.click();
        a.remove();
        setTimeout(() => URL.revokeObjectURL(url), 1000);
    }"""
)
private external fun jsShareImage(bytes: Int8Array, fileName: String, caption: String)

actual fun shareImage(bytes: ByteArray, fileName: String, caption: String) =
    jsShareImage(bytes.toInt8Array(), fileName, caption)
