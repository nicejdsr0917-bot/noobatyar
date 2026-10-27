package xyz.sattar.javid.proqueue.feature.businessDetail

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Call
import androidx.compose.material.icons.outlined.Campaign
import androidx.compose.material.icons.outlined.CloudOff
import androidx.compose.material.icons.outlined.Directions
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.Storefront
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.text.InlineTextContent
import androidx.compose.foundation.text.appendInlineContent
import androidx.compose.ui.text.Placeholder
import androidx.compose.ui.text.PlaceholderVerticalAlign
import androidx.compose.ui.text.buildAnnotatedString
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
import xyz.sattar.javid.proqueue.feature.discovery.BusinessAvatar
import xyz.sattar.javid.proqueue.feature.discovery.ChevronIcon
import xyz.sattar.javid.proqueue.feature.discovery.Hairline
import xyz.sattar.javid.proqueue.feature.discovery.IRANIAN_WEEK_ORDER
import xyz.sattar.javid.proqueue.feature.discovery.OWNER_LANDING_URL
import xyz.sattar.javid.proqueue.feature.discovery.OnlineBadge
import xyz.sattar.javid.proqueue.feature.discovery.OpenStatusText
import xyz.sattar.javid.proqueue.feature.discovery.Radius
import xyz.sattar.javid.proqueue.feature.discovery.SaveButton
import xyz.sattar.javid.proqueue.feature.discovery.Space
import xyz.sattar.javid.proqueue.feature.discovery.StateMessage
import xyz.sattar.javid.proqueue.feature.discovery.VerifiedMark
import xyz.sattar.javid.proqueue.feature.discovery.WEEKDAY_NAMES
import xyz.sattar.javid.proqueue.feature.discovery.areaLine
import xyz.sattar.javid.proqueue.feature.discovery.bookingUrl
import xyz.sattar.javid.proqueue.feature.discovery.canBookOnline
import xyz.sattar.javid.proqueue.feature.discovery.formatClock
import xyz.sattar.javid.proqueue.feature.discovery.formatDistance
import xyz.sattar.javid.proqueue.feature.discovery.openStatus
import xyz.sattar.javid.proqueue.feature.discovery.tehranNow
import xyz.sattar.javid.proqueue.feature.discovery.toPersianDigits
import xyz.sattar.javid.proqueue.ui.theme.WarningColor

/**
 * Text the backend (business/serializers.py BOOKING_BLOCKED_NOTICE) puts into
 * notice_message when the owner's plan can't take bookings. The booking state
 * already says that, so it is not repeated as a "notice from the centre".
 */
private const val VERIFIED_ID = "verified"

private const val BOOKING_BLOCKED_NOTICE = "پذیرش نوبت جدید برای این کسب‌وکار موقتاً غیرفعال است."

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
            else -> Column(Modifier.fillMaxSize()) {
                TopBar(title = null, showDivider = false, onBack = onNavigateBack)
                StateMessage(
                    icon = Icons.Outlined.CloudOff,
                    title = "اطلاعات مرکز دریافت نشد",
                    body = state.errorMessage.orEmpty(),
                    actionLabel = "تلاش دوباره",
                    onAction = viewModel::reload,
                    modifier = Modifier.padding(top = 80.dp)
                )
            }
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
private fun TopBar(
    title: String?,
    showDivider: Boolean,
    onBack: () -> Unit,
    actions: @Composable () -> Unit = {},
) {
    Column(
        Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.background)
            .windowInsetsPadding(WindowInsets.statusBars)
    ) {
        Row(Modifier.height(56.dp).padding(horizontal = Space.xs), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(BackIcon, contentDescription = "بازگشت") }
            // Kept composed and faded rather than added/removed, so the bar's
            // layout never jumps when the title appears on scroll.
            val titleAlpha by animateFloatAsState(if (title != null) 1f else 0f, label = "topBarTitle")
            Text(
                title.orEmpty(),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f).graphicsLayer { alpha = titleAlpha }
            )
            actions()
        }
        if (showDivider) Hairline() else Spacer(Modifier.height(1.dp))
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
    val booking = bookingUrl(business)
    val scroll = rememberScrollState()
    val pastHeader by remember { derivedStateOf { scroll.value > 180 } }
    val share = { shareText("${business.title}\n${business.categoryLabel}\n${booking ?: OWNER_LANDING_URL}") }

    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            TopBar(
                title = if (pastHeader) business.title else null,
                showDivider = scroll.value > 0,
                onBack = onBack,
                actions = {
                    IconButton(onClick = share) { Icon(Icons.Outlined.Share, contentDescription = "اشتراک‌گذاری") }
                    SaveButton(state.isSaved, onToggleSave)
                }
            )

            Column(Modifier.weight(1f).verticalScroll(scroll)) {
                Identity(business)
                ActionRow(business, onShare = share)
                SectionBand()

                when {
                    business.canBookOnline && booking != null -> Unit
                    business.isClaimed -> {
                        Notice(Icons.Outlined.Info, "نوبت‌دهی آنلاین فعلاً غیرفعال است. برای هماهنگی با مرکز تماس بگیرید.")
                        SectionBand()
                    }
                    else -> {
                        RequestOnlineSection(
                            count = state.bookingRequestsCount,
                            requested = state.hasRequestedBooking,
                            loading = state.isRequestingBooking,
                            onRequest = onRequestBooking
                        )
                        SectionBand()
                    }
                }

                if (business.noticeEnabled && business.noticeMessage.isNotBlank() &&
                    business.noticeMessage != BOOKING_BLOCKED_NOTICE
                ) {
                    Notice(Icons.Outlined.Campaign, business.noticeMessage, tint = WarningColor)
                    SectionBand()
                }

                Section("آدرس و تماس") {
                    Text(business.address.ifBlank { "آدرس ثبت نشده" }, style = MaterialTheme.typography.bodyMedium)
                    val phones = (listOf(business.phone) + business.extraPhones).filter { it.isNotBlank() }
                    if (phones.isNotEmpty()) Spacer(Modifier.height(Space.sm))
                    phones.forEach { phone ->
                        Surface(onClick = { openPhoneDial(phone) }, color = Color.Transparent, modifier = Modifier.fillMaxWidth()) {
                            Row(Modifier.height(44.dp), verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Outlined.Call, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                                Spacer(Modifier.width(Space.md))
                                Text(phone.toPersianDigits(), style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                                Icon(ChevronIcon, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(20.dp))
                            }
                        }
                    }
                }

                if (business.isClaimed) {
                    SectionBand()
                    Section("ساعات کاری") { WeeklyHours(business) }
                }

                if (business.services.isNotEmpty()) {
                    SectionBand()
                    Section("خدمات") { Tags(business.services) }
                }

                if (business.insurances.isNotEmpty()) {
                    SectionBand()
                    Section("بیمه‌های طرف قرارداد") {
                        Tags(business.insurances.map { it.name })
                        if (!business.isClaimed) {
                            Spacer(Modifier.height(Space.sm))
                            Text(
                                "از منابع عمومی؛ پیش از مراجعه پذیرش بیمه را با مرکز چک کنید.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                if (business.bio.isNotBlank()) {
                    SectionBand()
                    Section("درباره") { Text(business.bio, style = MaterialTheme.typography.bodyMedium) }
                }

                SectionBand()
                if (!business.isClaimed) {
                    Surface(onClick = { openUrl(OWNER_LANDING_URL) }, color = Color.Transparent, modifier = Modifier.fillMaxWidth()) {
                        Row(Modifier.padding(horizontal = Space.page, vertical = Space.lg), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Outlined.Storefront, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                            Spacer(Modifier.width(Space.lg))
                            Column(Modifier.weight(1f)) {
                                Text("صاحب این مرکز هستید؟", style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
                                Text(
                                    "مالکیت صفحه را در اپ نوبت‌یار کسب‌وکار ثبت کنید.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Icon(ChevronIcon, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    Hairline(Modifier.padding(horizontal = Space.page))
                }
                Column(Modifier.padding(Space.page)) {
                    if (!business.isClaimed) {
                        Text(
                            "اطلاعات این صفحه از منابع عمومی گردآوری شده و ممکن است به‌روز نباشد.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    TextButton(onClick = onReport, contentPadding = PaddingValues(0.dp)) {
                        Text("گزارش اطلاعات نادرست", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                // Clears the sticky action bar.
                Spacer(Modifier.height(88.dp))
                Spacer(Modifier.windowInsetsPadding(WindowInsets.navigationBars))
            }
        }
        StickyActionBar(business, booking)
    }
}

@Composable
private fun Identity(business: Business) {
    val status = remember(business) { business.openStatus() }
    Column(Modifier.padding(horizontal = Space.page, vertical = Space.lg)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            BusinessAvatar(business, 72.dp)
            Spacer(Modifier.width(Space.lg))
            Column(Modifier.weight(1f)) {
                // Verified mark as inline content, so on a two-line name it sits
                // right after the last word instead of floating at the row's edge.
                Text(
                    buildAnnotatedString {
                        append(business.title)
                        if (business.isClaimed) {
                            append(" ")
                            appendInlineContent(VERIFIED_ID, "✓")
                        }
                    },
                    inlineContent = mapOf(
                        VERIFIED_ID to InlineTextContent(
                            Placeholder(20.sp, 20.sp, PlaceholderVerticalAlign.Center)
                        ) { VerifiedMark(18.dp) }
                    ),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                )
                Text(business.categoryLabel, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                val area = business.areaLine()
                if (area.isNotBlank()) {
                    Text(area, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        val distance = business.distanceKm
        if (status != null || distance != null || business.canBookOnline) {
            Spacer(Modifier.height(Space.md))
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Space.sm)) {
                if (business.canBookOnline) OnlineBadge()
                if (status != null) OpenStatusText(status)
                if (distance != null) {
                    Text(
                        (if (status != null) "· " else "") + formatDistance(distance),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
private fun ActionRow(business: Business, onShare: () -> Unit) {
    val lat = business.latitude
    val lng = business.longitude
    Row(
        Modifier.padding(start = Space.page, end = Space.page, bottom = Space.lg),
        horizontalArrangement = Arrangement.spacedBy(Space.sm)
    ) {
        ActionButton(Icons.Outlined.Call, "تماس", business.phone.isNotBlank(), Modifier.weight(1f)) {
            openPhoneDial(business.phone)
        }
        ActionButton(Icons.Outlined.Directions, "مسیریابی", lat != null && lng != null, Modifier.weight(1f)) {
            if (lat != null && lng != null) openMap(lat, lng, business.title)
        }
        ActionButton(Icons.Outlined.Share, "اشتراک", true, Modifier.weight(1f), onShare)
    }
}

@Composable
private fun ActionButton(icon: ImageVector, label: String, enabled: Boolean, modifier: Modifier, onClick: () -> Unit) {
    OutlinedButton(
        onClick = onClick,
        enabled = enabled,
        shape = Radius.md,
        contentPadding = PaddingValues(horizontal = Space.sm),
        modifier = modifier.height(44.dp)
    ) {
        Icon(icon, contentDescription = null, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(6.dp))
        Text(label, style = MaterialTheme.typography.labelLarge, maxLines = 1)
    }
}

/** 8dp neutral band between sections — reads as grouping without boxing every section in a card. */
@Composable
private fun SectionBand() {
    Box(Modifier.fillMaxWidth().height(8.dp).background(MaterialTheme.colorScheme.surfaceVariant))
}

@Composable
private fun Section(title: String, content: @Composable ColumnScope.() -> Unit) {
    Column(Modifier.fillMaxWidth().padding(horizontal = Space.page, vertical = Space.lg)) {
        Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(Space.md))
        content()
    }
}

@Composable
private fun Notice(icon: ImageVector, text: String, tint: Color = MaterialTheme.colorScheme.onSurfaceVariant) {
    Row(Modifier.padding(horizontal = Space.page, vertical = Space.lg), verticalAlignment = Alignment.Top) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(Space.md))
        Text(text, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun Tags(items: List<String>) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(Space.sm), verticalArrangement = Arrangement.spacedBy(Space.sm)) {
        items.forEach {
            Text(
                it,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier
                    .background(MaterialTheme.colorScheme.surfaceVariant, Radius.sm)
                    .padding(horizontal = Space.md, vertical = 6.dp)
            )
        }
    }
}

@Composable
private fun RequestOnlineSection(count: Int, requested: Boolean, loading: Boolean, onRequest: () -> Unit) {
    Column(Modifier.padding(horizontal = Space.page, vertical = Space.lg)) {
        Text("این مرکز هنوز نوبت آنلاین ندارد", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(Space.xs))
        Text(
            "درخواست بدهید تا به مرکز اطلاع دهیم مراجعانش منتظر نوبت‌دهی آنلاین هستند.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(Space.md))
        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedButton(onClick = onRequest, enabled = !requested && !loading, shape = Radius.md) {
                if (loading) {
                    CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                    Spacer(Modifier.width(Space.sm))
                }
                Text(if (requested) "درخواست شما ثبت شد" else "درخواست نوبت آنلاین")
            }
            if (count > 0) {
                Spacer(Modifier.width(Space.md))
                Text(
                    "${count.toPersianDigits()} نفر درخواست داده‌اند",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

/** Saturday-first week; today in bold accent, days off in secondary text. */
@Composable
private fun WeeklyHours(business: Business) {
    val today = remember { tehranNow().dayOfWeek.ordinal }
    val hours = "${formatClock(business.workStartHour, business.workStartMinute)} – " +
        formatClock(business.workEndHour, business.workEndMinute)
    Column {
        IRANIAN_WEEK_ORDER.forEach { day ->
            val isToday = day == today
            val closed = day in business.closedWeekdays
            val color = when {
                isToday -> MaterialTheme.colorScheme.primary
                closed -> MaterialTheme.colorScheme.onSurfaceVariant
                else -> MaterialTheme.colorScheme.onSurface
            }
            Row(Modifier.fillMaxWidth().height(36.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    WEEKDAY_NAMES[day],
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = if (isToday) FontWeight.Bold else FontWeight.Normal,
                    color = color,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    if (closed) "تعطیل" else hours,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = if (isToday) FontWeight.Bold else FontWeight.Normal,
                    color = color
                )
            }
        }
    }
}

/**
 * The one thing a visitor came to do, always under the thumb: book when the
 * business takes online bookings, otherwise call — with directions beside it.
 */
@Composable
private fun androidx.compose.foundation.layout.BoxScope.StickyActionBar(business: Business, booking: String?) {
    val bookTarget = booking?.takeIf { business.canBookOnline }
    val canBook = bookTarget != null
    val canCall = business.phone.isNotBlank()
    if (!canBook && !canCall) return
    val lat = business.latitude
    val lng = business.longitude
    Column(
        Modifier
            .align(Alignment.BottomCenter)
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.background)
    ) {
        Hairline()
        Row(
            Modifier
                .windowInsetsPadding(WindowInsets.navigationBars)
                .padding(horizontal = Space.page, vertical = Space.md),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Button(
                onClick = { if (bookTarget != null) openUrl(bookTarget) else openPhoneDial(business.phone) },
                shape = Radius.md,
                colors = ButtonDefaults.buttonColors(),
                modifier = Modifier.weight(1f).height(48.dp)
            ) {
                Text(if (canBook) "رزرو نوبت" else "تماس با مرکز", fontWeight = FontWeight.Bold)
            }
            if (lat != null && lng != null) {
                Spacer(Modifier.width(Space.sm))
                OutlinedButton(
                    onClick = { openMap(lat, lng, business.title) },
                    shape = Radius.md,
                    contentPadding = PaddingValues(0.dp),
                    modifier = Modifier.size(48.dp)
                ) {
                    Icon(Icons.Outlined.Directions, contentDescription = "مسیریابی")
                }
            }
        }
    }
}

@Composable
private fun DetailShimmer(onBack: () -> Unit) {
    Column {
        TopBar(title = null, showDivider = false, onBack = onBack)
        Row(Modifier.padding(Space.page), verticalAlignment = Alignment.CenterVertically) {
            ShimmerBox(Modifier.size(72.dp), Radius.md)
            Spacer(Modifier.width(Space.lg))
            Column {
                ShimmerBox(Modifier.width(180.dp).height(18.dp), Radius.sm)
                Spacer(Modifier.height(Space.sm))
                ShimmerBox(Modifier.width(120.dp).height(14.dp), Radius.sm)
            }
        }
        Row(Modifier.padding(horizontal = Space.page), horizontalArrangement = Arrangement.spacedBy(Space.sm)) {
            repeat(3) { ShimmerBox(Modifier.weight(1f).height(44.dp), Radius.md) }
        }
        Spacer(Modifier.height(Space.xl))
        Column(Modifier.padding(horizontal = Space.page), verticalArrangement = Arrangement.spacedBy(Space.md)) {
            ShimmerBox(Modifier.fillMaxWidth().height(14.dp), Radius.sm)
            ShimmerBox(Modifier.fillMaxWidth(0.7f).height(14.dp), Radius.sm)
            ShimmerBox(Modifier.fillMaxWidth(0.85f).height(14.dp), Radius.sm)
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
                            .padding(vertical = Space.xs),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(selected = reason == code, onClick = null)
                        Spacer(Modifier.width(Space.sm))
                        Text(label, style = MaterialTheme.typography.bodyMedium)
                    }
                }
                Spacer(Modifier.height(Space.sm))
                OutlinedTextField(
                    value = detail,
                    onValueChange = { if (it.length <= 500) detail = it },
                    label = { Text("توضیح (اختیاری)") },
                    minLines = 2,
                    shape = Radius.md,
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
