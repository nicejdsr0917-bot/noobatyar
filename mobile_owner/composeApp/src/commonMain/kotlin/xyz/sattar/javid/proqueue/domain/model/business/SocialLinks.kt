package xyz.sattar.javid.proqueue.domain.model.business

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/**
 * The business's social pages, keyed like the server's `social_links`
 * (backend/business/social_links.py). The server reduces whatever the owner
 * pastes (`@name`, a profile URL, `wa.me/98…`) to a bare handle, a mobile
 * number for WhatsApp, or an https URL for the website.
 */
object SocialLinks {
    /** Display order, same as the server's SOCIAL_KINDS. */
    val kinds = listOf("instagram", "telegram", "whatsapp", "eitaa", "bale", "rubika", "website")

    /** Blank entries dropped; the server would drop them anyway. */
    fun clean(links: Map<String, String>): Map<String, String> =
        kinds.mapNotNull { k -> links[k]?.trim()?.takeIf { it.isNotEmpty() }?.let { k to it } }.toMap()

    fun encode(links: Map<String, String>): String =
        JsonObject(clean(links).mapValues { JsonPrimitive(it.value) }).toString()

    fun decode(raw: String): Map<String, String> =
        if (raw.isBlank()) emptyMap()
        else runCatching {
            Json.parseToJsonElement(raw).let { it as JsonObject }
                .mapValues { (it.value as JsonPrimitive).content }
        }.getOrDefault(emptyMap())
}
