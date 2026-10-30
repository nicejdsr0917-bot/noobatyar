package xyz.sattar.javid.proqueue.core.utils

import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.graphics.ImageBitmap
import com.preat.peekaboo.image.picker.ResizeOptions
import com.preat.peekaboo.image.picker.SelectionMode
import com.preat.peekaboo.image.picker.rememberImagePickerLauncher
import com.preat.peekaboo.image.picker.toImageBitmap

@Composable
actual fun rememberImagePicker(onSingleImagePicked: (ByteArray) -> Unit): ImagePickerLauncher {
    val scope = rememberCoroutineScope()
    val peekabooLauncher = rememberImagePickerLauncher(
        selectionMode = SelectionMode.Single,
        // Shrunk on the device: receipts go up over slow mobile data.
        resizeOptions = ResizeOptions(width = 1600, height = 1600, resizeThresholdBytes = 512 * 1024L, compressionQuality = 0.85),
        scope = scope,
        onResult = { byteArrays ->
            byteArrays.firstOrNull()?.let(onSingleImagePicked)
        }
    )
    return object : ImagePickerLauncher {
        override fun launch() = peekabooLauncher.launch()
    }
}

actual fun ByteArray.toImageBitmapOrNull(): ImageBitmap? = try {
    toImageBitmap()
} catch (e: Exception) {
    null
}
