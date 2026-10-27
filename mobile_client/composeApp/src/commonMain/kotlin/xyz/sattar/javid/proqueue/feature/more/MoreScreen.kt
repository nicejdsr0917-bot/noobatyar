package xyz.sattar.javid.proqueue.feature.more

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.PrivacyTip
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
import xyz.sattar.javid.proqueue.feature.discovery.Hairline
import xyz.sattar.javid.proqueue.feature.discovery.ListRow
import xyz.sattar.javid.proqueue.feature.discovery.OWNER_LANDING_URL
import xyz.sattar.javid.proqueue.feature.discovery.OwnerInviteRow
import xyz.sattar.javid.proqueue.feature.discovery.Radius
import xyz.sattar.javid.proqueue.feature.discovery.Space
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
            .padding(bottom = bottomPadding + Space.xl)
    ) {
        Text(
            "بیشتر",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = Space.page, vertical = Space.lg)
        )

        OwnerInviteRow(
            onClick = { openUrl(OWNER_LANDING_URL) },
            modifier = Modifier.padding(horizontal = Space.page)
        )

        GroupTitle("ظاهر")
        ThemeSegmentedControl(
            selected = themeMode,
            onSelect = { mode ->
                ThemeStateHolder.setThemeMode(mode)
                scope.launch { PreferencesManager.setThemeMode(mode) }
            }
        )

        GroupTitle("نوبت‌یار")
        Hairline()
        ListRow(Icons.Outlined.Share, "معرفی به دوستان", onClick = {
            shareText("نوبت‌یار — پزشک، سالن و مشاور نزدیکت را پیدا کن و آنلاین نوبت بگیر:\n$OWNER_LANDING_URL")
        })
        Hairline(Modifier.padding(start = 54.dp))
        ListRow(Icons.Outlined.PrivacyTip, "حریم خصوصی", onClick = { openUrl("${AppConfig.bookingBaseUrl}/privacy") })
        Hairline(Modifier.padding(start = 54.dp))
        ListRow(Icons.Outlined.Info, "درباره ما", onClick = onOpenAbout)
        Hairline()

        Text(
            "نسخه ${AppInfo.versionName.toPersianDigits()}",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.align(Alignment.CenterHorizontally).padding(top = Space.xl)
        )
    }
}

@Composable
private fun GroupTitle(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(start = Space.page, end = Space.page, top = Space.xl, bottom = Space.sm)
    )
}

/** One bordered control with three segments — the platform idiom for a single exclusive choice. */
@Composable
private fun ThemeSegmentedControl(selected: AppThemeMode, onSelect: (AppThemeMode) -> Unit) {
    val options = listOf(
        AppThemeMode.SYSTEM to "خودکار",
        AppThemeMode.LIGHT to "روشن",
        AppThemeMode.DARK to "تیره",
    )
    Surface(
        shape = Radius.md,
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        modifier = Modifier.padding(horizontal = Space.page).fillMaxWidth().height(44.dp)
    ) {
        Row(Modifier.padding(Space.xs)) {
            options.forEach { (mode, label) ->
                val isSelected = mode == selected
                val bg by animateColorAsState(
                    if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface
                )
                Surface(
                    onClick = { onSelect(mode) },
                    shape = Radius.sm,
                    color = bg,
                    modifier = Modifier.weight(1f).fillMaxHeight()
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = androidx.compose.foundation.layout.Arrangement.Center) {
                        Text(
                            label,
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                            color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}
