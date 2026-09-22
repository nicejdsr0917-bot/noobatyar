package xyz.sattar.javid.proqueue.core.utils

import android.content.Intent
import android.graphics.Bitmap
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.core.content.FileProvider
import java.io.ByteArrayOutputStream
import java.io.File

actual fun encodePng(image: ImageBitmap): ByteArray {
    val stream = ByteArrayOutputStream()
    image.asAndroidBitmap().compress(Bitmap.CompressFormat.PNG, 100, stream)
    return stream.toByteArray()
}

actual fun shareImage(bytes: ByteArray, fileName: String, caption: String) {
    val context = xyz.sattar.javid.proqueue.ProQueueApp.appContext

    // Matches the one path declared in res/xml/shared_images_paths.xml.
    val dir = File(context.cacheDir, "shared_images").apply { mkdirs() }
    val file = File(dir, fileName).apply { writeBytes(bytes) }
    val uri = FileProvider.getUriForFile(
        context,
        "${context.packageName}.fileprovider",
        file,
    )

    val send = Intent(Intent.ACTION_SEND).apply {
        type = "image/png"
        putExtra(Intent.EXTRA_STREAM, uri)
        if (caption.isNotBlank()) putExtra(Intent.EXTRA_TEXT, caption)
        // The grant on the Intent itself covers whichever app the owner picks
        // from the chooser; a grantUriPermission() call would need to name the
        // target package, which isn't known until after they pick.
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    context.startActivity(
        Intent.createChooser(send, null).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    )
}
