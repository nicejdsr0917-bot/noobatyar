package xyz.sattar.javid.proqueue.feature.businessDetail

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Call
import androidx.compose.material.icons.outlined.Campaign
import androidx.compose.material.icons.outlined.CloudOff
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.Storefront
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import org.koin.compose.viewmodel.koinViewModel
import xyz.sattar.javid.proqueue.core.ui.components.ToastyHost
import xyz.sattar.javid.proqueue.core.ui.components.UiMessage
import xyz.sattar.javid.proqueue.core.ui.components.showToasty
import xyz.sattar.javid.proqueue.core.utils.openPhoneDial
import xyz.sattar.javid.proqueue.core.utils.openUrl
import xyz.sattar.javid.proqueue.core.utils.shareText
import xyz.sattar.javid.proqueue.domain.model.business.Business
import xyz.sattar.javid.proqueue.feature.discovery.ChevronIcon
import xyz.sattar.javid.proqueue.feature.discovery.Hairline
import xyz.sattar.javid.proqueue.feature.discovery.OWNER_LANDING_URL
import xyz.sattar.javid.proqueue.feature.discovery.SaveButton
import xyz.sattar.javid.proqueue.feature.discovery.Space
import xyz.sattar.javid.proqueue.feature.discovery.StateMessage
import xyz.sattar.javid.proqueue.feature.discovery.bookingUrl
import xyz.sattar.javid.proqueue.feature.discovery.canBookOnline
import xyz.sattar.javid.proqueue.feature.discovery.toPersianDigits
import xyz.sattar.javid.proqueue.ui.theme.WarningColor



/**
 * Text the backend (business/serializers.py BOOKING_BLOCKED_NOTICE) puts into
 * notice_message when the owner's plan can't take bookings. The booking state
 * already says that, so it is not repeated as a "notice from the centre".
 */
internal const val VERIFIED_ID = "verified"

internal const val BOOKING_BLOCKED_NOTICE = "پذیرش نوبت جدید برای این کسب‌وکار موقتاً غیرفعال است."

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
internal fun DetailContent(
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
