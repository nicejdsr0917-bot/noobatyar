package xyz.sattar.javid.proqueue.core.network

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi

/**
 * The `user_id` claim of a SimpleJWT access token, or null if the token
 * can't be read. Not verified — only used to know whose profile to fetch;
 * the server checks the signature on that request as on any other.
 *
 * Needed where the signed-in user's id is otherwise unknown: the web panel
 * kept the user only in memory, so after a page reload it had valid tokens
 * but no idea who it was signed in as.
 */
@OptIn(ExperimentalEncodingApi::class)
fun userIdFromJwt(token: String): Int? = runCatching {
    val payload = token.split('.')[1]
    val json = Base64.UrlSafe
        .withPadding(Base64.PaddingOption.ABSENT_OPTIONAL)
        .decode(payload)
        .decodeToString()
    // SimpleJWT writes the claim as a string ("2"); accept a number too.
    Json.parseToJsonElement(json).jsonObject["user_id"]?.jsonPrimitive?.content?.toIntOrNull()
}.getOrNull()
