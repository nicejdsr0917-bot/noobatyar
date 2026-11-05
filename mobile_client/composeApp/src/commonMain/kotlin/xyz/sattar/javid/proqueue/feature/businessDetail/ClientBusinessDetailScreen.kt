package xyz.sattar.javid.proqueue.feature.businessDetail

import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.CloudOff
import com.composables.icons.lucide.Flag
import com.composables.icons.lucide.Info
import com.composables.icons.lucide.Lock
import com.composables.icons.lucide.MapPin
import com.composables.icons.lucide.Megaphone
import com.composables.icons.lucide.Phone
import com.composables.icons.lucide.Share2
import com.composables.icons.lucide.Store
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.statusBars
import androidx.compose.ui.layout.onSizeChanged
import xyz.sattar.javid.proqueue.core.ui.components.PullToRefreshBox
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.foundation.layout.Arrangement
import xyz.sattar.javid.proqueue.core.location.rememberDirections
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
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import xyz.sattar.javid.proqueue.ui.theme.SuccessColorDark
import xyz.sattar.javid.proqueue.ui.theme.LocalIsDarkTheme
import xyz.sattar.javid.proqueue.ui.theme.SuccessColor
import xyz.sattar.javid.proqueue.feature.discovery.LocalMyInsuranceSlug
import xyz.sattar.javid.proqueue.domain.model.business.acceptsInsurance
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
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
import xyz.sattar.javid.proqueue.feature.discovery.FirstVisitTag
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
    /** Booking with a day (ISO date) and slot already picked on this page. */
    onBookSlot: (id: Long, date: String, slotAt: Long) -> Unit = { id, _, _ -> onBook(id) },
    /** Stars picked on the "rate your visit" sheet; > 0 opens the review form with them. */
    openReviewWith: Int = 0,
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
    var reviewHandled by rememberSaveable { mutableStateOf(openReviewWith == 0) }
    LaunchedEffect(socialState.isSignedIn) {
        if (!reviewHandled && socialState.isSignedIn) {
            reviewHandled = true
            showReviewDialog = true
        }
    }

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
                            UiMessage.info("برای ثبت نظر، از تب «بیشتر» وارد شوید")
                        )
                    }
                },
                onMoreReviews = social::loadMoreReviews,
                onReportReview = { reportingReview = it.id },
                onBack = onNavigateBack,
                onToggleSave = viewModel::toggleSaved,
                onToggleFollow = { viewModel.toggleFollow(onSignIn) },
                onRequestBooking = viewModel::requestOnlineBooking,
                onReport = { showReport = true },
                onBook = {
                    val slot = state.selectedSlot
                    val day = state.days.getOrNull(state.selectedDay)
                    if (slot != null && day != null) onBookSlot(business.id, day.date.toString(), slot.timestamp)
                    else onBook(business.id)
                },
                onSelectDay = viewModel::selectDay,
                onSelectSlot = viewModel::selectSlot,
                onRefresh = {
                    viewModel.refresh()
                    social.load(businessId)
                },
                onOpenMap = { onOpenMap(business.id) },
                onSignIn = onSignIn,
            )
            state.isLoading -> DetailShimmer(onBack = onNavigateBack)
            else -> Column(Modifier.fillMaxSize()) {
                TopBar(title = null, showDivider = false, onBack = onNavigateBack)
                StateMessage(
                    icon = Lucide.CloudOff,
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
            initialRating = openReviewWith,
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
    onToggleFollow: () -> Unit = {},
    onRequestBooking: () -> Unit,
    onReport: () -> Unit,
    onBook: () -> Unit,
    onOpenMap: () -> Unit = {},
    onSignIn: () -> Unit = {},
    onSelectDay: (Int) -> Unit = {},
    onSelectSlot: (xyz.sattar.javid.proqueue.data.remoteDataSource.booking.model.TimeSlotDto) -> Unit = {},
    onRefresh: () -> Unit = {},
) {
    val directions = rememberDirections()
    // Only for sharing: booking itself happens in the app, never on the site.
    val booking = bookingUrl(business)
    val scroll = rememberScrollState()
    val collapse = remember(scroll) { HeroCollapse(scroll) }
    val density = LocalDensity.current
    val barPx = WindowInsets.statusBars.getTop(density) + with(density) { PinnedBarHeight.toPx() }
    val share = { shareText("${business.title}\n${business.categoryLabel}\n${booking ?: OWNER_LANDING_URL}") }

    Box(Modifier.fillMaxSize()) {
        // The badge floats down just under the pinned bar; the page itself stays put.
        val topInset = with(density) { WindowInsets.statusBars.getTop(density).toDp() }
        PullToRefreshBox(
            isRefreshing = state.isRefreshing,
            onRefresh = onRefresh,
            modifier = Modifier.fillMaxSize(),
            indicatorTopPadding = topInset + PinnedBarHeight + 8.dp,
            floatingIndicator = true,
        ) {
            Column(
                Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .verticalScroll(scroll),
            ) {
                // Brand hero, then a stats card riding its lower edge; everything
                // below sits in inset cards.
                DetailHero(
                    business = business,
                    rating = social.summary,
                    collapse = collapse,
                    // Collapses over its own height, less the pinned bar it tucks under.
                    modifier = Modifier.onSizeChanged { collapse.range = it.height - barPx },
                )
                StatsRow(business, social.summary)
                Column(Modifier.offset(y = (-16).dp), verticalArrangement = Arrangement.spacedBy(Space.md)) {
                    var card = 0

                    FollowRow(state.followersCount, state.isFollowing, state.followBusy, onToggleFollow)

                    if (business.firstVisitDiscount > 0) {
                        FirstVisitTag(business.firstVisitDiscount, Modifier.padding(horizontal = Space.page))
                    }

                    if (state.days.isNotEmpty()) {
                        QuickPickSection(state, card++, onSelectDay, onSelectSlot)
                    }

                    when {
                        business.canBookOnline -> Unit
                        business.isClaimed -> GroupCard(title = null, index = card++) {
                            Notice(Lucide.Info, "نوبت‌دهی آنلاین غیرفعال است؛ با مرکز تماس بگیرید.")
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
                            Notice(Lucide.Megaphone, business.noticeMessage, tint = WarningColor)
                        }
                    }

                    if (social.portfolio.isNotEmpty()) PortfolioSection(social.portfolio, card++)
                    if (social.certificates.isNotEmpty()) CertificatesSection(social.certificates, card++)
                    if (business.socialLinks.isNotEmpty()) SocialLinksSection(business.socialLinks, business.title, card++)
                    if (business.bio.isNotBlank()) {
                        GroupCard(title = "درباره", index = card++) {
                            Text(business.bio, style = MaterialTheme.typography.bodyMedium)
                        }
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
                                Lucide.Lock,
                                "آدرس و تلفن فقط بعد از ورود نمایش داده می‌شود",
                                subtitle = "ورود یا ثبت‌نام",
                                tint = MaterialTheme.colorScheme.primary,
                                onClick = onSignIn,
                            )
                        }
                        // Same as tapping the map above: the full map, with directions there.
                        if (!guestLocked) LinkRow(
                            Lucide.MapPin,
                            business.address.ifBlank { "آدرس ثبت نشده" },
                            subtitle = if (lat != null && lng != null) "نمایش روی نقشه" else null,
                            tint = MaterialTheme.colorScheme.primary,
                            onClick = if (lat != null && lng != null) onOpenMap else null,
                        )
                        val phones = (listOf(business.phone) + business.extraPhones).filter { it.isNotBlank() }
                        phones.forEach { phone ->
                            Hairline(Modifier.padding(start = 52.dp, end = Space.lg))
                            LinkRow(
                                Lucide.Phone,
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
                            val mine = LocalMyInsuranceSlug.current
                            if (mine != null) {
                                val accepts = business.acceptsInsurance(mine)
                                Text(
                                    if (accepts) "بیمه شما را قبول می‌کند" else "بیمه شما در این فهرست نیست؛ قبل از مراجعه هماهنگ کنید.",
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Medium,
                                    color = if (accepts) {
                                        if (LocalIsDarkTheme.current) SuccessColorDark else SuccessColor
                                    } else MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                Spacer(Modifier.height(Space.sm))
                            }
                            Tags(business.insurances.map { it.name })
                            if (!business.isClaimed) {
                                Spacer(Modifier.height(Space.sm))
                                Text(
                                    "از منابع عمومی؛ قبل از مراجعه بیمه را چک کنید.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }

                    if (social.summary != null) {
                        ReviewsSection(social, card++, onWrite = onWriteReview, onMore = onMoreReviews, onReport = onReportReview)
                    }

                    // Housekeeping links, grouped at the end like a settings list.
                    GroupCard(title = null, index = card++, bleed = {
                        if (!business.isClaimed) {
                            LinkRow(
                                Lucide.Store,
                                "صاحب این مرکز هستید؟",
                                subtitle = "مالکیت صفحه را در اپ نوبت‌یار کسب‌وکار ثبت کنید",
                                onClick = { openUrl(OWNER_LANDING_URL) },
                            )
                            Hairline(Modifier.padding(start = 52.dp, end = Space.lg))
                        }
                        LinkRow(
                            Lucide.Flag,
                            "گزارش اطلاعات نادرست",
                            subtitle = if (!business.isClaimed) "اطلاعات از منابع عمومی است و ممکن است قدیمی باشد" else null,
                            tint = MaterialTheme.colorScheme.error,
                            titleColor = MaterialTheme.colorScheme.error,
                            onClick = onReport,
                        )
                    })
                }
                // Clears the sticky action bar.
                Spacer(Modifier.height(88.dp + Space.md))
                Spacer(Modifier.windowInsetsPadding(WindowInsets.navigationBars))
            }
        }
        PinnedDetailBar(
            business = business,
            collapse = collapse,
            isSaved = state.isSaved,
            onBack = onBack,
            onShare = share,
            onToggleSave = onToggleSave,
        )
        StickyActionBar(business, onBook, signedIn = state.isSignedIn, picked = state.selectedSlot?.time)
    }
}
