package xyz.sattar.javid.proqueue.feature.createBusiness

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.painterResource
import proqueue.composeapp.generated.resources.Res
import proqueue.composeapp.generated.resources.ic_social_bale
import proqueue.composeapp.generated.resources.ic_social_eitaa
import proqueue.composeapp.generated.resources.ic_social_instagram
import proqueue.composeapp.generated.resources.ic_social_rubika
import proqueue.composeapp.generated.resources.ic_social_telegram
import proqueue.composeapp.generated.resources.ic_social_website
import proqueue.composeapp.generated.resources.whatsapp
import xyz.sattar.javid.proqueue.core.i18n.Str
import xyz.sattar.javid.proqueue.core.ui.components.AppTextField
import xyz.sattar.javid.proqueue.domain.model.business.SocialLinks

private class Network(
    val kind: String,
    val label: String,
    val icon: DrawableResource,
    val tile: Brush,
    val iconPadding: Dp,
    /** The vector icons are white glyphs; the PNG logos keep their colours. */
    val tintWhite: Boolean,
    val placeholder: String,
    val keyboard: KeyboardType = KeyboardType.Uri,
)

private val InstagramTile = Brush.linearGradient(
    listOf(Color(0xFFFEDA75), Color(0xFFFA7E1E), Color(0xFFD62976), Color(0xFF962FBF), Color(0xFF4F5BD5))
)

@Composable
private fun networks() = listOf(
    Network("instagram", Str.instagram, Res.drawable.ic_social_instagram, InstagramTile, 9.dp, true, "@salon.name"),
    Network("telegram", Str.telegram, Res.drawable.ic_social_telegram,
        Brush.linearGradient(listOf(Color(0xFF37AEE2), Color(0xFF1E96C8))), 8.dp, true, "@salon_name"),
    Network("whatsapp", Str.whatsapp, Res.drawable.whatsapp, SolidColor(Color(0xFF25D366)), 8.dp, true,
        Str.social_whatsapp_hint, KeyboardType.Phone),
    Network("eitaa", Str.social_eitaa, Res.drawable.ic_social_eitaa, SolidColor(Color(0xFFF47435)), 0.dp, false, "@salon_name"),
    Network("bale", Str.social_bale, Res.drawable.ic_social_bale, SolidColor(Color(0xFF00B894)), 0.dp, false, "@salon_name"),
    Network("rubika", Str.social_rubika, Res.drawable.ic_social_rubika, SolidColor(Color.White), 3.dp, false, "@salon_name"),
    Network("website", Str.social_website, Res.drawable.ic_social_website,
        Brush.linearGradient(listOf(Color(0xFFA855F7), Color(0xFF6D28D9))), 8.dp, true, Str.social_website_hint),
)

/**
 * The business's social pages, one row per network. Each row's logo tile is
 * greyed out while the field is empty and springs to its brand colours once
 * something is typed, so the owner sees at a glance which pages are linked.
 * Whatever is pasted (a handle, `@name`, a profile URL) is fine: the server
 * reduces it to the stored form (backend/business/social_links.py).
 */
@Composable
fun SocialLinksField(
    links: Map<String, String>,
    onLinks: (Map<String, String>) -> Unit,
    enabled: Boolean,
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(
            text = Str.social_links_title,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Text(
            text = Str.social_links_hint,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        networks().forEachIndexed { index, net ->
            val value = links[net.kind].orEmpty()
            SocialRow(
                net = net,
                value = value,
                index = index,
                enabled = enabled,
                onValue = { v ->
                    onLinks(SocialLinks.kinds.associateWith { k -> if (k == net.kind) v else links[k].orEmpty() }
                        .filterValues { it.isNotEmpty() })
                },
            )
        }
    }
}

@Composable
private fun SocialRow(net: Network, value: String, index: Int, enabled: Boolean, onValue: (String) -> Unit) {
    val filled = value.isNotBlank()

    // Rows slide in one after another the first time the form shows them.
    val enter = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        delay(index * 45L)
        enter.animateTo(1f, tween(320, easing = FastOutSlowInEasing))
    }
    val lift = with(LocalDensity.current) { 14.dp.toPx() }

    // The tile pops when a link is added and settles back to grey when cleared.
    val tileScale by animateFloatAsState(
        if (filled) 1f else 0.88f,
        spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMediumLow),
        label = "tileScale",
    )
    val saturation by animateFloatAsState(if (filled) 1f else 0f, tween(260), label = "tileColour")
    val checkAlpha by animateFloatAsState(if (filled) 1f else 0f, tween(200), label = "check")
    val ring by animateColorAsState(
        if (filled) MaterialTheme.colorScheme.primary.copy(alpha = 0.35f) else Color.Transparent,
        tween(260), label = "ring",
    )

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .graphicsLayer {
                alpha = enter.value
                translationY = (1f - enter.value) * lift
            },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Box(
            Modifier
                .size(46.dp)
                .graphicsLayer { scaleX = tileScale; scaleY = tileScale }
                .border(2.dp, ring, RoundedCornerShape(15.dp))
                .padding(3.dp)
                .clip(RoundedCornerShape(12.dp)),
            contentAlignment = Alignment.Center,
        ) {
            // Grey plate underneath, brand plate fading in over it.
            Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surfaceVariant))
            Box(Modifier.fillMaxSize().graphicsLayer { alpha = saturation }.background(net.tile))
            Image(
                painter = painterResource(net.icon),
                contentDescription = net.label,
                colorFilter = when {
                    net.tintWhite && filled -> ColorFilter.tint(Color.White)
                    net.tintWhite -> ColorFilter.tint(MaterialTheme.colorScheme.onSurfaceVariant)
                    else -> null
                },
                modifier = Modifier
                    .fillMaxSize()
                    .padding(net.iconPadding)
                    .graphicsLayer { alpha = 0.55f + 0.45f * saturation },
            )
        }
        AppTextField(
            enabled = enabled,
            value = value,
            onValueChange = onValue,
            label = net.label,
            placeholder = net.placeholder,
            maxLength = 200,
            keyboardType = net.keyboard,
            trailingIcon = {
                Icon(
                    Icons.Rounded.CheckCircle,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp).graphicsLayer {
                        alpha = checkAlpha; scaleX = 0.6f + 0.4f * checkAlpha; scaleY = 0.6f + 0.4f * checkAlpha
                    },
                )
            },
            modifier = Modifier.weight(1f),
        )
    }
}
