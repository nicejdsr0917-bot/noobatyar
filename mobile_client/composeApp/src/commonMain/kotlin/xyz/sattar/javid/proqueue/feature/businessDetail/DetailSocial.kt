package xyz.sattar.javid.proqueue.feature.businessDetail

import androidx.compose.animation.core.LinearEasing
import xyz.sattar.javid.proqueue.feature.booking.pressScale
import kotlinx.coroutines.delay
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.draw.shadow
import androidx.compose.runtime.remember
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.painterResource
import proqueue.composeapp.generated.resources.Res
import proqueue.composeapp.generated.resources.ic_brand_bale
import proqueue.composeapp.generated.resources.ic_brand_eitaa
import proqueue.composeapp.generated.resources.ic_brand_instagram
import proqueue.composeapp.generated.resources.ic_brand_rubika
import proqueue.composeapp.generated.resources.ic_brand_telegram
import proqueue.composeapp.generated.resources.ic_brand_website
import proqueue.composeapp.generated.resources.ic_brand_whatsapp
import xyz.sattar.javid.proqueue.core.utils.openUrl
import xyz.sattar.javid.proqueue.feature.booking.LocalReducedMotion
import xyz.sattar.javid.proqueue.feature.discovery.toPersianDigits

/** Tiles and icons from the owner app's About screen; paddings scaled to the 60dp tile. */
private class SocialNetwork(
    val kind: String,
    val label: String,
    val icon: DrawableResource,
    val tile: Brush,
    val iconPadding: Dp,
    /** Null keeps the icon's own colours (the PNG logos). */
    val tint: Color? = null,
    val url: (String) -> String,
    val show: (String) -> String = { "@$it" },
)

private val InstagramGradient = Brush.linearGradient(
    listOf(Color(0xFFFEDA75), Color(0xFFFA7E1E), Color(0xFFD62976), Color(0xFF962FBF), Color(0xFF4F5BD5))
)

private val SocialNetworks = listOf(
    SocialNetwork("instagram", "اینستاگرام", Res.drawable.ic_brand_instagram, InstagramGradient, 15.dp,
        url = { "https://instagram.com/$it" }),
    SocialNetwork("telegram", "تلگرام", Res.drawable.ic_brand_telegram,
        Brush.linearGradient(listOf(Color(0xFF37AEE2), Color(0xFF1E96C8))), 14.dp, url = { "https://t.me/$it" }),
    SocialNetwork("whatsapp", "واتساپ", Res.drawable.ic_brand_whatsapp, SolidColor(Color(0xFF25D366)), 14.dp,
        tint = Color.White, url = { "https://wa.me/98${it.removePrefix("0")}" }, show = { it.toPersianDigits() }),
    SocialNetwork("eitaa", "ایتا", Res.drawable.ic_brand_eitaa, SolidColor(Color(0xFFF47435)), 0.dp,
        url = { "https://eitaa.com/$it" }),
    SocialNetwork("bale", "بله", Res.drawable.ic_brand_bale, SolidColor(Color(0xFF00B894)), 0.dp,
        url = { "https://ble.ir/$it" }),
    // Rubika's icon is a hexagon on transparency; it sits on a white plate.
    SocialNetwork("rubika", "روبیکا", Res.drawable.ic_brand_rubika, SolidColor(Color.White), 4.dp,
        url = { "https://rubika.ir/$it" }),
    SocialNetwork("website", "وب‌سایت", Res.drawable.ic_brand_website,
        Brush.linearGradient(listOf(Color(0xFFA855F7), Color(0xFF6D28D9))), 14.dp,
        url = { if (it.startsWith("http")) it else "https://$it" },
        show = { it.removePrefix("https://").removePrefix("http://").removePrefix("www.").trimEnd('/') }),
)

/**
 * The business's social pages: a row of brand tiles (the owner app's logos
 * and colours) that spring in one after another, with the owner app's light
 * sweeping across them, and open the page on tap. The server stores bare
 * handles, so each URL is built here and can only point at the network its
 * key names.
 */
@Composable
internal fun SocialLinksSection(links: Map<String, String>, businessTitle: String, index: Int) {
    val items = SocialNetworks.mapNotNull { n -> links[n.kind]?.takeIf { it.isNotBlank() }?.let { n to it } }
    if (items.isEmpty()) return
    GroupCard(
        // Named after the business (or doctor) itself: the card sits among
        // several, and "follow us" would not say whose pages these are.
        title = "$businessTitle در شبکه‌های اجتماعی",
        index = index,
        bleed = {
            LazyRow(
                contentPadding = BleedPadding,
                horizontalArrangement = Arrangement.spacedBy(14.dp),
                modifier = Modifier.padding(bottom = 4.dp),
            ) {
                itemsIndexed(items, key = { _, it -> it.first.kind }) { i, (net, value) ->
                    SocialTile(net, value, i)
                }
            }
        },
    )
}

@Composable
private fun SocialTile(net: SocialNetwork, value: String, order: Int) {
    val reduced = LocalReducedMotion.current
    val pop = remember { Animatable(if (reduced) 1f else 0f) }
    LaunchedEffect(Unit) {
        if (reduced) return@LaunchedEffect
        delay(order * 70L)
        pop.animateTo(1f, spring(dampingRatio = 0.48f, stiffness = Spring.StiffnessMediumLow))
    }
    // The owner app's sheen, staggered along the row so it reads as one
    // highlight travelling through the section.
    val transition = rememberInfiniteTransition(label = "social-$order")
    val sheen by transition.animateFloat(
        0f, 1f, infiniteRepeatable(tween(5200, easing = LinearEasing)), label = "sheen-$order",
    )
    val phase = ((sheen + order * 0.12f) % 1f) * 1.9f - 0.45f
    val interaction = remember { MutableInteractionSource() }
    val shape = RoundedCornerShape(18.dp)

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .width(76.dp)
            .graphicsLayer {
                val p = pop.value
                alpha = p.coerceIn(0f, 1f)
                scaleX = 0.55f + 0.45f * p
                scaleY = 0.55f + 0.45f * p
                rotationZ = (1f - p) * -14f
                translationY = (1f - p) * 24f
            }
            .pressScale(interaction, 0.9f)
            .clickable(interactionSource = interaction, indication = null) { openUrl(net.url(value)) },
    ) {
        Box(
            Modifier
                .size(60.dp)
                .shadow(6.dp, shape, ambientColor = Color.Black.copy(alpha = 0.2f), spotColor = Color.Black.copy(alpha = 0.2f))
                .clip(shape)
                .background(net.tile)
                .drawWithContent {
                    drawContent()
                    if (reduced) return@drawWithContent
                    val band = size.width * 0.35f
                    val cx = size.width * phase
                    drawRect(
                        Brush.linearGradient(
                            listOf(Color.Transparent, Color.White.copy(alpha = 0.28f), Color.Transparent),
                            start = Offset(cx - band, 0f),
                            end = Offset(cx + band, size.height),
                        )
                    )
                },
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                painter = painterResource(net.icon),
                contentDescription = net.label,
                tint = net.tint ?: Color.Unspecified,
                modifier = Modifier.fillMaxSize().padding(net.iconPadding),
            )
        }
        Spacer(Modifier.height(8.dp))
        Text(net.label, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, maxLines = 1)
        Text(
            // LTR isolate: a Latin handle in an RTL paragraph would show "@name" as "name@".
            "\u2066${net.show(value)}\u2069",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
        )
    }
}
