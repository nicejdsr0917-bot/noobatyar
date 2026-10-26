package xyz.sattar.javid.proqueue.feature.more

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.DarkMode
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.LightMode
import androidx.compose.material.icons.rounded.PhoneAndroid
import androidx.compose.material.icons.rounded.Policy
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import xyz.sattar.javid.proqueue.core.prefs.PreferencesManager
import xyz.sattar.javid.proqueue.core.state.AppThemeMode
import xyz.sattar.javid.proqueue.core.state.ThemeStateHolder
import xyz.sattar.javid.proqueue.core.utils.AppConfig
import xyz.sattar.javid.proqueue.core.utils.AppInfo
import xyz.sattar.javid.proqueue.core.utils.openUrl
import xyz.sattar.javid.proqueue.core.utils.shareText
import xyz.sattar.javid.proqueue.feature.discovery.Brand
import xyz.sattar.javid.proqueue.feature.discovery.OWNER_LANDING_URL
import xyz.sattar.javid.proqueue.feature.discovery.OwnerInviteBanner
import xyz.sattar.javid.proqueue.feature.discovery.toPersianDigits

@Composable
fun MoreScreen(onOpenAbout: () -> Unit, bottomPadding: Dp) {
    val themeMode by ThemeStateHolder.themeMode.collectAsState()
    val scope = rememberCoroutineScope()

    Column(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .verticalScroll(rememberScrollState())
            .windowInsetsPadding(WindowInsets.statusBars)
            .padding(horizontal = 16.dp)
            .padding(top = 16.dp, bottom = bottomPadding + 24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            "بیشتر",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Black,
            modifier = Modifier.padding(horizontal = 4.dp)
        )

        OwnerInviteBanner(onClick = { openUrl(OWNER_LANDING_URL) })

        Group(title = "ظاهر") {
            Row(
                Modifier.padding(12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                listOf(
                    Triple(AppThemeMode.SYSTEM, "خودکار", Icons.Rounded.PhoneAndroid),
                    Triple(AppThemeMode.LIGHT, "روشن", Icons.Rounded.LightMode),
                    Triple(AppThemeMode.DARK, "تیره", Icons.Rounded.DarkMode),
                ).forEach { (mode, label, icon) ->
                    val selected = themeMode == mode
                    val bg by animateColorAsState(if (selected) Brand.Violet500 else MaterialTheme.colorScheme.surfaceVariant)
                    val fg = if (selected) Color.White else MaterialTheme.colorScheme.onSurface
                    Surface(
                        onClick = {
                            ThemeStateHolder.setThemeMode(mode)
                            scope.launch { PreferencesManager.setThemeMode(mode) }
                        },
                        shape = RoundedCornerShape(14.dp),
                        color = bg,
                        modifier = Modifier.weight(1f)
                    ) {
                        Column(Modifier.padding(vertical = 12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(icon, contentDescription = null, tint = fg)
                            Spacer(Modifier.height(4.dp))
                            Text(label, color = fg, style = MaterialTheme.typography.labelLarge)
                        }
                    }
                }
            }
        }

        Group(title = "نوبت‌یار") {
            Item(Icons.Rounded.Share, "معرفی به دوستان") {
                shareText("نوبت‌یار — نزدیک‌ترین پزشک، سالن و مشاور را پیدا کن و آنلاین نوبت بگیر:\n$OWNER_LANDING_URL")
            }
            Item(Icons.Rounded.Policy, "حریم خصوصی") { openUrl("${AppConfig.bookingBaseUrl}/privacy") }
            Item(Icons.Rounded.Info, "درباره ما", onClick = onOpenAbout)
        }

        Text(
            "نسخه ${AppInfo.versionName.toPersianDigits()}",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.align(Alignment.CenterHorizontally)
        )
    }
}

@Composable
private fun Group(title: String, content: @Composable () -> Unit) {
    Column {
        Text(
            title,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 4.dp, bottom = 8.dp)
        )
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = MaterialTheme.colorScheme.surface,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column { content() }
        }
    }
}

@Composable
private fun Item(icon: ImageVector, label: String, onClick: () -> Unit) {
    Surface(onClick = onClick, color = Color.Transparent, modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.padding(horizontal = 16.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .size(36.dp)
                    .background(Brand.Violet500.copy(alpha = 0.1f), RoundedCornerShape(10.dp)),
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, contentDescription = null, tint = Brand.Violet500, modifier = Modifier.size(20.dp))
            }
            Spacer(Modifier.width(12.dp))
            Text(label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
            Icon(
                Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
