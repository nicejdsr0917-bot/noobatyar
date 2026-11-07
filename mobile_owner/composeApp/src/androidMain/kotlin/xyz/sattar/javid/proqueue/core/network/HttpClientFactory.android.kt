package xyz.sattar.javid.proqueue.core.network

import com.chuckerteam.chucker.api.ChuckerInterceptor
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.DefaultRequest
import io.ktor.client.request.header
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.http.URLProtocol
import io.ktor.http.path
import io.ktor.serialization.kotlinx.json.json
import io.ktor.client.plugins.logging.LogLevel
import io.ktor.client.plugins.logging.Logger
import io.ktor.client.plugins.logging.Logging
import io.ktor.http.HttpHeaders
import io.ktor.client.plugins.logging.SIMPLE
import xyz.sattar.javid.proqueue.ProQueueApp

actual object HttpClientFactory {
    actual fun create(): HttpClient = HttpClient(OkHttp) {
        engine {
            addInterceptor(ChuckerInterceptor.Builder(ProQueueApp.appContext).build())
        }

        install(ContentNegotiation) {
            json(kotlinx.serialization.json.Json {
                ignoreUnknownKeys = true
                isLenient = true
            })
        }
        install(Logging) {
            logger = Logger.SIMPLE
            // HEADERS (not ALL): LogLevel.ALL reads the request body to log it,
            // which consumes the one-shot MultiPartFormDataContent channel and
            // makes the business PUT arrive at the server with an empty body.
            level = LogLevel.HEADERS
            // The access token is a live credential: it went to the browser
            // console / logcat in full on every request, release builds too.
            sanitizeHeader { header -> header == HttpHeaders.Authorization }
        }
        install(AuthInterceptor)
        install(DefaultRequest) {
            url("${xyz.sattar.javid.proqueue.core.utils.AppConfig.baseUrl}/api/")
            // Store builds (Myket) tell the server so prices shown match the store's.
            // Read per request: the billing is set in MainActivity after this is built.
            xyz.sattar.javid.proqueue.core.billing.StoreBillingProvider.billing?.let {
                header("X-Store", it.storeKey)
            }
        }
    }
}
