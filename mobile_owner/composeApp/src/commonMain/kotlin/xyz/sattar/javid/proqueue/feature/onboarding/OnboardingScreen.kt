package xyz.sattar.javid.proqueue.feature.onboarding

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import xyz.sattar.javid.proqueue.core.prefs.PreferencesManager
import xyz.sattar.javid.proqueue.core.ui.components.AppButton
import xyz.sattar.javid.proqueue.core.ui.components.BrandIllustration
import xyz.sattar.javid.proqueue.core.ui.components.IllustrationBaseSize
import xyz.sattar.javid.proqueue.core.ui.components.NewAccountIllustration
import xyz.sattar.javid.proqueue.core.ui.components.NoobatyarMark
import xyz.sattar.javid.proqueue.core.utils.toPersianDigits
import xyz.sattar.javid.proqueue.ui.theme.SuccessColor
import kotlin.math.absoluteValue

/**
 * First-run introduction, shown once before the login screen.
 *
 * New owners used to land straight on a login form with no hint that they
 * first need an account. Four short pages say what Noobatyar does, and the
 * last one lays out the three steps to get started with registration as the
 * main action. Login stays one tap away on every page for people who already
 * have an account.
 *
 * Phone width only: AuthNavHost sends a wider window straight to the login
 * screen.
 *
 * Seen-state is stored as soon as the owner leaves for either screen (see
 * [PreferencesManager.setOnboardingSeen]); App also stores it on any
 * signed-in start, so existing owners never get it after a later logout.
 */
@Composable
fun OnboardingScreen(
    onRegister: () -> Unit,
    onLogin: () -> Unit,
) {
    val pagerState = rememberPagerState { OnboardingPages.size }
    val scope = rememberCoroutineScope()
    val isLast = pagerState.currentPage == OnboardingPages.lastIndex

    val leave: (() -> Unit) -> Unit = { target ->
        scope.launch {
            PreferencesManager.setOnboardingSeen()
            target()
        }
    }
    val onPrimary: () -> Unit = {
        if (isLast) {
            leave(onRegister)
        } else {
            scope.launch { pagerState.animateScrollToPage(pagerState.currentPage + 1) }
        }
    }
    val onSkip: () -> Unit = {
        // To the last page, not past it: the point of the introduction is
        // that new owners see they have to register first.
        scope.launch { pagerState.animateScrollToPage(OnboardingPages.lastIndex) }
    }

    OnboardingPhone(
        pagerState = pagerState,
        isLast = isLast,
        onPrimary = onPrimary,
        onSkip = onSkip,
        onLogin = { leave(onLogin) }
    )
}

private class OnboardingPage(
    val title: String,
    val body: String,
    val steps: List<String> = emptyList(),
    val illustration: @Composable (settled: Boolean) -> Unit,
)

private val OnboardingPages = listOf(
    OnboardingPage(
        title = "به نوبت‌یار خوش آمدید",
        body = "نوبت‌ها، صف انتظار و مشتری‌های کسب‌وکارتان را از یک‌جا مدیریت کنید؛ بدون دفتر و تماس‌های پشت‌سرهم.",
        illustration = { settled -> WelcomeIllustration(settled) }
    ),
    OnboardingPage(
        title = "رزرو آنلاین با یک لینک",
        body = "لینک یا QR اختصاصی کسب‌وکارتان را به مشتری‌ها بدهید تا خودشان ساعت خالی را انتخاب کنند و نوبت بگیرند.",
        illustration = { BookingLinkIllustration() }
    ),
    OnboardingPage(
        title = "یادآوری خودکار، غیبت کمتر",
        body = "قبل از هر نوبت برای مشتری یادآوری فرستاده می‌شود؛ خودکار از پنل، یا با یک لمس از گوشی خودتان.",
        illustration = { ReminderIllustration() }
    ),
    OnboardingPage(
        title = "همه‌چیز زیر نظر شما",
        body = "صف امروز، نوبت‌های انجام‌شده، غیبت‌ها و روند هفتگی را در یک نگاه ببینید.",
        illustration = { StatsIllustration() }
    ),
    OnboardingPage(
        title = "در سه قدم شروع کنید",
        body = "برای استفاده از نوبت‌یار اول یک حساب کاربری بسازید.",
        steps = listOf(
            "ثبت‌نام با نام، شماره موبایل و رمز عبور",
            "ساخت پروفایل کسب‌وکار",
            "فرستادن لینک رزرو برای مشتری‌ها"
        ),
        illustration = { settled -> NewAccountIllustration(settled) }
    ),
)

private const val PrimaryLabelNext = "بعدی"
private const val PrimaryLabelRegister = "ثبت‌نام و شروع"
private const val LoginLabel = "حساب دارم، ورود"
private const val SkipLabel = "رد شدن"

// ── Phone ───────────────────────────────────────────────────────────────────

@Composable
private fun OnboardingPhone(
    pagerState: PagerState,
    isLast: Boolean,
    onPrimary: () -> Unit,
    onSkip: () -> Unit,
    onLogin: () -> Unit,
) {
    Scaffold(containerColor = MaterialTheme.colorScheme.background) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            OnboardingHeader(
                showSkip = !isLast,
                onSkip = onSkip,
                modifier = Modifier.padding(start = 20.dp, end = 8.dp, top = 8.dp)
            )

            HorizontalPager(
                state = pagerState,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            ) { index ->
                val page = OnboardingPages[index]
                val settled = pagerState.settledPage == index
                BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
                    // The picture gives way first on a short screen, so the
                    // text and, on the last page, the steps stay in view.
                    val pictureSize = (maxHeight * 0.44f).coerceIn(150.dp, IllustrationBaseSize)
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(rememberScrollState())
                            .padding(horizontal = 28.dp)
                            .pageParallax(pagerState, index),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        BrandIllustration(size = pictureSize) { page.illustration(settled) }
                        Spacer(Modifier.height(20.dp))
                        PageText(page = page, settled = settled)
                    }
                }
            }

            OnboardingFooter(
                pagerState = pagerState,
                isLast = isLast,
                onPrimary = onPrimary,
                onLogin = onLogin,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 16.dp)
            )
        }
    }
}

// ── Shared pieces ───────────────────────────────────────────────────────────

@Composable
private fun OnboardingHeader(
    showSkip: Boolean,
    onSkip: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        NoobatyarMark(
            modifier = Modifier.size(28.dp),
            tint = MaterialTheme.colorScheme.primary
        )
        Spacer(Modifier.width(8.dp))
        Text(
            text = "نوبت‌یار",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground
        )
        Spacer(Modifier.weight(1f))
        // Kept in the layout when hidden so the header doesn't jump on the
        // last page.
        TextButton(
            onClick = onSkip,
            enabled = showSkip,
            modifier = Modifier.graphicsLayer { alpha = if (showSkip) 1f else 0f }
        ) {
            Text(
                text = SkipLabel,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun PageText(page: OnboardingPage, settled: Boolean) {
    Text(
        text = page.title,
        style = MaterialTheme.typography.headlineSmall,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.onBackground,
        textAlign = TextAlign.Center
    )
    Spacer(Modifier.height(10.dp))
    Text(
        text = page.body,
        style = MaterialTheme.typography.bodyLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center
    )
    if (page.steps.isNotEmpty()) {
        Spacer(Modifier.height(18.dp))
        StartSteps(steps = page.steps, settled = settled)
    }
}

/** The numbered steps of the last page, ticking off one after another. */
@Composable
private fun StartSteps(steps: List<String>, settled: Boolean) {
    var done by remember { mutableIntStateOf(0) }
    OnSettled(settled, delayMillis = 500) {
        done = 0
        for (i in 1..steps.size) {
            done = i
            delay(420)
        }
    }
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        steps.forEachIndexed { i, label ->
            StepRow(number = i + 1, label = label, done = done > i)
        }
    }
}

@Composable
private fun StepRow(number: Int, label: String, done: Boolean) {
    val badgeColor by animateColorAsState(
        targetValue = if (done) SuccessColor else MaterialTheme.colorScheme.primary.copy(alpha = 0.14f),
        animationSpec = tween(300),
        label = "step-badge"
    )
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(30.dp)
                    .background(badgeColor, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                AnimatedContent(
                    targetState = done,
                    transitionSpec = {
                        (fadeIn(tween(220)) + scaleIn(tween(260), initialScale = 0.4f)) togetherWith fadeOut(tween(120))
                    },
                    label = "step-mark"
                ) { isDone ->
                    if (isDone) {
                        Icon(Icons.Rounded.Check, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
                    } else {
                        Text(
                            text = number.toString().toPersianDigits(),
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }
            Text(
                text = label,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
private fun OnboardingFooter(
    pagerState: PagerState,
    isLast: Boolean,
    onPrimary: () -> Unit,
    onLogin: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scope = rememberCoroutineScope()
    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        PagerDots(
            count = OnboardingPages.size,
            current = pagerState.currentPage,
            onSelect = { scope.launch { pagerState.animateScrollToPage(it) } }
        )
        Spacer(Modifier.height(4.dp))
        AppButton(
            text = if (isLast) PrimaryLabelRegister else PrimaryLabelNext,
            onClick = onPrimary
        )
        TextButton(onClick = onLogin) {
            Text(
                text = LoginLabel,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.primary
            )
        }
    }
}

@Composable
private fun PagerDots(count: Int, current: Int, onSelect: (Int) -> Unit) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        repeat(count) { i ->
            val selected = i == current
            val width by animateDpAsState(if (selected) 26.dp else 8.dp, tween(300), label = "dot-width")
            val color by animateColorAsState(
                if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
                tween(300),
                label = "dot-color"
            )
            Box(
                modifier = Modifier
                    .height(8.dp)
                    .width(width)
                    .clip(CircleShape)
                    .background(color)
                    .clickable { onSelect(i) }
            )
        }
    }
}

/**
 * Fades and slightly shrinks a page as it slides away from the centre, so the
 * swipe reads as moving through cards rather than a flat strip.
 */
private fun Modifier.pageParallax(pagerState: PagerState, index: Int): Modifier = graphicsLayer {
    val offset = ((pagerState.currentPage - index) + pagerState.currentPageOffsetFraction).absoluteValue
        .coerceIn(0f, 1f)
    alpha = 1f - offset * 0.6f
    val scale = 1f - offset * 0.08f
    scaleX = scale
    scaleY = scale
}
