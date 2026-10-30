package xyz.sattar.javid.proqueue.core.ui.components.map

import okhttp3.OkHttpClient
import org.maplibre.android.MapLibre
import xyz.sattar.javid.proqueue.ProQueueApp
import org.maplibre.android.module.http.HttpRequestUtil

actual fun installMapAuth(apiKey: String, userAgent: String) {
    // MapLibre must exist before its HTTP layer can be configured.
    MapLibre.getInstance(ProQueueApp.appContext)
    val client = OkHttpClient.Builder()
        .addInterceptor { chain ->
            val request = chain.request()
            val builder = request.newBuilder().header("User-Agent", userAgent)
            // Only Map.ir gets the key: it must never leak to tile hosts we don't own.
            if (apiKey.isNotBlank() && request.url.host.endsWith("map.ir")) builder.header("x-api-key", apiKey)
            chain.proceed(builder.build())
        }
        .build()
    HttpRequestUtil.setOkHttpClient(client)
}
