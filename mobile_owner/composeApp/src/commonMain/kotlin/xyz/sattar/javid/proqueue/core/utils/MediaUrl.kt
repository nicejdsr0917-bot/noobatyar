package xyz.sattar.javid.proqueue.core.utils

/**
 * Turns a server-provided image path into a loadable URL: absolute URLs pass
 * through, a relative `/media/...` path is resolved against the API host.
 */
fun mediaUrl(path: String): String =
    if (path.startsWith("http")) path else "${AppConfig.baseUrl}$path"
