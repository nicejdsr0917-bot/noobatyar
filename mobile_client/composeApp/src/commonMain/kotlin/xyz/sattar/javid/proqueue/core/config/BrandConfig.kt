package xyz.sattar.javid.proqueue.core.config

/** Public web page the owner app / landing lives on — the "register your business" CTA target. */
const val OWNER_LANDING_URL = "https://noobatyar.ir"

/** One of Noobatyar's public channels. [tile] is the network's own brand colour (ARGB). */
data class SocialChannelSpec(
    val id: String,
    val name: String,
    val handle: String,
    val url: String,
    val tile: Long,
)

/** Channels listed on the About screen, in display order. */
val socialChannels: List<SocialChannelSpec> = listOf(
    SocialChannelSpec("bale", "بله", "ble.ir/noobatyar", "https://ble.ir/noobatyar", 0xFF07A77A),
    SocialChannelSpec("eitaa", "ایتا", "eitaa.com/noobatyar", "https://eitaa.com/noobatyar", 0xFFEA6D0E),
    SocialChannelSpec("rubika", "روبیکا", "rubika.ir/noobatyar", "https://rubika.ir/noobatyar", 0xFF1C83B6),
    SocialChannelSpec("telegram", "تلگرام", "t.me/noobatyar", "https://t.me/noobatyar", 0xFF229ED9),
    SocialChannelSpec("instagram", "اینستاگرام", "@noobatyar", "https://instagram.com/noobatyar", 0xFFD62976),
    SocialChannelSpec("website", "سایت", "noobatyar.ir", "https://noobatyar.ir", 0xFF7C3AED),
)
