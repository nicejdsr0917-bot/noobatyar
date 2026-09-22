package xyz.sattar.javid.proqueue.core.utils

import android.content.Intent

actual fun shareText(text: String) {
    val context = xyz.sattar.javid.proqueue.ProQueueApp.appContext
    val send = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_TEXT, text)
    }
    // Always a chooser, never the last-used app: the owner shares the same
    // link to different customers on different messengers, so "remember my
    // choice" would be wrong nearly every time.
    context.startActivity(
        Intent.createChooser(send, null).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    )
}
