package xyz.sattar.javid.proqueue.feature.home

import xyz.sattar.javid.proqueue.core.i18n.Str
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.foundation.text.BasicText
import androidx.compose.ui.text.style.TextOverflow
import xyz.sattar.javid.proqueue.core.i18n.AppLocale
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import kotlinx.coroutines.launch
import xyz.sattar.javid.proqueue.core.ui.components.AdaptiveColumns
import xyz.sattar.javid.proqueue.core.ui.components.AdaptiveGrid
import xyz.sattar.javid.proqueue.core.ui.components.adaptivePagePadding
import xyz.sattar.javid.proqueue.core.ui.components.isWideLayout
import xyz.sattar.javid.proqueue.core.navigation.MainTab
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.CalendarToday
import androidx.compose.material.icons.rounded.Cancel
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.DataUsage
import androidx.compose.material.icons.rounded.Event
import androidx.compose.material.icons.rounded.EventBusy
import androidx.compose.material.icons.rounded.People
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.Sms
import androidx.compose.material.icons.rounded.Stars
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material.icons.rounded.WorkspacePremium
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.flow.Flow
import org.jetbrains.compose.ui.tooling.preview.Preview
import org.koin.compose.viewmodel.koinViewModel
import xyz.sattar.javid.proqueue.core.ui.collectWithLifecycleAware
import xyz.sattar.javid.proqueue.core.ui.components.BottomBarSpacer
import xyz.sattar.javid.proqueue.core.ui.components.HomeButtonShimmer
import xyz.sattar.javid.proqueue.core.ui.components.HomeChartShimmer
import xyz.sattar.javid.proqueue.core.ui.components.HomeDashboardShimmer
import xyz.sattar.javid.proqueue.core.ui.components.HomePlanBannerShimmer
import xyz.sattar.javid.proqueue.core.ui.components.HomeQueueRowShimmer
import xyz.sattar.javid.proqueue.core.ui.components.HomeUsageShimmer
import xyz.sattar.javid.proqueue.core.ui.components.MainTopAppBar
import xyz.sattar.javid.proqueue.core.ui.components.PullToRefreshBox
import xyz.sattar.javid.proqueue.core.ui.components.ToastyType
import xyz.sattar.javid.proqueue.core.ui.components.showToasty
import xyz.sattar.javid.proqueue.core.utils.DateTimeUtils
import xyz.sattar.javid.proqueue.core.utils.toPersianDigits
import xyz.sattar.javid.proqueue.feature.wallet.signedAmount
import xyz.sattar.javid.proqueue.data.remoteDataSource.user.model.EntitlementKeys
import xyz.sattar.javid.proqueue.data.remoteDataSource.user.model.EntitlementsResponseDto
import xyz.sattar.javid.proqueue.data.remoteDataSource.user.model.PlanDto
import xyz.sattar.javid.proqueue.domain.model.business.Business
import xyz.sattar.javid.proqueue.ui.theme.AppTheme

@Composable
fun HomeScreen(
    viewModel: HomeViewModel = koinViewModel<HomeViewModel>(),
    onNavigateToCalendar: () -> Unit,
    onNavigateToShare: () -> Unit = {},
    onNavigateToLogin: () -> Unit,
    onChangeBusiness: () -> Unit = {},
    onNavigateToAddons: () -> Unit = {},
    onNavigateToWallet: () -> Unit = {},
    onNavigateToVisitors: (VisitorsNavArgs) -> Unit = {},
    onNavigateToPayment: (String) -> Unit = {}
) {
    val uiState by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    // Initial data load happens once in HomeViewModel.init (it observes the
    // selected business). We deliberately don't re-trigger it here, so returning
    // to this tab does not fire a new server request.

    HandleEvents(
        events = viewModel.events,
        snackbarHostState = snackbarHostState,
        onNavigateToLogin = onNavigateToLogin,
        onOpenPaymentUrl = onNavigateToPayment
    )

    HomeScreenContent(
        uiState = uiState,
        snackbarHostState = snackbarHostState,
        onIntent = viewModel::sendIntent,
        onNavigateToCalendar = onNavigateToCalendar,
        onNavigateToShare = onNavigateToShare,
        onNavigateToLogin = onNavigateToLogin,
        onChangeBusiness = onChangeBusiness,
        onNavigateToAddons = onNavigateToAddons,
        onNavigateToWallet = onNavigateToWallet,
        onNavigateToVisitors = onNavigateToVisitors,
        bannerSlot = { xyz.sattar.javid.proqueue.feature.banner.HomeBannerCarousel(businessId = uiState.business?.id) }
    )
}

/**
 * Scroll distance, in pixels, over which [HomeHero] folds from expanded to
 * compact. Roughly the height of its action row, so the fold finishes just as
 * that row would have scrolled out of view.
 */
private const val COLLAPSE_DISTANCE_PX = 130f

/**
 * Arguments for jumping into the visitors/appointments tab pre-filtered —
 * used when tapping a Home stat card, the queue row, or the month card.
 */
/** Args for opening the نوبت‌ها screen on today, from a card that counts today. */
private fun todayArgs(status: String? = null, tab: Int): VisitorsNavArgs {
    val start = DateTimeUtils.startOfTodayMillis()
    return VisitorsNavArgs(
        status = status,
        tab = tab,
        dateFrom = start,
        dateTo = start + 24L * 60 * 60 * 1000 - 1
    )
}

data class VisitorsNavArgs(
    val status: String? = null,
    val tab: Int? = null,
    val dateFrom: Long? = null,
    val dateTo: Long? = null
)

@Composable
fun HomeScreenContent(
    modifier: Modifier = Modifier,
    uiState: HomeState,
    snackbarHostState: SnackbarHostState,
    onIntent: (HomeIntent) -> Unit,
    onNavigateToCalendar: () -> Unit,
    onNavigateToShare: () -> Unit = {},
    onNavigateToLogin: () -> Unit,
    onChangeBusiness: () -> Unit = {},
    onNavigateToAddons: () -> Unit = {},
    onNavigateToWallet: () -> Unit = {},
    onNavigateToVisitors: (VisitorsNavArgs) -> Unit = {},
    /** Rotating banners, just under the header; injected so previews need no Koin. */
    bannerSlot: @Composable () -> Unit = {}
) {
    var visible by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        visible = true
    }

    val listState = rememberLazyListState()
    // 0 = hero fully expanded, 1 = folded to its compact row. derivedStateOf so
    // the fraction is read during layout without recomposing the whole screen
    // on every scroll pixel; once past the first item the hero is simply
    // pinned collapsed rather than tracking an offset it can no longer see.
    val collapseFraction by remember {
        derivedStateOf {
            if (listState.firstVisibleItemIndex > 0) 1f
            else (listState.firstVisibleItemScrollOffset / COLLAPSE_DISTANCE_PX).coerceIn(0f, 1f)
        }
    }

    LaunchedEffect(uiState.message) {
        uiState.message?.let {
            snackbarHostState.showToasty(it)
            onIntent(HomeIntent.ClearMessage)
        }
    }

    Scaffold(
        snackbarHost = {
            xyz.sattar.javid.proqueue.core.ui.components.ToastyHost(hostState = snackbarHostState)
        },
        contentWindowInsets = WindowInsets(0),
        topBar = {
            MainTopAppBar(
                onNavigateToLogin = onNavigateToLogin,
                onChangeBusiness = onChangeBusiness,
                actions = {
                    Box(
                        modifier = Modifier
                            .padding(end = 8.dp)
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primaryContainer)
                            .clickable { onNavigateToCalendar() },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Event,
                            tint = MaterialTheme.colorScheme.onPrimaryContainer,
                            contentDescription = "Calendar",
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            )
        }
    ) { paddingValues ->
        PullToRefreshBox(
            isRefreshing = uiState.isLoading,
            onRefresh = { onIntent(HomeIntent.LoadData) },
            refreshTab = MainTab.Home,
            // Keeps the badge clear of the glass top bar, which the content
            // deliberately scrolls underneath.
            indicatorTopPadding = paddingValues.calculateTopPadding(),
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
        ) {
        Column(
            modifier = modifier
                .fillMaxSize()
                .padding(top = paddingValues.calculateTopPadding())
        ) {
            // Pinned, collapsing head. Holds the two actions an owner reaches
            // for most (calendar, booking link), which used to be a tab away
            // and the fifth section down respectively.
            HomeHero(
                collapseFraction = collapseFraction,
                todayAppointmentsCount = uiState.stats.totalAppointments.takeIf { uiState.statsLoaded },
                business = uiState.business,
                onOpenCalendar = onNavigateToCalendar,
                onOpenShare = onNavigateToShare
            )

            // Each block is written once; only the arrangement differs.
            // Phone: the original single column, in priority order. Wide:
            // two rows of paired blocks. Today's activity sits beside its
            // trend, and what's been used sits beside what can be bought.
            val queueBlock: @Composable () -> Unit = {
                if (!uiState.statsLoaded) {
                    HomeQueueRowShimmer()
                } else {
                    QueueStatRow(
                        count = uiState.queue.size,
                        // Today's queue, so the list opens on the same day
                        // the count here was taken from.
                        onClick = { onNavigateToVisitors(todayArgs(tab = 1)) }
                    )
                }
            }
            val statsBlock: @Composable () -> Unit = {
                if (!uiState.statsLoaded) {
                    HomeDashboardShimmer()
                } else {
                    DashboardStatsGrid(
                        stats = uiState.stats,
                        // tab = 0 (مراجعین/history) explicitly — LastVisitorsState
                        // defaults selectedTab to 1 (صف/queue), where a status
                        // filter like "cancelled" has nothing to show.
                        // Scoped to today as well: these are today's
                        // numbers, and the list used to open on the next
                        // seven days, where the counts didn't match.
                        onStatClick = { status ->
                            onNavigateToVisitors(todayArgs(status = status, tab = 0))
                        }
                    )
                }
            }
            val trendBlock: @Composable () -> Unit = { Column {
                if (!uiState.chartLoaded) {
                    HomeChartShimmer()
                } else {
                    HomeTrendCard(
                        weeklyCounts = uiState.dailyCounts.map { it.count },
                        overview = uiState.monthOverview,
                        onRangeClick = { start, endExclusive ->
                            onNavigateToVisitors(
                                VisitorsNavArgs(
                                    dateFrom = start,
                                    // endExclusive − 1ms so the range ends
                                    // inside the last day shown rather than
                                    // the day after it.
                                    dateTo = endExclusive - 1
                                )
                            )
                        }
                    )
                }
                // Inside the same block so the wide layout's column keeps it
                // next to the month card it counts from.
                uiState.sourceSummary?.takeUnless { it.isEmpty }?.let { summary ->
                    Spacer(Modifier.height(14.dp))
                    SourceSummaryCard(summary)
                }
            } }
            val usageBlock: @Composable () -> Unit = {
                if (!uiState.entitlementsLoaded) {
                    HomeUsageShimmer()
                } else if (uiState.entitlements != null) {
                    UsageMeterSection(
                        entitlements = uiState.entitlements,
                        onNavigateToAddons = onNavigateToAddons,
                        onNavigateToWallet = onNavigateToWallet
                    )
                }
            }
            val plansBlock: @Composable () -> Unit = {
                if (!uiState.plansLoaded) {
                    HomePlanBannerShimmer()
                } else if (uiState.plans.isNotEmpty()) {
                    PlanBannerSection(
                        plans = uiState.plans,
                        onPlanClick = { plan ->
                            onIntent(HomeIntent.PurchasePlan(plan.id))
                        }
                    )
                }
            }
            val wide = isWideLayout()

            LazyColumn(
                state = listState,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = adaptivePagePadding()),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                item(key = "banners") { bannerSlot() }
                if (wide) {
                    item {
                        HomeReveal(visible, delayMillis = 120) {
                            AdaptiveColumns(
                                startWeight = 1f,
                                endWeight = 1.25f,
                                spacing = 14.dp,
                                start = { queueBlock(); statsBlock() },
                                end = { trendBlock(); usageBlock() }
                            )
                        }
                    }
                    // Full width: plan cards need room for their two feature
                    // columns, which a half-width column cut off mid-word.
                    item {
                        HomeReveal(visible, delayMillis = 240) { plansBlock() }
                    }
                } else {
                    // ۱. صف امروز — تنها بخش «زنده»‌ی صفحه، پس اول می‌آید.
                    item { HomeReveal(visible, delayMillis = 120) { queueBlock() } }
                    // ۲. آمار امروز (۲×۲)
                    item { HomeReveal(visible, delayMillis = 180) { statsBlock() } }
                    // ۳. روند نوبت‌ها — هفتگی یا ماهانه.
                    item { HomeReveal(visible, delayMillis = 240) { trendBlock() } }
                    // ۴. مصرف‌سنج ماهانه
                    item { HomeReveal(visible, delayMillis = 300) { usageBlock() } }
                    // ۵. بنرهای پلن اشتراک
                    item { HomeReveal(visible, delayMillis = 360) { plansBlock() } }
                }

                item { BottomBarSpacer() }
            }
        }
        }
    }
}

/** The staggered slide-and-fade every home block enters with. */
@Composable
private fun HomeReveal(visible: Boolean, delayMillis: Int, content: @Composable () -> Unit) {
    AnimatedVisibility(
        visible = visible,
        enter = slideInVertically(initialOffsetY = { 50 }) + fadeIn(
            animationSpec = tween(500, delayMillis = delayMillis)
        )
    ) {
        content()
    }
}

@Composable
fun PlanBannerSection(
    plans: List<PlanDto>,
    onPlanClick: (PlanDto) -> Unit
) {
    if (isWideLayout()) {
        PlanCarousel(plans = plans, onPlanClick = onPlanClick)
        return
    }

    val pagerState = rememberPagerState(pageCount = { plans.size })

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp)
    ) {
        // contentPadding عمداً بیشتر از 4dp است: هر صفحه کمی باریک‌تر از عرض
        // صفحه می‌شود تا لبه‌ی بنر بعدی/قبلی دیده شود و کاربر بفهمد می‌تواند
        // آن‌ها را دستی swipe کند (دیگر چرخش خودکار نداریم).
        //
        // ارتفاع همه‌ی صفحات باید یکسان باشد، حتی اگر محتوای پلن‌ها متفاوت
        // باشد. قبلاً این کار با SubcomposeLayout انجام می‌شد، اما آن رویکرد
        // HorizontalPager را داخل یک subcomposition جدا قرار می‌داد و
        // gesture drag اصلاً به Pager نمی‌رسید — یعنی swipe کاملاً از کار
        // می‌افتاد (تأیید شده روی امولاتور واقعی). به‌جایش با
        // onSizeChanged روی هر صفحه، بلندترین ارتفاع دیده‌شده را ردیابی
        // می‌کنیم و به‌عنوان ارتفاع ثابت Pager می‌دهیم — Pager همچنان یک
        // composable معمولی می‌ماند و drag درست کار می‌کند.
        val pagerContentPadding = 28.dp
        val density = LocalDensity.current
        var maxCardHeight by remember(plans) { mutableStateOf(0.dp) }

        HorizontalPager(
            state = pagerState,
            modifier = Modifier
                .fillMaxWidth()
                .then(if (maxCardHeight > 0.dp) Modifier.height(maxCardHeight) else Modifier),
            contentPadding = PaddingValues(horizontal = pagerContentPadding),
            pageSpacing = 10.dp
        ) { page ->
            val plan = plans[page]
            PlanBannerItem(
                plan = plan,
                onClick = { onPlanClick(plan) },
                modifier = Modifier
                    .let { if (maxCardHeight > 0.dp) it.height(maxCardHeight) else it }
                    .onSizeChanged { size ->
                        val heightDp = with(density) { size.height.toDp() }
                        if (heightDp > maxCardHeight) maxCardHeight = heightDp
                    }
            )
        }

        if (plans.size > 1) {
            Text(
                text = Str.swipe_to_see_other_plans,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )
        }

        Spacer(modifier = Modifier.height(12.dp))

        // نشانگرهای صفحات (Dots)
        Row(
            Modifier
                .fillMaxWidth()
                .height(8.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            repeat(plans.size) { iteration ->
                val color = if (pagerState.currentPage == iteration) 
                    MaterialTheme.colorScheme.primary 
                else 
                    MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)
                
                val width by animateDpAsState(
                    targetValue = if (pagerState.currentPage == iteration) 24.dp else 8.dp
                )

                Box(
                    modifier = Modifier
                        .padding(horizontal = 4.dp)
                        .clip(CircleShape)
                        .background(color)
                        .width(width)
                        .height(8.dp)
                )
            }
        }
    }
}

/**
 * Desktop plans: one horizontal row of equal cards. A fixed card width keeps
 * each card's two feature columns readable, and every card takes the tallest
 * one's height so the row reads as a set. A mouse can't swipe, so the row
 * moves with the arrow buttons or by dragging.
 */
@Composable
private fun PlanCarousel(
    plans: List<PlanDto>,
    onPlanClick: (PlanDto) -> Unit
) {
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    var maxCardHeight by remember(plans) { mutableStateOf(0.dp) }

    Column(Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = Str.subscription_plans,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f)
            )
            // Backward first: it lands on the right under RTL, and the
            // auto-mirrored "Left" arrow is drawn pointing right there.
            FilledTonalIconButton(
                onClick = {
                    scope.launch {
                        listState.animateScrollToItem((listState.firstVisibleItemIndex - 1).coerceAtLeast(0))
                    }
                },
                enabled = listState.canScrollBackward
            ) {
                Icon(Icons.AutoMirrored.Rounded.KeyboardArrowLeft, contentDescription = Str.previous_plan)
            }
            Spacer(Modifier.width(6.dp))
            FilledTonalIconButton(
                onClick = {
                    scope.launch {
                        listState.animateScrollToItem(
                            (listState.firstVisibleItemIndex + 1).coerceAtMost(plans.lastIndex)
                        )
                    }
                },
                enabled = listState.canScrollForward
            ) {
                Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, contentDescription = Str.next_plan)
            }
        }
        Spacer(Modifier.height(10.dp))
        LazyRow(
            state = listState,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            modifier = Modifier
                .fillMaxWidth()
                // Mouse drag. Pointer deltas are screen-space, while the list
                // scrolls in layout direction, so RTL flips the sign.
                .draggable(
                    orientation = Orientation.Horizontal,
                    state = rememberDraggableState { delta ->
                        scope.launch { listState.scrollBy(if (rtl) delta else -delta) }
                    }
                )
        ) {
            itemsIndexed(plans, key = { _, plan -> plan.id }) { _, plan ->
                PlanBannerItem(
                    plan = plan,
                    onClick = { onPlanClick(plan) },
                    modifier = Modifier
                        .width(360.dp)
                        .then(if (maxCardHeight > 0.dp) Modifier.height(maxCardHeight) else Modifier)
                        .onSizeChanged { size ->
                            val h = with(density) { size.height.toDp() }
                            if (h > maxCardHeight) maxCardHeight = h
                        }
                )
            }
        }
    }
}

@Composable
fun PlanBannerItem(
    plan: PlanDto,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    // Matches the server-side (Persian) plan names, so these stay literal.
    val (gradientColors, badgeLabel) = when {
        plan.name.contains("پرو پلاس") -> Pair( // i18n-keep: server plan name
            listOf(Color(0xFF4A148C), Color(0xFF6A1B9A), Color(0xFFE65100)),
            Str.pro_plus_2
        )
        plan.name.contains("پرو") -> Pair( // i18n-keep: server plan name
            listOf(Color(0xFF1A237E), Color(0xFF283593), Color(0xFF7C4DFF)),
            Str.pro_2
        )
        plan.name.contains("اکو") -> Pair( // i18n-keep: server plan name
            listOf(Color(0xFF1B5E20), Color(0xFF2E7D32), Color(0xFF43A047)),
            Str.eco_2
        )
        plan.name.contains("پایه") -> Pair( // i18n-keep: server plan name
            listOf(Color(0xFF0D47A1), Color(0xFF1565C0), Color(0xFF1976D2)),
            Str.basic_2
        )
        else -> Pair(
            listOf(Color(0xFF37474F), Color(0xFF455A64), Color(0xFF607D8B)),
            Str.trial
        )
    }

    val gradient = Brush.linearGradient(colors = gradientColors)

    Card(
        onClick = onClick,
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 6.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(gradient)
        ) {
            // آیکون تزئینی پس‌زمینه
            Icon(
                imageVector = if (plan.isVip) Icons.Rounded.WorkspacePremium else Icons.Rounded.Stars,
                contentDescription = null,
                modifier = Modifier
                    .size(130.dp)
                    .align(Alignment.BottomEnd)
                    .offset(x = 20.dp, y = 20.dp),
                tint = Color.White.copy(alpha = 0.08f)
            )

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 18.dp)
            ) {
                // سطر اول: badge + قیمت
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Badge نام پلن
                    Surface(
                        color = Color.White.copy(alpha = 0.18f),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text(
                            text = badgeLabel,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 5.dp),
                            style = MaterialTheme.typography.labelLarge,
                            color = Color.White,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    // قیمت
                    Column(horizontalAlignment = Alignment.End) {
                        if (plan.price == 0L) {
                            Surface(
                                color = Color.White.copy(alpha = 0.22f),
                                shape = RoundedCornerShape(10.dp)
                            ) {
                                Text(
                                    text = Str.free,
                                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
                                    style = MaterialTheme.typography.titleMedium,
                                    color = Color.White,
                                    fontWeight = FontWeight.ExtraBold
                                )
                            }
                        } else {
                            Text(
                                text = plan.priceDisplay,
                                style = MaterialTheme.typography.titleLarge,
                                color = Color.White,
                                fontWeight = FontWeight.Black
                            )
                        }
                    }
                }

                Spacer(Modifier.height(12.dp))

                // سطر دوم: مدت اشتراک
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Rounded.Timer,
                        null,
                        tint = Color.White.copy(alpha = 0.75f),
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(Modifier.width(5.dp))
                    Text(
                        text = plan.durationDisplay,
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.White.copy(alpha = 0.85f)
                    )
                }

                // سطر سوم: تمام آیتم‌های توضیح
                if (plan.description.isNotEmpty()) {
                    Spacer(Modifier.height(10.dp))

                    // خط جداکننده
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(1.dp)
                            .background(Color.White.copy(alpha = 0.15f))
                    )

                    Spacer(Modifier.height(10.dp))

                    // نمایش همه آیتم‌ها در دو ستون
                    val half = (plan.description.size + 1) / 2
                    val col1 = plan.description.take(half)
                    val col2 = plan.description.drop(half)

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        // ستون اول
                        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                            col1.forEach { desc ->
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        Icons.Rounded.Check,
                                        null,
                                        tint = Color.White.copy(alpha = 0.8f),
                                        modifier = Modifier.size(13.dp)
                                    )
                                    Spacer(Modifier.width(5.dp))
                                    Text(
                                        text = desc,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = Color.White.copy(alpha = 0.85f),
                                        maxLines = 1,
                                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                                    )
                                }
                            }
                        }
                        // ستون دوم
                        if (col2.isNotEmpty()) {
                            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                                col2.forEach { desc ->
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(
                                            Icons.Rounded.Check,
                                            null,
                                            tint = Color.White.copy(alpha = 0.8f),
                                            modifier = Modifier.size(13.dp)
                                        )
                                        Spacer(Modifier.width(5.dp))
                                        Text(
                                            text = desc,
                                            style = MaterialTheme.typography.labelSmall,
                                            color = Color.White.copy(alpha = 0.85f),
                                            maxLines = 1,
                                            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                // دکمه خرید (فقط وقتی قیمت دارد)
                if (plan.price > 0L) {
                    Spacer(Modifier.height(14.dp))
                    Surface(
                        color = Color.White.copy(alpha = 0.2f),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = Str.buy_subscription,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 10.dp),
                            style = MaterialTheme.typography.labelLarge,
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                        )
                    }
                }
            }
        }
    }
}

/**
 * The 2×2 stat grid on its own.
 *
 * Split out from the old DashboardStatsSection, which also owned the queue
 * row. Home now shows the queue *above* the grid — it is the only live,
 * act-on-it-now number on the screen — and one composable cannot be in two
 * places in the list.
 */
@Composable
fun DashboardStatsGrid(
    stats: DashboardStats,
    onStatClick: (status: String?) -> Unit = {}
) {
    val isDark = !MaterialTheme.colorScheme.surface.let { color ->
        (color.red * 0.299 + color.green * 0.587 + color.blue * 0.114) > 0.5
    }

    Column(
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // ۴ مستطیل آمار (۲×۲) — «نوبت‌های امروز» حذف شد چون تعدادش کنار هدر
        // تاریخ نمایش داده می‌شود.
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            StatCard(
                modifier = Modifier.weight(1f),
                title = Str.complete_action,
                value = stats.completedAppointments.toString(),
                containerColor = if (isDark) Color(0xFF1B5E20).copy(alpha = 0.4f) else Color(0xFFE8F5E9),
                contentColor = if (isDark) Color(0xFFA5D6A7) else Color(0xFF2E7D32),
                icon = Icons.Rounded.CheckCircle,
                onClick = { onStatClick("COMPLETED") }
            )
            StatCard(
                modifier = Modifier.weight(1f),
                title = Str.total_clients,
                value = stats.totalVisitors.toString(),
                containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = if (isDark) 0.4f else 0.7f),
                contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                icon = Icons.Rounded.People,
                onClick = { onStatClick(null) }
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            StatCard(
                modifier = Modifier.weight(1f),
                title = Str.status_no_show,
                value = stats.noShowAppointments.toString(),
                containerColor = if (isDark) Color(0xFFB71C1C).copy(alpha = 0.4f) else Color(0xFFFFEBEE),
                contentColor = if (isDark) Color(0xFFEF9A9A) else Color(0xFFC62828),
                icon = Icons.Rounded.Cancel,
                onClick = { onStatClick("NO_SHOW") }
            )
            StatCard(
                modifier = Modifier.weight(1f),
                title = Str.status_cancelled,
                value = stats.cancelledAppointments.toString(),
                containerColor = if (isDark) Color(0xFF4A148C).copy(alpha = 0.4f) else Color(0xFFF3E5F5),
                contentColor = if (isDark) Color(0xFFCE93D8) else Color(0xFF6A1B9A),
                icon = Icons.Rounded.EventBusy,
                onClick = { onStatClick("CANCELLED") }
            )
        }
    }
}

@Composable
fun QueueStatRow(count: Int, onClick: () -> Unit = {}) {
    val isDark = !MaterialTheme.colorScheme.surface.let { color ->
        (color.red * 0.299 + color.green * 0.587 + color.blue * 0.114) > 0.5
    }
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isDark) Color(0xFF01579B).copy(alpha = 0.35f) else Color(0xFFE1F5FE)
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 18.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Rounded.Timer,
                    contentDescription = null,
                    tint = if (isDark) Color(0xFF81D4FA) else Color(0xFF0277BD),
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    text = Str.in_queue,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = if (isDark) Color(0xFF81D4FA) else Color(0xFF0277BD)
                )
            }
            Text(
                text = count.toString(),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Black,
                color = if (isDark) Color(0xFF81D4FA) else Color(0xFF0277BD)
            )
        }
    }
}

@Composable
fun StatCard(
    modifier: Modifier = Modifier,
    title: String,
    value: String,
    containerColor: Color,
    contentColor: Color,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    onClick: () -> Unit = {}
) {
    Card(
        modifier = modifier.clickable(onClick = onClick),
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = containerColor),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Box(modifier = Modifier.fillMaxWidth().height(if (AppLocale.isEnglish) 108.dp else 100.dp)) {
            // Large background icon
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier
                    .size(70.dp)
                    .align(Alignment.BottomEnd)
                    .offset(x = 15.dp, y = 15.dp),
                tint = contentColor.copy(alpha = 0.07f)
            )

            Column(
                modifier = Modifier
                    .padding(16.dp)
                    .fillMaxSize(),
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                val titleIcon = @Composable {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = contentColor.copy(alpha = 0.7f)
                    )
                }
                val titleText = @Composable {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.labelMedium,
                        color = contentColor.copy(alpha = 0.8f),
                        fontWeight = FontWeight.Bold,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                // English labels ("Completed", "Total clients") are longer than
                // the Persian ones this tile was sized for; beside the icon they
                // broke mid-word on narrow tiles, so in English the label gets
                // its own line under the icon.
                if (AppLocale.isEnglish) {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        titleIcon()
                        // One line, shrinking to fit rather than breaking a word.
                        BasicText(
                            text = title,
                            style = MaterialTheme.typography.labelMedium.copy(
                                color = contentColor.copy(alpha = 0.8f),
                                fontWeight = FontWeight.Bold
                            ),
                            maxLines = 1,
                            autoSize = TextAutoSize.StepBased(minFontSize = 8.sp, maxFontSize = 12.sp)
                        )
                    }
                } else {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        titleIcon()
                        titleText()
                    }
                }
                
                Text(
                    text = value,
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Black,
                    color = contentColor
                )
            }
        }
    }
}

@Composable
fun UsageMeterSection(
    entitlements: EntitlementsResponseDto,
    onNavigateToAddons: () -> Unit = {},
    onNavigateToWallet: () -> Unit = {}
) {
    val appt = entitlements.usage.appointments
    val sms = entitlements.usage.sms
    // SMS "used this month" = quota - remaining (guard for unlimited).
    val smsUsed = if (sms.quota == EntitlementKeys.UNLIMITED) 0 else (sms.quota - sms.monthlyRemaining).coerceAtLeast(0)

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
        ),
        border = CardDefaults.outlinedCardBorder().copy(
            brush = androidx.compose.ui.graphics.SolidColor(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Rounded.DataUsage,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = Str.this_months_usage,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            UsageRow(
                label = Str.appointments_tab,
                icon = Icons.Rounded.Event,
                used = appt.used,
                quota = appt.quota,
                trailingNote = appointmentWalletNote(appt.wallet, appt.quota),
                onNoteClick = onNavigateToWallet
            )

            Spacer(modifier = Modifier.height(14.dp))

            UsageRow(
                label = Str.sms,
                icon = Icons.Rounded.Sms,
                used = smsUsed,
                quota = sms.quota,
                trailingNote = smsWalletNote(sms.wallet),
                onNoteClick = onNavigateToWallet
            )

            if (entitlements.usage.tomanWallet > 0) {
                Spacer(modifier = Modifier.height(10.dp))
                Text(
                    text = Str.wallet_toman_note(
                        xyz.sattar.javid.proqueue.core.billing.formatToman(entitlements.usage.tomanWallet)
                    ).toPersianDigits(),
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .clickable(onClick = onNavigateToWallet)
                        .padding(vertical = 2.dp)
                )
            }

            Spacer(modifier = Modifier.height(14.dp))

            TextButton(
                onClick = onNavigateToAddons,
                modifier = Modifier.fillMaxWidth(),
                contentPadding = PaddingValues(vertical = 6.dp)
            ) {
                Icon(Icons.Rounded.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text(Str.buy_add_on_pack, style = MaterialTheme.typography.labelMedium)
            }
        }
    }
}

@Composable
fun UsageRow(
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    used: Int,
    quota: Int,
    trailingNote: String? = null,
    onNoteClick: () -> Unit = {}
) {
    val unlimited = quota == EntitlementKeys.UNLIMITED
    val fraction = if (unlimited || quota <= 0) 0f else (used.toFloat() / quota.toFloat()).coerceIn(0f, 1f)
    val nearLimit = !unlimited && fraction >= 0.85f
    val barColor = if (nearLimit) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary

    Column {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = label,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Text(
                text = if (unlimited) "$used / ∞" else "$used / $quota",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = if (nearLimit) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface
            )
        }

        Spacer(modifier = Modifier.height(6.dp))

        if (unlimited) {
            // A subtle full track to convey "unlimited".
            LinearProgressIndicator(
                progress = { 1f },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(8.dp)
                    .clip(RoundedCornerShape(6.dp)),
                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.35f),
                trackColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
            )
        } else {
            LinearProgressIndicator(
                progress = { fraction },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(8.dp)
                    .clip(RoundedCornerShape(6.dp)),
                color = barColor,
                trackColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f)
            )
        }

        if (trailingNote != null) {
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = trailingNote,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .clickable(onClick = onNoteClick)
                    .padding(vertical = 2.dp)
            )
        }
    }
}

@Composable
fun HandleEvents(
    events: Flow<HomeEvent>,
    snackbarHostState: SnackbarHostState,
    onNavigateToLogin: () -> Unit,
    onOpenPaymentUrl: (String) -> Unit = {}
) {
    events.collectWithLifecycleAware { event ->
        when (event) {
            HomeEvent.NavigateToLogin -> onNavigateToLogin()
            is HomeEvent.OpenUrl -> {
                onOpenPaymentUrl(event.url)
            }
            is HomeEvent.ShowError -> {
                snackbarHostState.showToasty(event.message, ToastyType.Error)
            }
            is HomeEvent.ShowSuccess -> {
                snackbarHostState.showToasty(event.message, ToastyType.Success)
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
fun PreviewHomeScreen() {
    AppTheme {
        HomeScreenContent(
            uiState = HomeState(),
            snackbarHostState = remember { SnackbarHostState() },
            onIntent = {},
            onNavigateToCalendar = {},
            onNavigateToLogin = {}
        )
    }
}

/**
 * Note under the bookings bar for add-on credit. Hidden on an unlimited plan:
 * the plan quota never runs out there, so the credit can never be spent and
 * showing it only reads as money that is stuck (the wallet screen explains it).
 */
internal fun appointmentWalletNote(wallet: Int, quota: Int): String? =
    if (showsAppointmentWalletNote(wallet, quota)) Str.wallet_extra_appointments(signedAmount(wallet))
    else null

internal fun smsWalletNote(wallet: Int): String? =
    if (wallet > 0) Str.wallet_extra_sms(signedAmount(wallet)) else null

internal fun showsAppointmentWalletNote(wallet: Int, quota: Int): Boolean =
    wallet > 0 && quota != EntitlementKeys.UNLIMITED
