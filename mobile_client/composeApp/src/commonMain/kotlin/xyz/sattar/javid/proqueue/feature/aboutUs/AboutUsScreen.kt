package xyz.sattar.javid.proqueue.feature.aboutUs

import xyz.sattar.javid.proqueue.core.config.socialChannels
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import org.jetbrains.compose.resources.painterResource
import proqueue.composeapp.generated.resources.Res
import proqueue.composeapp.generated.resources.ic_social_bale
import proqueue.composeapp.generated.resources.ic_social_eitaa
import proqueue.composeapp.generated.resources.ic_social_instagram
import proqueue.composeapp.generated.resources.ic_social_rubika
import proqueue.composeapp.generated.resources.ic_social_telegram
import proqueue.composeapp.generated.resources.ic_social_website
import proqueue.composeapp.generated.resources.main_icon
import xyz.sattar.javid.proqueue.core.utils.AppInfo
import xyz.sattar.javid.proqueue.core.utils.openUrl
import xyz.sattar.javid.proqueue.feature.discovery.BackIcon
import xyz.sattar.javid.proqueue.feature.discovery.ChevronIcon
import xyz.sattar.javid.proqueue.feature.discovery.Hairline
import xyz.sattar.javid.proqueue.feature.discovery.Radius
import xyz.sattar.javid.proqueue.feature.discovery.Space
import xyz.sattar.javid.proqueue.feature.discovery.toPersianDigits

private data class Channel(
    val name: String,
    val handle: String,
    val url: String,
    val icon: Painter,
    /** The network's own brand colour — the one place colour is allowed to vary. */
    val tile: Color,
)

private fun channelIcon(id: String) = when (id) {
    "bale" -> Res.drawable.ic_social_bale
    "eitaa" -> Res.drawable.ic_social_eitaa
    "rubika" -> Res.drawable.ic_social_rubika
    "telegram" -> Res.drawable.ic_social_telegram
    "instagram" -> Res.drawable.ic_social_instagram
    else -> Res.drawable.ic_social_website
}

@Composable
fun AboutUsScreen(onNavigateBack: () -> Unit) {
    val channels = socialChannels.map { spec ->
        Channel(spec.name, spec.handle, spec.url, painterResource(channelIcon(spec.id)), Color(spec.tile))
    }

    Column(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .windowInsetsPadding(WindowInsets.statusBars)
    ) {
        Row(Modifier.height(56.dp).padding(horizontal = Space.xs), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onNavigateBack) { Icon(BackIcon, contentDescription = "بازگشت") }
            Text("درباره ما", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        }
        Hairline()

        Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
            Column(
                Modifier.fillMaxWidth().padding(horizontal = Space.xl, vertical = 32.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Image(painterResource(Res.drawable.main_icon), contentDescription = null, modifier = Modifier.size(64.dp))
                Spacer(Modifier.height(Space.lg))
                Text("نوبت‌یار", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Text(
                    "نسخه ${AppInfo.versionName.toPersianDigits()}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(Space.lg))
                Text(
                    "نزدیک‌ترین پزشک، سالن و مشاور را پیدا کنید، " +
                        "بیمه‌های پذیرفته‌شده را ببینید و بدون تماس نوبت بگیرید.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
            }

            Box(Modifier.fillMaxWidth().height(8.dp).background(MaterialTheme.colorScheme.surfaceVariant))
            Text(
                "ما را دنبال کنید",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = Space.page, top = Space.lg, bottom = Space.sm)
            )
            channels.forEachIndexed { i, c ->
                ChannelRow(c)
                if (i != channels.lastIndex) Hairline(Modifier.padding(start = 64.dp))
            }
            Spacer(Modifier.height(Space.xl))
            Spacer(Modifier.windowInsetsPadding(WindowInsets.navigationBars))
        }
    }
}

@Composable
private fun ChannelRow(channel: Channel) {
    Surface(onClick = { openUrl(channel.url) }, color = Color.Transparent, modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.height(60.dp).padding(horizontal = Space.page), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(32.dp).clip(Radius.sm).background(channel.tile),
                contentAlignment = Alignment.Center
            ) {
                Image(channel.icon, contentDescription = null, modifier = Modifier.size(18.dp))
            }
            Spacer(Modifier.width(Space.lg))
            Column(Modifier.weight(1f)) {
                Text(channel.name, style = MaterialTheme.typography.bodyLarge)
                // LTR isolate: keeps "@noobatyar" from being reordered to "noobatyar@" in an RTL paragraph.
                Text(
                    "⁦${channel.handle}⁩",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Icon(ChevronIcon, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(20.dp))
        }
    }
}
