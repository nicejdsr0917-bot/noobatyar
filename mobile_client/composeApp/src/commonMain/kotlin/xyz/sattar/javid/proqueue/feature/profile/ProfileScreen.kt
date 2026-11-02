package xyz.sattar.javid.proqueue.feature.profile

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.composables.icons.lucide.Bookmark
import com.composables.icons.lucide.CalendarDays
import com.composables.icons.lucide.Camera
import com.composables.icons.lucide.Info
import com.composables.icons.lucide.LogIn
import com.composables.icons.lucide.LogOut
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.Pencil
import com.composables.icons.lucide.Share2
import com.composables.icons.lucide.ShieldCheck
import com.composables.icons.lucide.ShieldPlus
import com.composables.icons.lucide.Store
import com.composables.icons.lucide.SunMoon
import kotlinx.coroutines.launch
import org.koin.compose.viewmodel.koinViewModel
import xyz.sattar.javid.proqueue.core.config.OWNER_LANDING_URL
import xyz.sattar.javid.proqueue.core.prefs.PreferencesManager
import xyz.sattar.javid.proqueue.core.state.AppThemeMode
import xyz.sattar.javid.proqueue.core.state.ThemeStateHolder
import xyz.sattar.javid.proqueue.core.ui.components.ToastyHost
import xyz.sattar.javid.proqueue.core.ui.components.UiMessage
import xyz.sattar.javid.proqueue.core.ui.components.showToasty
import xyz.sattar.javid.proqueue.core.utils.AppConfig
import xyz.sattar.javid.proqueue.core.utils.AppInfo
import xyz.sattar.javid.proqueue.core.utils.openUrl
import xyz.sattar.javid.proqueue.core.utils.rememberImagePicker
import xyz.sattar.javid.proqueue.core.utils.rememberReducedMotion
import xyz.sattar.javid.proqueue.core.utils.shareText
import xyz.sattar.javid.proqueue.feature.booking.LocalReducedMotion
import xyz.sattar.javid.proqueue.feature.booking.enterStagger
import xyz.sattar.javid.proqueue.feature.booking.pressScale
import xyz.sattar.javid.proqueue.feature.discovery.ChevronIcon
import xyz.sattar.javid.proqueue.feature.discovery.Space
import xyz.sattar.javid.proqueue.feature.discovery.toPersianDigits
import xyz.sattar.javid.proqueue.feature.explore.InsuranceSheet
import xyz.sattar.javid.proqueue.feature.more.MyInsuranceViewModel
import xyz.sattar.javid.proqueue.ui.theme.ErrorColor
import xyz.sattar.javid.proqueue.ui.theme.ErrorColorDark
import xyz.sattar.javid.proqueue.ui.theme.LocalIsDarkTheme

private val CardShape = RoundedCornerShape(20.dp)

// One soft hue per row: tells rows apart at a glance without shouting.
private val TintAccount = Color(0xFF7C3AED)
private val TintInsurance = Color(0xFF0D9488)
private val TintTheme = Color(0xFFD97706)
private val TintShare = Color(0xFFDB2777)
private val TintPrivacy = Color(0xFF16A34A)
private val TintAbout = Color(0xFF2563EB)
private val TintOwner = Color(0xFF475569)

/**
 * The profile tab (was «بیشتر»): who is signed in, at the top like a social
 * profile — photo in the brand ring, name, number, edit — then the visitor's
 * counts, settings as rounded groups with tinted icon tiles, and sign-out.
 */
@Composable
fun ProfileScreen(
    profile: ProfileViewModel,
    onOpenAbout: () -> Unit,
    onSignIn: () -> Unit,
    onOpenAppointments: () -> Unit,
    onOpenSaved: () -> Unit,
    myInsurance: MyInsuranceViewModel = koinViewModel(),
) {
    val state by profile.state.collectAsState()
    val insurance by myInsurance.state.collectAsState()
    val themeMode by ThemeStateHolder.themeMode.collectAsState()
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    var editing by remember { mutableStateOf(false) }
    var pickInsurance by remember { mutableStateOf(false) }
    var confirmSignOut by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) { profile.refresh() }
    LaunchedEffect(Unit) {
        profile.messages.collect {
            when (it) {
                is ProfileMessage.Success -> snackbar.showToasty(UiMessage.success(it.text))
                is ProfileMessage.Error -> snackbar.showToasty(UiMessage.error(it.text))
            }
        }
    }

    CompositionLocalProvider(LocalReducedMotion provides rememberReducedMotion()) {
        Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surfaceVariant)) {
            Column(
                Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .windowInsetsPadding(WindowInsets.statusBars)
                    .padding(bottom = Space.xl),
                verticalArrangement = Arrangement.spacedBy(Space.md),
            ) {
                Text(
                    "پروفایل",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(start = Space.page, end = Space.page, top = Space.lg),
                )

                var card = 0
                ProfileHeader(
                    state = state,
                    onEdit = { editing = true },
                    onSignIn = onSignIn,
                    onOpenAppointments = onOpenAppointments,
                    onOpenSaved = onOpenSaved,
                    modifier = Modifier.enterStagger(card++),
                )

                Group(title = "حساب", index = card++) {
                    if (state.signedIn) {
                        MenuRow(Lucide.Pencil, TintAccount, "ویرایش پروفایل", subtitle = "نام و عکس") { editing = true }
                        RowDivider()
                    }
                    MenuRow(
                        Lucide.ShieldPlus, TintInsurance, "بیمه تکمیلی من",
                        value = insurance.mine?.name ?: "تعیین نشده",
                    ) { pickInsurance = true }
                }

                Group(title = "ظاهر", index = card++) {
                    Row(Modifier.padding(horizontal = Space.lg, vertical = Space.sm), verticalAlignment = Alignment.CenterVertically) {
                        IconTile(Lucide.SunMoon, TintTheme)
                        Spacer(Modifier.width(Space.md))
                        Text("تم", style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
                    }
                    ThemeSwitch(themeMode) { mode ->
                        ThemeStateHolder.setThemeMode(mode)
                        scope.launch { PreferencesManager.setThemeMode(mode) }
                    }
                    Spacer(Modifier.height(Space.sm))
                }

                Group(title = "نوبت‌یار", index = card++) {
                    MenuRow(Lucide.Share2, TintShare, "معرفی به دوستان") {
                        shareText("نوبت‌یار — پزشک، سالن و مشاور نزدیکت را پیدا کن و آنلاین نوبت بگیر:\n$OWNER_LANDING_URL")
                    }
                    RowDivider()
                    MenuRow(Lucide.Store, TintOwner, "صاحب کسب‌وکار هستید؟", subtitle = "مرکزتان را ثبت کنید و آنلاین نوبت بدهید") {
                        openUrl(OWNER_LANDING_URL)
                    }
                    RowDivider()
                    MenuRow(Lucide.ShieldCheck, TintPrivacy, "حریم خصوصی") { openUrl("${AppConfig.bookingBaseUrl}/privacy") }
                    RowDivider()
                    MenuRow(Lucide.Info, TintAbout, "درباره ما", onClick = onOpenAbout)
                }

                if (state.signedIn) {
                    val red = if (LocalIsDarkTheme.current) ErrorColorDark else ErrorColor
                    Group(title = null, index = card++) {
                        MenuRow(Lucide.LogOut, red, "خروج از حساب", titleColor = red, chevron = false) { confirmSignOut = true }
                    }
                }

                Text(
                    "نسخه ${AppInfo.versionName.toPersianDigits()}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.align(Alignment.CenterHorizontally).padding(top = Space.sm),
                )
            }
            ToastyHost(hostState = snackbar)
        }
    }

    if (editing) {
        EditProfileSheet(state = state, profile = profile, onDismiss = { editing = false })
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
    if (confirmSignOut) {
        AlertDialog(
            onDismissRequest = { confirmSignOut = false },
            title = { Text("خروج از حساب") },
            text = { Text("نوبت‌هایتان سر جایشان می‌مانند؛ برای دیدنشان دوباره وارد شوید.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmSignOut = false
                    profile.signOut()
                }) { Text("خروج") }
            },
            dismissButton = { TextButton(onClick = { confirmSignOut = false }) { Text("انصراف") } },
        )
    }
}

// ── Header ──────────────────────────────────────────────────────────────────

@Composable
private fun ProfileHeader(
    state: ProfileState,
    onEdit: () -> Unit,
    onSignIn: () -> Unit,
    onOpenAppointments: () -> Unit,
    onOpenSaved: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(shape = CardShape, color = MaterialTheme.colorScheme.surface, modifier = modifier.fillMaxWidth().padding(horizontal = Space.page)) {
        Column(Modifier.padding(Space.lg)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box {
                    RingedAvatar(state.me?.avatar, 88.dp, Modifier.clickable(enabled = state.signedIn, onClick = onEdit))
                    if (state.signedIn) {
                        Box(
                            Modifier
                                .align(Alignment.BottomEnd)
                                .size(28.dp)
                                .background(MaterialTheme.colorScheme.primary, CircleShape)
                                .border(2.dp, MaterialTheme.colorScheme.surface, CircleShape),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(Lucide.Camera, contentDescription = "تغییر عکس", tint = MaterialTheme.colorScheme.onPrimary, modifier = Modifier.size(14.dp))
                        }
                    }
                }
                Spacer(Modifier.width(Space.lg))
                if (state.signedIn) {
                    Row(Modifier.weight(1f), horizontalArrangement = Arrangement.SpaceEvenly) {
                        Stat(state.appointmentCount?.toPersianDigits() ?: "–", "نوبت", onOpenAppointments)
                        Stat(state.savedCount.toPersianDigits(), "نشان‌شده", onOpenSaved)
                    }
                } else {
                    Column(Modifier.weight(1f)) {
                        Text("مهمان", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                        Text(
                            "وارد شوید تا نوبت‌هایتان روی هر گوشی همراهتان باشد.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
            Spacer(Modifier.height(Space.md))
            if (state.signedIn) {
                val me = state.me
                Text(
                    me?.fullName?.ifBlank { null } ?: if (state.loading) "…" else "بدون نام",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                me?.phoneNumber?.takeIf { it.isNotBlank() }?.let {
                    Text(
                        it.toPersianDigits(),
                        style = MaterialTheme.typography.bodyMedium.copy(textDirection = TextDirection.Ltr),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Spacer(Modifier.height(Space.md))
                FilledTonalButton(onClick = onEdit, shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth().height(44.dp)) {
                    Icon(Lucide.Pencil, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(Space.sm))
                    Text("ویرایش پروفایل", fontWeight = FontWeight.Bold)
                }
            } else {
                Button(onClick = onSignIn, shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth().height(48.dp)) {
                    Icon(Lucide.LogIn, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(Space.sm))
                    Text("ورود یا ثبت‌نام", fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
private fun Stat(value: String, label: String, onClick: () -> Unit) {
    Column(
        Modifier.clip(RoundedCornerShape(12.dp)).clickable(onClick = onClick).padding(horizontal = Space.md, vertical = Space.xs),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(value, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

// ── Menu ────────────────────────────────────────────────────────────────────

@Composable
private fun Group(title: String?, index: Int, content: @Composable ColumnScope.() -> Unit) {
    Column(Modifier.enterStagger(index)) {
        if (title != null) {
            Text(
                title,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = Space.page + Space.xs, end = Space.page, bottom = Space.sm),
            )
        }
        Surface(shape = CardShape, color = MaterialTheme.colorScheme.surface, modifier = Modifier.fillMaxWidth().padding(horizontal = Space.page)) {
            Column(Modifier.padding(vertical = Space.xs), content = content)
        }
    }
}

@Composable
private fun RowDivider() {
    Box(
        Modifier
            .fillMaxWidth()
            .padding(start = 64.dp, end = Space.lg)
            .height(1.dp)
            .background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
    )
}

/** A rounded square of the row's tint at low strength, with the icon in full tint. */
@Composable
private fun IconTile(icon: ImageVector, tint: Color) {
    Box(
        Modifier
            .size(36.dp)
            .background(tint.copy(alpha = if (LocalIsDarkTheme.current) 0.22f else 0.12f), RoundedCornerShape(10.dp)),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(18.dp))
    }
}

@Composable
private fun MenuRow(
    icon: ImageVector,
    tint: Color,
    title: String,
    subtitle: String? = null,
    value: String? = null,
    titleColor: Color = Color.Unspecified,
    chevron: Boolean = true,
    onClick: () -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    Row(
        Modifier
            .fillMaxWidth()
            .pressScale(interaction, 0.98f)
            .clickable(interactionSource = interaction, indication = androidx.compose.material3.ripple(), onClick = onClick)
            .padding(horizontal = Space.lg, vertical = Space.md),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconTile(icon, tint)
        Spacer(Modifier.width(Space.md))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium, color = titleColor)
            if (subtitle != null) {
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        if (value != null) {
            Text(value, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(start = Space.sm))
        }
        if (chevron) {
            Icon(ChevronIcon, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(18.dp))
        }
    }
}

/** Three segments with a pill that slides (and springs) to the chosen one. */
@Composable
private fun ThemeSwitch(selected: AppThemeMode, onSelect: (AppThemeMode) -> Unit) {
    val options = listOf(AppThemeMode.SYSTEM to "خودکار", AppThemeMode.LIGHT to "روشن", AppThemeMode.DARK to "تیره")
    val index = options.indexOfFirst { it.first == selected }.coerceAtLeast(0)
    BoxWithConstraints(
        Modifier
            .padding(horizontal = Space.lg, vertical = Space.xs)
            .fillMaxWidth()
            .height(44.dp)
            .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(12.dp))
            .padding(4.dp)
    ) {
        val segment = maxWidth / options.size
        val x by animateDpAsState(segment * index, spring(Spring.DampingRatioLowBouncy, Spring.StiffnessMediumLow), label = "themePill")
        Surface(
            shape = RoundedCornerShape(9.dp),
            color = MaterialTheme.colorScheme.surface,
            shadowElevation = 2.dp,
            modifier = Modifier.offset(x = x).width(segment).fillMaxHeight(),
        ) {}
        Row(Modifier.fillMaxSize()) {
            options.forEach { (mode, label) ->
                val on = mode == selected
                val color by animateColorAsState(if (on) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant, label = "themeLabel")
                Box(
                    Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { onSelect(mode) },
                    contentAlignment = Alignment.Center,
                ) {
                    Text(label, style = MaterialTheme.typography.labelLarge, fontWeight = if (on) FontWeight.Bold else FontWeight.Medium, color = color)
                }
            }
        }
    }
}

// ── Edit sheet ──────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EditProfileSheet(state: ProfileState, profile: ProfileViewModel, onDismiss: () -> Unit) {
    val sheet = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()
    var name by remember { mutableStateOf(state.me?.fullName.orEmpty()) }
    val picker = rememberImagePicker { bytes -> profile.setPhoto(bytes) }
    val close: () -> Unit = { scope.launch { sheet.hide() }.invokeOnCompletion { onDismiss() } }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheet, containerColor = MaterialTheme.colorScheme.surface) {
        Column(
            Modifier.fillMaxWidth().padding(horizontal = Space.page).padding(bottom = Space.xl),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text("ویرایش پروفایل", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(Space.lg))
            Box(contentAlignment = Alignment.Center) {
                RingedAvatar(state.me?.avatar, 112.dp, Modifier.clickable { picker.launch() })
                // Explicit: inside this Column the scoped overload would be picked instead.
                androidx.compose.animation.AnimatedVisibility(visible = state.uploadingPhoto, enter = fadeIn(), exit = fadeOut()) {
                    Box(Modifier.size(100.dp).background(Color.Black.copy(alpha = 0.4f), CircleShape), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = Color.White, strokeWidth = 2.dp, modifier = Modifier.size(28.dp))
                    }
                }
            }
            Row(horizontalArrangement = Arrangement.Center) {
                TextButton(onClick = { picker.launch() }, enabled = !state.uploadingPhoto) {
                    Icon(Lucide.Camera, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(if (state.me?.avatar == null) "افزودن عکس" else "تغییر عکس")
                }
                if (state.me?.avatar != null) {
                    TextButton(onClick = { profile.removePhoto() }, enabled = !state.uploadingPhoto) {
                        Text("حذف عکس", color = MaterialTheme.colorScheme.error)
                    }
                }
            }
            Spacer(Modifier.height(Space.md))
            OutlinedTextField(
                value = name,
                onValueChange = { if (it.length <= 60) name = it },
                label = { Text("نام و نام خانوادگی") },
                singleLine = true,
                shape = RoundedCornerShape(12.dp),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(Space.sm))
            OutlinedTextField(
                value = state.me?.phoneNumber.orEmpty().toPersianDigits(),
                onValueChange = {},
                enabled = false,
                label = { Text("شماره موبایل") },
                supportingText = { Text("شماره با کد پیامکی تایید شده و از اینجا عوض نمی‌شود.") },
                singleLine = true,
                shape = RoundedCornerShape(12.dp),
                textStyle = MaterialTheme.typography.bodyLarge.copy(textDirection = TextDirection.Ltr),
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(Space.lg))
            Button(
                onClick = { profile.rename(name) { close() } },
                enabled = !state.savingName && name.trim().length >= 2,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth().height(50.dp),
            ) {
                if (state.savingName) CircularProgressIndicator(strokeWidth = 2.dp, modifier = Modifier.size(20.dp), color = MaterialTheme.colorScheme.onPrimary)
                else Text("ذخیره", fontWeight = FontWeight.Bold)
            }
        }
    }
}
