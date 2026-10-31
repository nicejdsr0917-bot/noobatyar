package xyz.sattar.javid.proqueue.feature.more

import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.Info
import com.composables.icons.lucide.LogIn
import com.composables.icons.lucide.LogOut
import com.composables.icons.lucide.Share2
import com.composables.icons.lucide.ShieldCheck
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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import com.composables.icons.lucide.ShieldPlus
import xyz.sattar.javid.proqueue.feature.explore.InsuranceSheet
import org.koin.compose.viewmodel.koinViewModel
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
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
import xyz.sattar.javid.proqueue.core.config.OWNER_LANDING_URL
import xyz.sattar.javid.proqueue.feature.discovery.OwnerInviteRow
import xyz.sattar.javid.proqueue.feature.discovery.Radius
import xyz.sattar.javid.proqueue.feature.discovery.Space
import xyz.sattar.javid.proqueue.feature.discovery.toPersianDigits

@Composable
fun MoreScreen(
    onOpenAbout: () -> Unit,
    onSignIn: () -> Unit,
    bottomPadding: Dp,
    myInsurance: MyInsuranceViewModel = koinViewModel(),
) {
    val insurance by myInsurance.state.collectAsState()
    var pickInsurance by remember { mutableStateOf(false) }
    val themeMode by ThemeStateHolder.themeMode.collectAsState()
    val signedIn by PreferencesManager.visitorToken.collectAsState(initial = PreferencesManager.getVisitorToken())
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

        GroupTitle("حساب کاربری")
        Hairline()
        if (signedIn != null) {
            ListRow(Lucide.LogOut, "خروج از حساب", showChevron = false, onClick = {
                scope.launch { PreferencesManager.setVisitorToken(null) }
            })
        } else {
            ListRow(Lucide.LogIn, "ورود یا ثبت‌نام", onClick = onSignIn)
        }
        Hairline()

        GroupTitle("بیمه")
        Hairline()
        ListRow(
            Lucide.ShieldPlus, "بیمه تکمیلی من", value = insurance.mine?.name ?: "تعیین نشده",
            onClick = { pickInsurance = true },
        )
        Hairline()

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
        ListRow(Lucide.Share2, "معرفی به دوستان", onClick = {
            shareText("نوبت‌یار — پزشک، سالن و مشاور نزدیکت را پیدا کن و آنلاین نوبت بگیر:\n$OWNER_LANDING_URL")
        })
        Hairline(Modifier.padding(start = 54.dp))
        ListRow(Lucide.ShieldCheck, "حریم خصوصی", onClick = { openUrl("${AppConfig.bookingBaseUrl}/privacy") })
        Hairline(Modifier.padding(start = 54.dp))
        ListRow(Lucide.Info, "درباره ما", onClick = onOpenAbout)
        Hairline()

        Text(
            "نسخه ${AppInfo.versionName.toPersianDigits()}",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.align(Alignment.CenterHorizontally).padding(top = Space.xl)
        )
    }

    if (pickInsurance) {
        InsuranceSheet(
            insurances = insurance.insurances,
            selectedSlug = insurance.mySlug,
            onDismiss = { pickInsurance = false },
            onSelect = {
                myInsurance.select(it)
                pickInsurance = false
            },
            title = "بیمه تکمیلی من",
            subtitle = "پزشک‌های نزدیک که بیمه شما را اعلام کرده‌اند، در کاوش پیش‌فرض نشان داده می‌شوند.",
            noneLabel = "ندارم یا نمی‌خواهم",
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
