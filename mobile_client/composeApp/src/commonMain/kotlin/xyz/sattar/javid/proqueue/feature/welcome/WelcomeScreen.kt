package xyz.sattar.javid.proqueue.feature.welcome

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.StartOffset
import androidx.compose.animation.core.StartOffsetType
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.composables.icons.lucide.BadgePercent
import com.composables.icons.lucide.CalendarCheck
import com.composables.icons.lucide.CircleCheck
import com.composables.icons.lucide.Clock
import com.composables.icons.lucide.Heart
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.MapPin
import com.composables.icons.lucide.Navigation
import com.composables.icons.lucide.ShieldCheck
import com.composables.icons.lucide.Stethoscope
import com.composables.icons.lucide.UserPlus
import kotlinx.coroutines.launch
import xyz.sattar.javid.proqueue.core.ui.components.AnimatedNoobatyarMark
import xyz.sattar.javid.proqueue.core.ui.components.NoobatyarMark
import xyz.sattar.javid.proqueue.ui.theme.SuccessColor
import kotlin.math.PI
import kotlin.math.absoluteValue
import kotlin.math.cos
import kotlin.math.sin

/**
 * One-time introduction, in the owner app's style (its feature/onboarding):
 * five pages, each an animated picture on the brand disc with small cards
 * floating beside it. Says what the app does for a customer: find on the map,
 * book without a call, insurance, and stories with discounts. The last page
 * leads to the city picker (the app is city-first, like Divar: the explore
 * screen opens the picker by itself on first launch). "رد شدن" jumps to that
 * last page rather than past it.
 */
@Composable
fun WelcomeScreen(onDone: () -> Unit) {
    val pager = rememberPagerState { Pages.size }
    val scope = rememberCoroutineScope()
    val isLast = pager.currentPage == Pages.lastIndex

    Column(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .windowInsetsPadding(WindowInsets.statusBars)
            .windowInsetsPadding(WindowInsets.navigationBars)
    ) {
        Header(
            showSkip = !isLast,
            onSkip = { scope.launch { pager.animateScrollToPage(Pages.lastIndex) } },
        )
        HorizontalPager(state = pager, modifier = Modifier.weight(1f).fillMaxWidth()) { index ->
            val page = Pages[index]
            val settled = pager.settledPage == index
            BoxWithConstraints(Modifier.fillMaxSize()) {
                // The picture gives way first on a short screen, so the text stays in view.
                val picture = (maxHeight * 0.5f).coerceIn(160.dp, BaseSize)
                Column(
                    Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 28.dp)
                        .pageParallax(pager, index),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    Scaled(picture) { page.picture(settled) }
                    Spacer(Modifier.height(24.dp))
                    Text(
                        page.title,
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center,
                    )
                    Spacer(Modifier.height(10.dp))
                    Text(
                        page.body,
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                    )
                }
            }
        }
        Column(
            Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Dots(Pages.size, pager.currentPage) { scope.launch { pager.animateScrollToPage(it) } }
            Spacer(Modifier.height(20.dp))
            Button(
                onClick = {
                    if (isLast) onDone() else scope.launch { pager.animateScrollToPage(pager.currentPage + 1) }
                },
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth().height(52.dp),
            ) {
                Text(if (isLast) "انتخاب شهر و شروع" else "بعدی", fontWeight = FontWeight.Bold)
            }
        }
    }
}

private class Page(val title: String, val body: String, val picture: @Composable (settled: Boolean) -> Unit)

private val Pages = listOf(
    Page(
        "به نوبت‌یار خوش آمدید",
        "پزشک، سالن و مشاور نزدیک خود را پیدا کنید و بدون تماس نوبت بگیرید.",
    ) { settled -> WelcomePicture(settled) },
    Page(
        "نزدیک‌ترین‌ها روی نقشه",
        "مراکز شهرتان را روی نقشه ببینید، با ساعت کاری و اولین نوبت خالی.",
    ) { MapPicture() },
    Page(
        "رزرو آنلاین، بدون تماس",
        "روز و ساعت را انتخاب کنید؛ نوبت شما همان لحظه ثبت می‌شود و یادآوری می‌گیرید.",
    ) { BookingPicture() },
    Page(
        "بیمه تکمیلی شما",
        "یک بار بیمه‌تان را انتخاب کنید تا مراکزی که آن را می‌پذیرند مشخص شوند.",
    ) { InsurancePicture() },
    Page(
        "استوری و تخفیف",
        "مراکز محبوبتان را دنبال کنید و استوری‌ها و تخفیف‌هایشان را از دست ندهید.",
    ) { StoryPicture() },
)

// ── Frame ────────────────────────────────────────────────────────────────────

@Composable
private fun Header(showSkip: Boolean, onSkip: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(start = 20.dp, end = 8.dp, top = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        NoobatyarMark(Modifier.size(28.dp), tint = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.width(8.dp))
        Text("نوبت‌یار", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Spacer(Modifier.weight(1f))
        // Kept in the layout when hidden so the header doesn't jump on the last page.
        TextButton(onClick = onSkip, enabled = showSkip, modifier = Modifier.graphicsLayer { alpha = if (showSkip) 1f else 0f }) {
            Text("رد شدن", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun Dots(count: Int, current: Int, onSelect: (Int) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        repeat(count) { i ->
            val selected = i == current
            val width by animateDpAsState(if (selected) 26.dp else 8.dp, tween(300), label = "dot")
            val color by animateColorAsState(
                if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
                tween(300), label = "dotColor",
            )
            Box(Modifier.height(8.dp).width(width).clip(CircleShape).background(color).clickable { onSelect(i) })
        }
    }
}

/** Fades and shrinks a page as it slides off-centre, so the swipe reads as moving through cards. */
private fun Modifier.pageParallax(pager: PagerState, index: Int): Modifier = graphicsLayer {
    val offset = ((pager.currentPage - index) + pager.currentPageOffsetFraction).absoluteValue.coerceIn(0f, 1f)
    alpha = 1f - offset * 0.6f
    val scale = 1f - offset * 0.08f
    scaleX = scale
    scaleY = scale
}

// ── Picture stage (as the owner app's BrandIllustration) ─────────────────────

private val BaseSize = 260.dp
private val DiscSize = 140.dp
private val Violet = Color(0xFF7C3AED)
private val Amber = Color(0xFFF59E0B)
private val DiscBrush = Brush.linearGradient(listOf(Color(0xFFA78BFA), Violet, Color(0xFF5B21B6)))

/** Shows a picture authored at [BaseSize], scaled to [size]. */
@Composable
private fun Scaled(size: Dp, content: @Composable () -> Unit) {
    Box(Modifier.size(size), contentAlignment = Alignment.Center) {
        Box(
            Modifier.requiredSize(BaseSize).graphicsLayer { val s = size / BaseSize; scaleX = s; scaleY = s },
            contentAlignment = Alignment.Center,
        ) { content() }
    }
}

/** A breathing violet glow behind the brand disc, with [content] on top. */
@Composable
private fun Stage(content: @Composable BoxScope.() -> Unit) {
    val glow = MaterialTheme.colorScheme.primary
    val breathe by rememberInfiniteTransition(label = "stage").animateFloat(
        0.94f, 1.04f, infiniteRepeatable(tween(2400, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "breathe",
    )
    Box(Modifier.size(BaseSize), contentAlignment = Alignment.Center) {
        Box(
            Modifier.size(BaseSize).graphicsLayer { scaleX = breathe; scaleY = breathe }
                .background(Brush.radialGradient(listOf(glow.copy(alpha = 0.3f), Color.Transparent)), CircleShape)
        )
        Box(
            Modifier.size(DiscSize).shadow(18.dp, CircleShape, ambientColor = Violet, spotColor = Violet)
                .background(DiscBrush, CircleShape)
        )
        content()
    }
}

/** A small card bobbing beside the disc on its own phase. */
@Composable
private fun Chip(
    icon: ImageVector,
    modifier: Modifier = Modifier,
    label: String? = null,
    tint: Color = MaterialTheme.colorScheme.primary,
    phase: Int = 0,
) {
    val bob by rememberInfiniteTransition(label = "chip").animateFloat(
        -5f, 5f,
        infiniteRepeatable(
            tween(1900, easing = FastOutSlowInEasing), RepeatMode.Reverse,
            initialStartOffset = StartOffset(phase, StartOffsetType.FastForward),
        ),
        label = "bob",
    )
    Surface(
        modifier = modifier.graphicsLayer { translationY = bob.dp.toPx() },
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)),
        shadowElevation = 6.dp,
    ) {
        Row(
            Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(18.dp))
            if (label != null) Text(label, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
        }
    }
}

/** Counts up each time [settled] turns true, so `key()` on it replays a play-once animation. */
@Composable
private fun rememberReplayKey(settled: Boolean): Int {
    var replay by remember { mutableIntStateOf(0) }
    LaunchedEffect(settled) { if (settled) replay++ }
    return replay
}

// ── Pictures ─────────────────────────────────────────────────────────────────

/** The mark assembling itself, with places to book circling it (one amber). */
@Composable
private fun WelcomePicture(settled: Boolean) {
    val replay = rememberReplayKey(settled)
    val dot = MaterialTheme.colorScheme.primary
    val angle by rememberInfiniteTransition(label = "orbit").animateFloat(
        0f, 360f, infiniteRepeatable(tween(16000, easing = LinearEasing)), label = "angle",
    )
    Stage {
        Canvas(Modifier.size(BaseSize)) {
            val r = size.minDimension / 2 - 14.dp.toPx()
            for (i in 0 until 9) {
                val a = (angle + i * 40f) * PI.toFloat() / 180f
                drawCircle(
                    color = if (i == 0) Amber else dot.copy(alpha = 0.25f + 0.25f * (i % 3)),
                    radius = (if (i == 0) 6f else 3.5f).dp.toPx(),
                    center = Offset(center.x + r * cos(a), center.y + r * sin(a)),
                )
            }
        }
        key(replay) { AnimatedNoobatyarMark(Modifier.size(88.dp), tint = Color.White) }
    }
}

/** A pin that drops and bounces on the disc, sending out rings, with what the map tells you. */
@Composable
private fun MapPicture() {
    val ring = MaterialTheme.colorScheme.primary
    val t = rememberInfiniteTransition(label = "map")
    val ripple by t.animateFloat(0f, 1f, infiniteRepeatable(tween(2400, easing = LinearEasing)), label = "ripple")
    val drop by t.animateFloat(
        0f, 0f,
        infiniteRepeatable(keyframes {
            durationMillis = 2400
            -26f at 0
            0f at 320
            -9f at 480
            0f at 620
            0f at 2400
        }),
        label = "drop",
    )
    Stage {
        Canvas(Modifier.size(BaseSize)) {
            val min = DiscSize.toPx() / 2
            val max = size.minDimension / 2
            repeat(2) { k ->
                val f = (ripple + k * 0.5f) % 1f
                drawCircle(ring.copy(alpha = 0.5f * (1f - f)), radius = min + (max - min) * f, style = Stroke(2.dp.toPx()))
            }
        }
        Icon(
            Lucide.MapPin, contentDescription = null, tint = Color.White,
            modifier = Modifier.size(64.dp).graphicsLayer { translationY = drop.dp.toPx() },
        )
        Chip(Lucide.Stethoscope, Modifier.align(Alignment.TopStart).offset(x = 6.dp, y = 26.dp), label = "باز است", tint = SuccessColor)
        Chip(Lucide.Navigation, Modifier.align(Alignment.BottomEnd).offset(x = (-4).dp, y = (-28).dp), label = "۱٫۲ کیلومتر", phase = 700)
        Chip(Lucide.Clock, Modifier.align(Alignment.TopEnd).offset(x = (-10).dp, y = 48.dp), phase = 1300)
    }
}

/** A calendar ticking a slot, with the booking it just made. */
@Composable
private fun BookingPicture() {
    val pop by rememberInfiniteTransition(label = "book").animateFloat(
        1f, 1f,
        infiniteRepeatable(keyframes {
            durationMillis = 2600
            1f at 0
            0.82f at 1100
            1.14f at 1300
            1f at 1500
            1f at 2600
        }),
        label = "pop",
    )
    Stage {
        Icon(
            Lucide.CalendarCheck, contentDescription = null, tint = Color.White,
            modifier = Modifier.size(64.dp).graphicsLayer { scaleX = pop; scaleY = pop },
        )
        Chip(Lucide.Clock, Modifier.align(Alignment.TopStart).offset(x = 4.dp, y = 24.dp), label = "شنبه ۱۰:۳۰")
        Chip(Lucide.CircleCheck, Modifier.align(Alignment.BottomEnd).offset(x = (-2).dp, y = (-26).dp), label = "ثبت شد", tint = SuccessColor, phase = 700)
    }
}

/** A shield with a check that pulses, and a card that says your insurance is accepted. */
@Composable
private fun InsurancePicture() {
    val t = rememberInfiniteTransition(label = "shield")
    val tilt by t.animateFloat(-6f, 6f, infiniteRepeatable(tween(1800, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "tilt")
    val glow by t.animateFloat(0f, 1f, infiniteRepeatable(tween(1800, easing = LinearEasing)), label = "glow")
    Stage {
        Canvas(Modifier.size(DiscSize)) {
            drawCircle(Color.White.copy(alpha = 0.35f * (1f - glow)), radius = size.minDimension / 2 * (0.55f + 0.45f * glow), style = Stroke(3.dp.toPx()))
        }
        Icon(
            Lucide.ShieldCheck, contentDescription = null, tint = Color.White,
            modifier = Modifier.size(64.dp).graphicsLayer { rotationZ = tilt; transformOrigin = TransformOrigin(0.5f, 0.9f) },
        )
        Chip(Lucide.CircleCheck, Modifier.align(Alignment.TopEnd).offset(x = (-6).dp, y = 30.dp), label = "بیمه شما پذیرفته می‌شود", tint = SuccessColor)
    }
}

/** A story ring turning round the disc, a heart for following, and a discount card. */
@Composable
private fun StoryPicture() {
    val t = rememberInfiniteTransition(label = "story")
    val turn by t.animateFloat(0f, 360f, infiniteRepeatable(tween(3200, easing = LinearEasing)), label = "turn")
    val beat by t.animateFloat(
        1f, 1f,
        infiniteRepeatable(keyframes {
            durationMillis = 1400
            1f at 0
            1.22f at 140
            1f at 300
            1.14f at 440
            1f at 600
        }),
        label = "beat",
    )
    val ring = Brush.sweepGradient(listOf(Color(0xFFA78BFA), Violet, Color(0xFFC026D3), Amber, Color(0xFFA78BFA)))
    Stage {
        Canvas(Modifier.size(DiscSize + 26.dp).graphicsLayer { rotationZ = turn }) {
            drawCircle(ring, radius = size.minDimension / 2 - 3.dp.toPx(), style = Stroke(5.dp.toPx()))
        }
        Icon(
            Lucide.Heart, contentDescription = null, tint = Color.White,
            modifier = Modifier.size(60.dp).graphicsLayer { scaleX = beat; scaleY = beat },
        )
        Chip(Lucide.BadgePercent, Modifier.align(Alignment.TopStart).offset(x = 2.dp, y = 20.dp), label = "٪۲۰ تخفیف", tint = Amber)
        Chip(Lucide.UserPlus, Modifier.align(Alignment.BottomEnd).offset(x = (-4).dp, y = (-24).dp), label = "دنبال کردن", phase = 800)
    }
}
