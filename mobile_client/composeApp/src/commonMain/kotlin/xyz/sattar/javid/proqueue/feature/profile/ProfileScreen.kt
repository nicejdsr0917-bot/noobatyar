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
import androidx.compose.foundation.layout.IntrinsicSize
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
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.text.style.TextOverflow
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
import com.composables.icons.lucide.UserCheck
import kotlinx.coroutines.launch
import org.koin.compose.viewmodel.koinViewModel
import xyz.sattar.javid.proqueue.core.config.OWNER_LANDING_URL
import xyz.sattar.javid.proqueue.core.prefs.PreferencesManager
import xyz.sattar.javid.proqueue.core.state.AppThemeMode
import xyz.sattar.javid.proqueue.core.state.ThemeStateHolder
import xyz.sattar.javid.proqueue.core.ui.components.ImageCropperDialog
import xyz.sattar.javid.proqueue.core.ui.components.ToastyHost
import xyz.sattar.javid.proqueue.core.ui.components.UiMessage
import xyz.sattar.javid.proqueue.core.ui.components.showToasty
import xyz.sattar.javid.proqueue.core.utils.AppConfig
import xyz.sattar.javid.proqueue.core.utils.AppInfo
import xyz.sattar.javid.proqueue.core.utils.openUrl
import xyz.sattar.javid.proqueue.core.utils.rememberImagePicker
import xyz.sattar.javid.proqueue.core.utils.rememberReducedMotion
import xyz.sattar.javid.proqueue.core.utils.shareText
import xyz.sattar.javid.proqueue.core.utils.toImageBitmapOrNull
import xyz.sattar.javid.proqueue.data.remoteDataSource.profile.BIO_MAX_LENGTH
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

private val CardShape = RoundedCornerShape(16.dp)

/** One hairline for every card and divider on the tab, so nothing competes with the content. */
@Composable
private fun hairline() = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.7f)

/**
 * The profile tab: the person first — photo, name and their own short bio —
 * then what they have here (bookings, pages they follow, saved places) as one
 * quiet strip, then settings as plain grouped lists. Neutral surfaces, thin
 * outlines and single-colour icons: the colour on the screen is the brand's
 * accent, used only where something can be pressed.
 */
@Composable
fun ProfileScreen(
    profile: ProfileViewModel,
    onOpenAbout: () -> Unit,
    onSignIn: () -> Unit,
    onOpenAppointments: () -> Unit,
    onOpenSaved: () -> Unit,
    onOpenFollowing: () -> Unit = {},
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
        Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
            Column(
                Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .windowInsetsPadding(WindowInsets.statusBars)
                    .padding(bottom = Space.xl),
                verticalArrangement = Arrangement.spacedBy(Space.xl),
            ) {
                var card = 0
                Column(Modifier.enterStagger(card++)) {
                    Text(
                        "پروفایل",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(start = Space.page, end = Space.page, top = Space.lg, bottom = Space.lg),
                    )
                    if (state.signedIn) {
                        SignedInHeader(state, onEdit = { editing = true })
                        Spacer(Modifier.height(Space.lg))
                        StatsStrip(
                            listOf(
                                Triple(state.appointmentCount, "نوبت", onOpenAppointments),
                                Triple(state.followingCount, "دنبال می‌کنید", onOpenFollowing),
                                Triple(state.savedCount, "نشان‌شده", onOpenSaved),
                            )
                        )
                    } else {
                        GuestHeader(onSignIn)
                    }
                }

                Group(title = "فعالیت من", index = card++) {
                    if (state.signedIn) {
                        MenuRow(Lucide.CalendarDays, "نوبت‌های من", onClick = onOpenAppointments)
                        RowDivider()
                        MenuRow(Lucide.UserCheck, "صفحه‌هایی که دنبال می‌کنید", onClick = onOpenFollowing)
                        RowDivider()
                    }
                    MenuRow(Lucide.Bookmark, "نشان‌شده‌ها", onClick = onOpenSaved)
                    RowDivider()
                    MenuRow(Lucide.ShieldPlus, "بیمه تکمیلی من", value = insurance.mine?.name ?: "تعیین نشده") { pickInsurance = true }
                }

                Group(title = "ظاهر", index = card++) {
                    Row(Modifier.padding(start = Space.lg, end = Space.lg, top = Space.md), verticalAlignment = Alignment.CenterVertically) {
                        RowIcon(Lucide.SunMoon)
                        Spacer(Modifier.width(Space.md))
                        Text("تم", style = MaterialTheme.typography.bodyLarge)
                    }
                    ThemeSwitch(themeMode) { mode ->
                        ThemeStateHolder.setThemeMode(mode)
                        scope.launch { PreferencesManager.setThemeMode(mode) }
                    }
                    Spacer(Modifier.height(Space.sm))
                }

                Group(title = "نوبت‌یار", index = card++) {
                    MenuRow(Lucide.Share2, "معرفی به دوستان") {
                        shareText("نوبت‌یار — پزشک، سالن و مشاور نزدیکت را پیدا کن و آنلاین نوبت بگیر:\n$OWNER_LANDING_URL")
                    }
                    RowDivider()
                    MenuRow(Lucide.Store, "صاحب کسب‌وکار هستید؟", subtitle = "مرکزتان را ثبت کنید و آنلاین نوبت بدهید") {
                        openUrl(OWNER_LANDING_URL)
                    }
                    RowDivider()
                    MenuRow(Lucide.ShieldCheck, "حریم خصوصی") { openUrl("${AppConfig.bookingBaseUrl}/privacy") }
                    RowDivider()
                    MenuRow(Lucide.Info, "درباره ما", onClick = onOpenAbout)
                }

                if (state.signedIn) {
                    val red = if (LocalIsDarkTheme.current) ErrorColorDark else ErrorColor
                    Group(title = null, index = card++) {
                        MenuRow(Lucide.LogOut, "خروج از حساب", tint = red, titleColor = red, chevron = false) { confirmSignOut = true }
                    }
                }

                Text(
                    "نسخه ${AppInfo.versionName.toPersianDigits()}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.align(Alignment.CenterHorizontally),
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
private fun SignedInHeader(state: ProfileState, onEdit: () -> Unit) {
    val me = state.me
    Column(Modifier.padding(horizontal = Space.page)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box {
                ProfileAvatar(
                    me?.avatar, 80.dp,
                    Modifier
                        .clip(CircleShape)
                        .border(1.dp, hairline(), CircleShape)
                        .clickable(onClick = onEdit),
                )
                Box(
                    Modifier
                        .align(Alignment.BottomEnd)
                        .size(26.dp)
                        .background(MaterialTheme.colorScheme.onSurface, CircleShape)
                        .border(2.dp, MaterialTheme.colorScheme.background, CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Lucide.Camera, contentDescription = "تغییر عکس", tint = MaterialTheme.colorScheme.surface, modifier = Modifier.size(13.dp))
                }
            }
            Spacer(Modifier.width(Space.lg))
            Column(Modifier.weight(1f)) {
                Text(
                    me?.fullName?.ifBlank { null } ?: if (state.loading) "…" else "بدون نام",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(2.dp))
                val bio = me?.bio.orEmpty()
                if (bio.isNotBlank()) {
                    Text(
                        bio,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 4,
                        overflow = TextOverflow.Ellipsis,
                    )
                } else if (me != null) {
                    Text(
                        "چند کلمه درباره خودتان بنویسید",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.clip(RoundedCornerShape(6.dp)).clickable(onClick = onEdit),
                    )
                }
            }
        }
        Spacer(Modifier.height(Space.lg))
        OutlinedButton(
            onClick = onEdit,
            shape = RoundedCornerShape(12.dp),
            border = BorderStroke(1.dp, hairline()),
            modifier = Modifier.fillMaxWidth().height(42.dp),
        ) {
            Icon(Lucide.Pencil, contentDescription = null, tint = MaterialTheme.colorScheme.onSurface, modifier = Modifier.size(15.dp))
            Spacer(Modifier.width(Space.sm))
            Text("ویرایش پروفایل", color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.Medium)
        }
    }
}

@Composable
private fun GuestHeader(onSignIn: () -> Unit) {
    Surface(
        shape = CardShape,
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, hairline()),
        modifier = Modifier.fillMaxWidth().padding(horizontal = Space.page),
    ) {
        Column(Modifier.padding(Space.xl), horizontalAlignment = Alignment.CenterHorizontally) {
            ProfileAvatar(null, 64.dp)
            Spacer(Modifier.height(Space.md))
            Text("وارد حساب شوید", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(Space.xs))
            Text(
                "نوبت‌ها، صفحه‌هایی که دنبال می‌کنید و نظرهایتان روی هر گوشی همراهتان است.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(Space.lg))
            Button(onClick = onSignIn, shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth().height(48.dp)) {
                Icon(Lucide.LogIn, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(Space.sm))
                Text("ورود یا ثبت‌نام", fontWeight = FontWeight.Bold)
            }
        }
    }
}

/** Three counts in one outlined strip, split by hairlines; each opens its list. */
@Composable
private fun StatsStrip(items: List<Triple<Int?, String, () -> Unit>>) {
    Surface(
        shape = CardShape,
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, hairline()),
        modifier = Modifier.fillMaxWidth().padding(horizontal = Space.page),
    ) {
        Row(Modifier.height(IntrinsicSize.Min)) {
            items.forEachIndexed { index, (value, label, onClick) ->
                if (index > 0) Box(Modifier.width(1.dp).fillMaxHeight().padding(vertical = Space.md).background(hairline()))
                Column(
                    Modifier.weight(1f).clickable(onClick = onClick).padding(vertical = Space.md),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(value?.toPersianDigits() ?: "–", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                }
            }
        }
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
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = Space.page + Space.xs, end = Space.page, bottom = Space.sm),
            )
        }
        Surface(
            shape = CardShape,
            color = MaterialTheme.colorScheme.surface,
            border = BorderStroke(1.dp, hairline()),
            modifier = Modifier.fillMaxWidth().padding(horizontal = Space.page),
        ) {
            Column(content = content)
        }
    }
}

@Composable
private fun RowDivider() {
    Box(
        Modifier
            .fillMaxWidth()
            .padding(start = 52.dp)
            .height(1.dp)
            .background(hairline())
    )
}

@Composable
private fun RowIcon(icon: ImageVector, tint: Color = MaterialTheme.colorScheme.onSurfaceVariant) {
    Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(20.dp))
}

@Composable
private fun MenuRow(
    icon: ImageVector,
    title: String,
    subtitle: String? = null,
    value: String? = null,
    tint: Color = MaterialTheme.colorScheme.onSurfaceVariant,
    titleColor: Color = Color.Unspecified,
    chevron: Boolean = true,
    onClick: () -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    Row(
        Modifier
            .fillMaxWidth()
            .pressScale(interaction, 0.99f)
            .clickable(interactionSource = interaction, indication = androidx.compose.material3.ripple(), onClick = onClick)
            .padding(horizontal = Space.lg, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RowIcon(icon, tint)
        Spacer(Modifier.width(Space.md))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge, color = titleColor)
            if (subtitle != null) {
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        if (value != null) {
            Text(value, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(start = Space.sm))
        }
        if (chevron) {
            Icon(ChevronIcon, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f), modifier = Modifier.size(18.dp))
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
            .padding(horizontal = Space.lg, vertical = Space.sm)
            .fillMaxWidth()
            .height(40.dp)
            .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(10.dp))
            .padding(3.dp)
    ) {
        val segment = maxWidth / options.size
        val x by animateDpAsState(segment * index, spring(Spring.DampingRatioNoBouncy, Spring.StiffnessMediumLow), label = "themePill")
        Surface(
            shape = RoundedCornerShape(8.dp),
            color = MaterialTheme.colorScheme.surface,
            shadowElevation = 1.dp,
            modifier = Modifier.offset(x = x).width(segment).fillMaxHeight(),
        ) {}
        Row(Modifier.fillMaxSize()) {
            options.forEach { (mode, label) ->
                val on = mode == selected
                val color by animateColorAsState(if (on) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant, label = "themeLabel")
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
    var bio by remember { mutableStateOf(state.me?.bio.orEmpty()) }
    // A picked photo waits here until it is cropped (or the crop is cancelled).
    var picked by remember { mutableStateOf<Pair<ByteArray, ImageBitmap>?>(null) }
    val picker = rememberImagePicker { bytes ->
        val bitmap = bytes.toImageBitmapOrNull()
        if (bitmap != null) picked = bytes to bitmap else profile.setPhoto(bytes)
    }
    val close: () -> Unit = { scope.launch { sheet.hide() }.invokeOnCompletion { onDismiss() } }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheet, containerColor = MaterialTheme.colorScheme.surface) {
        Column(
            Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = Space.page).padding(bottom = Space.xl),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text("ویرایش پروفایل", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(Space.lg))
            Box(contentAlignment = Alignment.Center) {
                ProfileAvatar(state.me?.avatar, 96.dp, Modifier.clip(CircleShape).border(1.dp, hairline(), CircleShape).clickable { picker.launch() })
                // Explicit: inside this Column the scoped overload would be picked instead.
                androidx.compose.animation.AnimatedVisibility(visible = state.uploadingPhoto, enter = fadeIn(), exit = fadeOut()) {
                    Box(Modifier.size(96.dp).background(Color.Black.copy(alpha = 0.4f), CircleShape), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = Color.White, strokeWidth = 2.dp, modifier = Modifier.size(26.dp))
                    }
                }
            }
            Row(horizontalArrangement = Arrangement.Center) {
                TextButton(onClick = { picker.launch() }, enabled = !state.uploadingPhoto) {
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
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(Space.sm))
            val used = bio.length
            val full = used >= BIO_MAX_LENGTH
            OutlinedTextField(
                value = bio,
                // Never past the limit: a paste is cut at 150 instead of refused.
                onValueChange = { bio = it.take(BIO_MAX_LENGTH) },
                label = { Text("درباره من") },
                placeholder = { Text("مثلاً: عاشق موهای کوتاه، صبح‌ها وقت آزاد دارم") },
                minLines = 3,
                maxLines = 5,
                shape = RoundedCornerShape(12.dp),
                supportingText = {
                    Row(Modifier.fillMaxWidth()) {
                        Text("فقط خودتان می‌بینید.", modifier = Modifier.weight(1f))
                        Text(
                            "${used.toPersianDigits()} / ${BIO_MAX_LENGTH.toPersianDigits()}",
                            color = if (full) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                            fontWeight = if (full) FontWeight.Bold else FontWeight.Normal,
                        )
                    }
                },
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(Space.sm))
            OutlinedTextField(
                value = state.me?.phoneNumber.orEmpty().toPersianDigits(),
                onValueChange = {},
                enabled = false,
                label = { Text("شماره موبایل") },
                supportingText = { Text("فقط برای ورود است و به هیچ‌کس نشان داده نمی‌شود.") },
                singleLine = true,
                shape = RoundedCornerShape(12.dp),
                textStyle = MaterialTheme.typography.bodyLarge.copy(textDirection = TextDirection.Ltr),
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(Space.lg))
            Button(
                onClick = { profile.save(name, bio) { close() } },
                enabled = !state.savingName && name.trim().length >= 2,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth().height(50.dp),
            ) {
                if (state.savingName) CircularProgressIndicator(strokeWidth = 2.dp, modifier = Modifier.size(20.dp), color = MaterialTheme.colorScheme.onPrimary)
                else Text("ذخیره", fontWeight = FontWeight.Bold)
            }
        }
    }

    picked?.let { (bytes, bitmap) ->
        ImageCropperDialog(
            source = bytes,
            bitmap = bitmap,
            onDismiss = { picked = null },
            onCropped = { cropped ->
                picked = null
                profile.setPhoto(cropped)
            },
        )
    }
}
