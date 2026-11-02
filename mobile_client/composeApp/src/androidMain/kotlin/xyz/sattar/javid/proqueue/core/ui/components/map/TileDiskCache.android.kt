package xyz.sattar.javid.proqueue.core.ui.components.map

import xyz.sattar.javid.proqueue.ProQueueApp

internal actual fun platformCacheDir(): String = ProQueueApp.appContext.cacheDir.absolutePath
