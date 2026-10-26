package xyz.sattar.javid.proqueue.feature.businessDetail

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.Call
import androidx.compose.material.icons.rounded.Campaign
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.rounded.CloudOff
import androidx.compose.material.icons.rounded.Directions
import androidx.compose.material.icons.rounded.Flag
import androidx.compose.material.icons.rounded.HealthAndSafety
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.LocationOn
import androidx.compose.material.icons.rounded.NearMe
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.Spa
import androidx.compose.material.icons.rounded.Storefront
import androidx.compose.material.icons.rounded.Verified
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.koin.compose.viewmodel.koinViewModel
import xyz.sattar.javid.proqueue.core.ui.components.ShimmerBox
import xyz.sattar.javid.proqueue.core.ui.components.ToastyHost
import xyz.sattar.javid.proqueue.core.ui.components.UiMessage
import xyz.sattar.javid.proqueue.core.ui.components.showToasty
import xyz.sattar.javid.proqueue.core.utils.openMap
import xyz.sattar.javid.proqueue.core.utils.openPhoneDial
import xyz.sattar.javid.proqueue.core.utils.openUrl
import xyz.sattar.javid.proqueue.core.utils.shareText
import xyz.sattar.javid.proqueue.domain.model.business.Business
import xyz.sattar.javid.proqueue.feature.discovery.BackIcon
import xyz.sattar.javid.proqueue.feature.discovery.Brand
import xyz.sattar.javid.proqueue.feature.discovery.BusinessAvatar
import xyz.sattar.javid.proqueue.feature.discovery.CategoryGroup
import xyz.sattar.javid.proqueue.feature.discovery.CircleIconButton
import xyz.sattar.javid.proqueue.feature.discovery.OWNER_LANDING_URL
import xyz.sattar.javid.proqueue.feature.discovery.Pill
import xyz.sattar.javid.proqueue.feature.discovery.SaveToggle
import xyz.sattar.javid.proqueue.feature.discovery.VerifiedBadge
import xyz.sattar.javid.proqueue.feature.discovery.areaLine
import xyz.sattar.javid.proqueue.feature.discovery.bookingUrl
import xyz.sattar.javid.proqueue.feature.discovery.canBookOnline
import xyz.sattar.javid.proqueue.feature.discovery.formatDistance
import xyz.sattar.javid.proqueue.feature.discovery.toPersianDigits
import xyz.sattar.javid.proqueue.feature.discovery.IRANIAN_WEEK_ORDER
import xyz.sattar.javid.proqueue.feature.discovery.OpenStatusLabel
import xyz.sattar.javid.proqueue.feature.discovery.WEEKDAY_NAMES
import xyz.sattar.javid.proqueue.feature.discovery.openStatus
import xyz.sattar.javid.proqueue.feature.discovery.tehranNow

@Composable
fun ClientBusinessDetailScreen(
    businessId: Long,
    onNavigateBack: () -> Unit,
    viewModel: ClientBusinessDetailViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsState()
    val snackbar = remember { SnackbarHostState() }
    var showReport by remember { mutableStateOf(false) }

    LaunchedEffect(businessId) { viewModel.load(businessId) }
    LaunchedEffect(Unit) {
        viewModel.messages.collect { msg ->
            when (msg) {
                is DetailMessage.Success -> snackbar.showToasty(UiMessage.success(msg.text))
                is DetailMessage.Error -> snackbar.showToasty(UiMessage.error(msg.text))
            }
        }
    }

    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        val business = state.business
        when {
            business != null -> DetailContent(
                business = business,
                state = state,
                onBack = onNavigateBack,
                onToggleSave = viewModel::toggleSaved,
                onRequestBooking = viewModel::requestOnlineBooking,
                onReport = { showReport = true },
            )
            state.isLoading -> DetailShimmer(onBack = onNavigateBack)
            else -> DetailError(state.errorMessage, onRetry = viewModel::reload, onBack = onNavigateBack)
        }
        ToastyHost(hostState = snackbar)
    }

    if (showReport) {
        ReportDialog(
            isSubmitting = state.isReporting,
            onDismiss = { showReport = false },
            onSubmit = { reason, detail -> viewModel.report(reason, detail) { showReport = false } }
        )
    }
}

@Composable
private fun DetailContent(
    business: Business,
    state: ClientBusinessDetailState,
    onBack: () -> Unit,
    onToggleSave: () -> Unit,
    onRequestBooking: () -> Unit,
    onReport: () -> Unit,
) {
    val group = CategoryGroup.of(business.category)
    val heroBrush = if (business.isClaimed) Brand.HeroGradient
    else Brush.linearGradient(listOf(group.tint.copy(alpha = 0.95f), group.tint.copy(alpha = 0.65f)))
    val booking = bookingUrl(business)

    Box(Modifier.fillMaxSize()) {
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
    ) {
        // ── Hero ──
        Box(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(bottomStart = 32.dp, bottomEnd = 32.dp))
                .background(heroBrush)
        ) {
            Column(
                Modifier
                    .windowInsetsPadding(WindowInsets.statusBars)
                    .padding(horizontal = 16.dp)
                    .padding(top = 8.dp, bottom = 56.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CircleIconButton(BackIcon, "بازگشت", onBack)
                    Spacer(Modifier.weight(1f))
                    CircleIconButton(Icons.Rounded.Share, "اشتراک‌گذاری", {
                        val link = booking ?: OWNER_LANDING_URL
                        shareText("${business.title}\n${business.categoryLabel}\n$link")
                    })
                    Spacer(Modifier.width(8.dp))
                    Surface(shape = CircleShape, color = Color.White.copy(alpha = 0.18f), modifier = Modifier.size(40.dp)) {
                        Box(contentAlignment = Alignment.Center) {
                            SaveToggle(state.isSaved, onToggleSave, onDark = true)
                        }
                    }
                }
            }
        }

        // ── Identity card, overlapping the hero ──
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surface,
            shadowElevation = 4.dp,
            modifier = Modifier
                .padding(horizontal = 16.dp)
                .offset(y = (-44).dp)
                .fillMaxWidth()
        ) {
            Column(Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    BusinessAvatar(business, size = 72.dp, corner = 20.dp)
                    Spacer(Modifier.width(14.dp))
                    Column(Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                business.title,
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.weight(1f, fill = false)
                            )
                            if (business.isClaimed) {
                                Spacer(Modifier.width(4.dp))
                                Icon(Icons.Rounded.Verified, contentDescription = "ثبت‌شده در نوبت‌یار", tint = Brand.Violet500, modifier = Modifier.size(20.dp))
                            }
                        }
                        Text(business.categoryLabel, color = group.tint, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                        val area = business.areaLine()
                        if (area.isNotBlank()) {
                            Text(area, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
                        }
                        business.openStatus()?.let {
                            Spacer(Modifier.height(6.dp))
                            OpenStatusLabel(it)
                        }
                    }
                }
                Spacer(Modifier.height(12.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    if (business.canBookOnline) VerifiedBadge()
                    business.distanceKm?.let { Pill(formatDistance(it), Icons.Rounded.NearMe) }
                    if (business.insurances.isNotEmpty()) {
                        Pill(
                            "${business.insurances.size.toPersianDigits()} بیمه",
                            Icons.Rounded.HealthAndSafety,
                            container = Brand.Emerald.copy(alpha = 0.12f),
                            content = Brand.Emerald
                        )
                    }
                }

                Spacer(Modifier.height(16.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    QuickAction(
                        icon = Icons.Rounded.Call,
                        label = "تماس",
                        enabled = business.phone.isNotBlank(),
                        modifier = Modifier.weight(1f),
                        onClick = { openPhoneDial(business.phone) }
                    )
                    val lat = business.latitude
                    val lng = business.longitude
                    QuickAction(
                        icon = Icons.Rounded.Directions,
                        label = "مسیریابی",
                        enabled = lat != null && lng != null,
                        modifier = Modifier.weight(1f),
                        onClick = { if (lat != null && lng != null) openMap(lat, lng, business.title) }
                    )
                    QuickAction(
                        icon = Icons.Rounded.Share,
                        label = "ارسال",
                        enabled = true,
                        modifier = Modifier.weight(1f),
                        onClick = { shareText("${business.title}\n${booking ?: OWNER_LANDING_URL}") }
                    )
                }
            }
        }

        Column(
            Modifier
                .offset(y = (-28).dp)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // ── Booking ──
            when {
                // Bookable: the sticky bottom bar carries the booking action.
                business.canBookOnline && booking != null -> Unit
                business.isClaimed -> InfoCard(
                    icon = Icons.Rounded.Info,
                    title = "نوبت‌دهی آنلاین موقتاً غیرفعال است",
                    body = "برای هماهنگی نوبت با مرکز تماس بگیرید."
                )
                else -> RequestOnlineCard(
                    count = state.bookingRequestsCount,
                    requested = state.hasRequestedBooking,
                    loading = state.isRequestingBooking,
                    onRequest = onRequestBooking
                )
            }

            if (business.noticeEnabled && business.noticeMessage.isNotBlank() &&
                business.noticeMessage != BOOKING_BLOCKED_NOTICE
            ) {
                InfoCard(
                    icon = Icons.Rounded.Campaign,
                    title = "اطلاعیه مرکز",
                    body = business.noticeMessage,
                    accent = Brand.Amber
                )
            }

            // ── Info sections ──
            Section(icon = Icons.Rounded.LocationOn, title = "آدرس و تماس") {
                Text(
                    business.address.ifBlank { "آدرس ثبت نشده" },
                    style = MaterialTheme.typography.bodyMedium
                )
                val phones = (listOf(business.phone) + business.extraPhones).filter { it.isNotBlank() }
                if (phones.isNotEmpty()) {
                    Spacer(Modifier.height(10.dp))
                    phones.forEach { phone ->
                        Surface(
                            onClick = { openPhoneDial(phone) },
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                            modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp)
                        ) {
                            Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Rounded.Call, contentDescription = null, tint = Brand.Violet500, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(10.dp))
                                Text(phone.toPersianDigits(), style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
                            }
                        }
                    }
                }
            }

            if (business.isClaimed) {
                Section(icon = Icons.Rounded.Schedule, title = "ساعات کاری") {
                    WeeklyHours(business)
                }
            }

            if (business.services.isNotEmpty()) {
                Section(icon = Icons.Rounded.Spa, title = "خدمات") {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        business.services.forEach {
                            Pill(it, container = group.tint.copy(alpha = 0.1f), content = group.tint)
                        }
                    }
                }
            }

            if (business.insurances.isNotEmpty()) {
                Section(icon = Icons.Rounded.HealthAndSafety, title = "بیمه‌های طرف قرارداد") {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        business.insurances.forEach {
                            Pill(it.name, container = Brand.Emerald.copy(alpha = 0.12f), content = Brand.Emerald)
                        }
                    }
                    if (!business.isClaimed) {
                        Spacer(Modifier.height(8.dp))
                        Text(
                            "این فهرست از منابع عمومی است؛ پیش از مراجعه پذیرش بیمه را با مرکز چک کنید.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            if (business.bio.isNotBlank()) {
                Section(icon = Icons.Rounded.Info, title = "درباره") {
                    Text(business.bio, style = MaterialTheme.typography.bodyMedium)
                }
            }

            if (!business.isClaimed) {
                OwnerClaimCard()
                Text(
                    "اطلاعات این مرکز از منابع عمومی گردآوری شده و ممکن است به‌روز نباشد.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
            }

            TextButton(
                onClick = onReport,
                modifier = Modifier.align(Alignment.CenterHorizontally)
            ) {
                Icon(Icons.Rounded.Flag, contentDescription = null, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.error)
                Spacer(Modifier.width(6.dp))
                Text("گزارش اطلاعات نادرست", color = MaterialTheme.colorScheme.error)
            }
            // Clears the sticky action bar.
            Spacer(Modifier.height(96.dp))
            Spacer(Modifier.windowInsetsPadding(WindowInsets.navigationBars))
        }
    }
    StickyActionBar(business, booking)
    }
}

/**
 * The one thing a visitor came to do, always under the thumb: book when the
 * business takes online bookings, otherwise call — with directions beside it.
 */
@Composable
private fun androidx.compose.foundation.layout.BoxScope.StickyActionBar(business: Business, booking: String?) {
    val canBook = business.canBookOnline && booking != null
    val canCall = business.phone.isNotBlank()
    if (!canBook && !canCall) return
    val lat = business.latitude
    val lng = business.longitude
    Surface(
        color = MaterialTheme.colorScheme.surface,
        shadowElevation = 16.dp,
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
        modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth()
    ) {
        Row(
            Modifier
                .windowInsetsPadding(WindowInsets.navigationBars)
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                onClick = { if (canBook) openUrl(booking) else openPhoneDial(business.phone) },
                shape = RoundedCornerShape(16.dp),
                color = Color.Transparent,
                modifier = Modifier.weight(1f).height(54.dp)
            ) {
                Row(
                    Modifier
                        .fillMaxSize()
                        .background(if (canBook) Brand.FeaturedGradient else Brand.HeroGradient),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(if (canBook) Icons.Rounded.Bolt else Icons.Rounded.Call, contentDescription = null, tint = Color.White)
                    Spacer(Modifier.width(8.dp))
                    Text(
                        if (canBook) "رزرو آنلاین نوبت" else "تماس با مرکز",
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp
                    )
                }
            }
            if (lat != null && lng != null) {
                Spacer(Modifier.width(10.dp))
                Surface(
                    onClick = { openMap(lat, lng, business.title) },
                    shape = RoundedCornerShape(16.dp),
                    color = Brand.Violet500.copy(alpha = 0.12f),
                    modifier = Modifier.size(54.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(Icons.Rounded.Directions, contentDescription = "مسیریابی", tint = Brand.Violet500)
                    }
                }
            }
        }
    }
}

/** Saturday-first week with today highlighted and days off in red. */
@Composable
private fun WeeklyHours(business: Business) {
    val today = remember { tehranNow().dayOfWeek.ordinal }
    val hours = "${hm(business.workStartHour, business.workStartMinute)} – ${hm(business.workEndHour, business.workEndMinute)}"
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        IRANIAN_WEEK_ORDER.forEach { day ->
            val isToday = day == today
            val closed = day in business.closedWeekdays
            Row(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(if (isToday) Brand.Violet500.copy(alpha = 0.1f) else Color.Transparent)
                    .padding(horizontal = 10.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    WEEKDAY_NAMES[day] + if (isToday) " (امروز)" else "",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = if (isToday) FontWeight.Bold else FontWeight.Normal,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    if (closed) "تعطیل" else hours,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = if (isToday) FontWeight.Bold else FontWeight.Normal,
                    color = if (closed) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface
                )
            }
        }
    }
}

/**
 * Text the backend (business/serializers.py BOOKING_BLOCKED_NOTICE) puts into
 * notice_message when the owner's plan can't take bookings. The booking card
 * above already says that, so it is not repeated as a "notice from the centre".
 */
private const val BOOKING_BLOCKED_NOTICE = "پذیرش نوبت جدید برای این کسب‌وکار موقتاً غیرفعال است."


private fun hm(h: Int, m: Int) =
    "${h.toString().padStart(2, '0')}:${m.toString().padStart(2, '0')}".toPersianDigits()

@Composable
private fun QuickAction(
    icon: ImageVector,
    label: String,
    enabled: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    Surface(
        onClick = onClick,
        enabled = enabled,
        shape = RoundedCornerShape(16.dp),
        color = if (enabled) Brand.Violet500.copy(alpha = 0.1f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        modifier = modifier
    ) {
        Column(
            Modifier.padding(vertical = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            val tint = if (enabled) Brand.Violet500 else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
            Icon(icon, contentDescription = null, tint = tint)
            Spacer(Modifier.height(4.dp))
            Text(label, style = MaterialTheme.typography.labelMedium, color = tint)
        }
    }
}

@Composable
private fun RequestOnlineCard(count: Int, requested: Boolean, loading: Boolean, onRequest: () -> Unit) {
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surface,
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, Brand.Violet500.copy(alpha = 0.3f), RoundedCornerShape(20.dp))
    ) {
        Column(Modifier.padding(16.dp)) {
            Text("این مرکز هنوز نوبت آنلاین ندارد", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(4.dp))
            Text(
                "اگر دوست دارید از اینجا نوبت بگیرید، درخواست بدهید؛ به مرکز اطلاع می‌دهیم که مراجعانش منتظر نوبت‌دهی آنلاین هستند.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(12.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Button(
                    onClick = onRequest,
                    enabled = !requested && !loading,
                    colors = ButtonDefaults.buttonColors(containerColor = Brand.Violet500)
                ) {
                    if (loading) {
                        CircularProgressIndicator(Modifier.size(16.dp), color = Color.White, strokeWidth = 2.dp)
                        Spacer(Modifier.width(8.dp))
                    }
                    Text(if (requested) "درخواست ثبت شد ✓" else "درخواست نوبت آنلاین")
                }
                Spacer(Modifier.width(12.dp))
                if (count > 0) {
                    Text(
                        "${count.toPersianDigits()} نفر درخواست داده‌اند",
                        style = MaterialTheme.typography.labelMedium,
                        color = Brand.Violet500
                    )
                }
            }
        }
    }
}

@Composable
private fun OwnerClaimCard() {
    Surface(
        onClick = { openUrl(OWNER_LANDING_URL) },
        shape = RoundedCornerShape(20.dp),
        color = Color.Transparent,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            Modifier.background(Brand.HeroGradient).padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Rounded.Storefront, contentDescription = null, tint = Color.White, modifier = Modifier.size(32.dp))
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text("صاحب این مرکز هستید؟", color = Color.White, fontWeight = FontWeight.Bold)
                Text(
                    "با اپ «نوبت‌یار کسب‌وکار» مالکیت این صفحه را ثبت کنید و نوبت آنلاین بگیرید.",
                    color = Color.White.copy(alpha = 0.85f),
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }
    }
}

@Composable
private fun InfoCard(icon: ImageVector, title: String, body: String, accent: Color = Brand.Violet500) {
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = accent.copy(alpha = 0.1f),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(Modifier.padding(16.dp)) {
            Icon(icon, contentDescription = null, tint = accent)
            Spacer(Modifier.width(12.dp))
            Column {
                Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                Text(body, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun Section(icon: ImageVector, title: String, content: @Composable () -> Unit) {
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surface,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(icon, contentDescription = null, tint = Brand.Violet500, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(8.dp))
                Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.height(10.dp))
            content()
        }
    }
}

@Composable
private fun DetailShimmer(onBack: () -> Unit) {
    Column {
        Box(
            Modifier
                .fillMaxWidth()
                .height(140.dp)
                .clip(RoundedCornerShape(bottomStart = 32.dp, bottomEnd = 32.dp))
                .background(Brand.HeroGradient)
        ) {
            Box(Modifier.windowInsetsPadding(WindowInsets.statusBars).padding(16.dp)) {
                CircleIconButton(BackIcon, "بازگشت", onBack)
            }
        }
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            ShimmerBox(Modifier.fillMaxWidth().height(150.dp), RoundedCornerShape(24.dp))
            ShimmerBox(Modifier.fillMaxWidth().height(72.dp), RoundedCornerShape(20.dp))
            ShimmerBox(Modifier.fillMaxWidth().height(110.dp), RoundedCornerShape(20.dp))
        }
    }
}

@Composable
private fun DetailError(message: String?, onRetry: () -> Unit, onBack: () -> Unit) {
    Column(Modifier.fillMaxSize()) {
        Box(Modifier.windowInsetsPadding(WindowInsets.statusBars).padding(16.dp)) {
            CircleIconButton(BackIcon, "بازگشت", onBack, onDark = false)
        }
        Column(
            Modifier.fillMaxSize().padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(Icons.Rounded.CloudOff, contentDescription = null, tint = Brand.Violet500, modifier = Modifier.size(56.dp))
            Spacer(Modifier.height(12.dp))
            Text("اطلاعات مرکز دریافت نشد", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            if (!message.isNullOrBlank()) {
                Text(message, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
            }
            Spacer(Modifier.height(16.dp))
            Button(onClick = onRetry, colors = ButtonDefaults.buttonColors(containerColor = Brand.Violet500)) { Text("تلاش دوباره") }
        }
    }
}

@Composable
private fun ReportDialog(
    isSubmitting: Boolean,
    onDismiss: () -> Unit,
    onSubmit: (reason: String, detail: String) -> Unit,
) {
    var reason by remember { mutableStateOf(reportReasons.first().first) }
    var detail by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("گزارش این مرکز") },
        text = {
            Column {
                reportReasons.forEach { (code, label) ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .selectable(selected = reason == code, role = Role.RadioButton, onClick = { reason = code })
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(selected = reason == code, onClick = null)
                        Spacer(Modifier.width(8.dp))
                        Text(label, style = MaterialTheme.typography.bodyMedium)
                    }
                }
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = detail,
                    onValueChange = { if (it.length <= 500) detail = it },
                    label = { Text("توضیح (اختیاری)") },
                    minLines = 2,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onSubmit(reason, detail.trim()) }, enabled = !isSubmitting) {
                Text(if (isSubmitting) "در حال ارسال…" else "ارسال گزارش")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("انصراف") } }
    )
}
