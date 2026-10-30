package xyz.sattar.javid.proqueue.feature.businessDetail

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.material.icons.outlined.Flag
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.LocationOn
import xyz.sattar.javid.proqueue.core.utils.openMap
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import kotlinx.coroutines.launch
import org.koin.compose.viewmodel.koinViewModel
import xyz.sattar.javid.proqueue.data.remoteDataSource.social.model.ReviewDto
import xyz.sattar.javid.proqueue.core.ui.components.ToastyHost
import xyz.sattar.javid.proqueue.core.ui.components.UiMessage
import xyz.sattar.javid.proqueue.core.ui.components.showToasty
import xyz.sattar.javid.proqueue.core.utils.openPhoneDial
import xyz.sattar.javid.proqueue.core.utils.openUrl
import xyz.sattar.javid.proqueue.core.utils.shareText
import xyz.sattar.javid.proqueue.domain.model.business.Business
import xyz.sattar.javid.proqueue.feature.discovery.ChevronIcon
import xyz.sattar.javid.proqueue.feature.discovery.Hairline
import xyz.sattar.javid.proqueue.core.config.OWNER_LANDING_URL
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
    onBook: (Long) -> Unit,
    onOpenMap: (Long) -> Unit = {},
    onSignIn: () -> Unit = {},
    viewModel: ClientBusinessDetailViewModel = koinViewModel(),
    social: BusinessSocialViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsState()
    val socialState by social.state.collectAsState()
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    var showReport by remember { mutableStateOf(false) }
    var showReviewDialog by remember { mutableStateOf(false) }
    var reportingReview by remember { mutableStateOf<Long?>(null) }

    LaunchedEffect(businessId) {
        viewModel.load(businessId)
        social.load(businessId)
    }
    // Signing in happens inside the booking flow; pick the token up on return.
    LifecycleResumeEffect(Unit) {
        social.refreshAccount()
        onPauseOrDispose { }
    }
    LaunchedEffect(Unit) {
        social.messages.collect { msg ->
            when (msg) {
                is DetailMessage.Success -> snackbar.showToasty(UiMessage.success(msg.text))
                is DetailMessage.Error -> snackbar.showToasty(UiMessage.error(msg.text))
            }
        }
    }
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
                social = socialState,
                onWriteReview = {
                    if (socialState.isSignedIn) showReviewDialog = true
                    else scope.launch {
                        snackbar.showToasty(
                            UiMessage.info("برای ثبت نظر باید وارد شده باشید؛ از «بیشتر» یا هنگام رزرو نوبت وارد شوید.")
                        )
                    }
                },
                onMoreReviews = social::loadMoreReviews,
                onReportReview = { reportingReview = it.id },
                onBack = onNavigateBack,
                onToggleSave = viewModel::toggleSaved,
                onRequestBooking = viewModel::requestOnlineBooking,
                onReport = { showReport = true },
                onBook = { onBook(business.id) },
                onOpenMap = { onOpenMap(business.id) },
                onSignIn = onSignIn,
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

    if (showReviewDialog) {
        ReviewDialog(
            existing = socialState.myReview,
            isSaving = socialState.isSavingReview,
            onDismiss = { showReviewDialog = false },
            onSubmit = { rating, text -> social.saveReview(rating, text) { showReviewDialog = false } },
            onDelete = { social.deleteReview { showReviewDialog = false } },
        )
    }
    reportingReview?.let { reviewId ->
        ReviewReportDialog(
            isSubmitting = socialState.isReportingReview,
            onDismiss = { reportingReview = null },
            onSubmit = { reason, detail -> social.reportReview(reviewId, reason, detail) { reportingReview = null } }
        )
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
    social: BusinessSocialState,
    onWriteReview: () -> Unit,
    onMoreReviews: () -> Unit,
    onReportReview: (ReviewDto) -> Unit,
    onBack: () -> Unit,
    onToggleSave: () -> Unit,
    onRequestBooking: () -> Unit,
    onReport: () -> Unit,
    onBook: () -> Unit,
    onOpenMap: () -> Unit = {},
    onSignIn: () -> Unit = {},
) {
    // Only for sharing: booking itself happens in the app, never on the site.
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

            Column(
                Modifier
                    .weight(1f)
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .verticalScroll(scroll),
            ) {
                // Header block on the plain surface; everything below sits in inset cards.
                Column(Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surface)) {
                    Identity(business)
                    ActionRow(business, onShare = share)
                }
                Spacer(Modifier.height(Space.md))
                Column(verticalArrangement = Arrangement.spacedBy(Space.md)) {
                    var card = 0

                    when {
                        business.canBookOnline -> Unit
                        business.isClaimed -> GroupCard(title = null, index = card++) {
                            Notice(Icons.Outlined.Info, "نوبت‌دهی آنلاین فعلاً غیرفعال است. برای هماهنگی با مرکز تماس بگیرید.")
                        }
                        else -> GroupCard(title = null, index = card++) {
                            RequestOnlineSection(
                                count = state.bookingRequestsCount,
                                requested = state.hasRequestedBooking,
                                loading = state.isRequestingBooking,
                                onRequest = onRequestBooking
                            )
                        }
                    }

                    if (business.noticeEnabled && business.noticeMessage.isNotBlank() &&
                        business.noticeMessage != BOOKING_BLOCKED_NOTICE
                    ) {
                        GroupCard(title = null, index = card++) {
                            Notice(Icons.Outlined.Campaign, business.noticeMessage, tint = WarningColor)
                        }
                    }

                    if (social.portfolio.isNotEmpty()) PortfolioSection(social.portfolio, card++)
                    if (social.certificates.isNotEmpty()) CertificatesSection(social.certificates, card++)
                    if (social.summary != null) {
                        ReviewsSection(social, card++, onWrite = onWriteReview, onMore = onMoreReviews, onReport = onReportReview)
                    }

                    // Contact: rows that do what they say, like a native contact card.
                    GroupCard(title = "آدرس و تماس", index = card++, bleed = {
                        val lat = business.latitude
                        val lng = business.longitude
                        if (lat != null && lng != null) {
                            MapPreview(business, lat, lng, onOpen = onOpenMap, modifier = Modifier.padding(horizontal = Space.lg).padding(bottom = Space.xs))
                        }
                        // The owner keeps these for signed-in customers: say so, and
                        // offer the way in, instead of "not registered".
                        val guestLocked = !state.isSignedIn && !business.allowAnonymousView && business.address.isBlank() && business.phone.isBlank()
                        if (guestLocked) {
                            LinkRow(
                                Icons.Outlined.Lock,
                                "آدرس و شمارهٔ تماس این مرکز فقط برای کاربران واردشده نمایش داده می‌شود",
                                subtitle = "ورود یا ثبت‌نام",
                                tint = MaterialTheme.colorScheme.primary,
                                onClick = onSignIn,
                            )
                        }
                        if (!guestLocked) LinkRow(
                            Icons.Outlined.LocationOn,
                            business.address.ifBlank { "آدرس ثبت نشده" },
                            subtitle = if (lat != null && lng != null) "مسیریابی" else null,
                            tint = MaterialTheme.colorScheme.primary,
                            onClick = if (lat != null && lng != null) ({ openMap(lat, lng, business.title) }) else null,
                        )
                        val phones = (listOf(business.phone) + business.extraPhones).filter { it.isNotBlank() }
                        phones.forEach { phone ->
                            Hairline(Modifier.padding(start = 52.dp, end = Space.lg))
                            LinkRow(
                                Icons.Outlined.Call,
                                phone.toPersianDigits(),
                                tint = MaterialTheme.colorScheme.primary,
                                onClick = { openPhoneDial(phone) },
                            )
                        }
                    })

                    if (business.isClaimed) {
                        GroupCard(title = "ساعات کاری", index = card++) { WeeklyHours(business) }
                    }

                    if (business.services.isNotEmpty()) {
                        GroupCard(title = "خدمات", index = card++) { Tags(business.services) }
                    }

                    val payment = if (business.isClaimed) paymentInfo(business) else null
                    if (payment != null) {
                        GroupCard(title = "بیعانه و پرداخت", index = card++) {
                            payment.deposit?.let {
                                Text(it, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
                                if (payment.methods.isNotEmpty()) Spacer(Modifier.height(Space.sm))
                            }
                            if (payment.methods.isNotEmpty()) Tags(payment.methods)
                        }
                    }

                    if (business.insurances.isNotEmpty()) {
                        GroupCard(title = "بیمه‌های طرف قرارداد", index = card++) {
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
                        GroupCard(title = "درباره", index = card++) {
                            Text(business.bio, style = MaterialTheme.typography.bodyMedium)
                        }
                    }

                    // Housekeeping links, grouped at the end like a settings list.
                    GroupCard(title = null, index = card++, bleed = {
                        if (!business.isClaimed) {
                            LinkRow(
                                Icons.Outlined.Storefront,
                                "صاحب این مرکز هستید؟",
                                subtitle = "مالکیت صفحه را در اپ نوبت‌یار کسب‌وکار ثبت کنید",
                                onClick = { openUrl(OWNER_LANDING_URL) },
                            )
                            Hairline(Modifier.padding(start = 52.dp, end = Space.lg))
                        }
                        LinkRow(
                            Icons.Outlined.Flag,
                            "گزارش اطلاعات نادرست",
                            subtitle = if (!business.isClaimed) "این صفحه از منابع عمومی گردآوری شده و ممکن است به‌روز نباشد" else null,
                            onClick = onReport,
                        )
                    })
                }
                // Clears the sticky action bar.
                Spacer(Modifier.height(88.dp + Space.md))
                Spacer(Modifier.windowInsetsPadding(WindowInsets.navigationBars))
            }
        }
        StickyActionBar(business, onBook, signedIn = state.isSignedIn)
    }
}
