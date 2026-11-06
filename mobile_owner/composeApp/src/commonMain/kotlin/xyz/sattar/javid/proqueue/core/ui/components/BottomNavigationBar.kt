package xyz.sattar.javid.proqueue.core.ui.components

import xyz.sattar.javid.proqueue.core.i18n.Str
import androidx.compose.animation.*
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableIntStateOf
import xyz.sattar.javid.proqueue.core.navigation.MainTab
import xyz.sattar.javid.proqueue.core.navigation.TabRefreshRequests
import androidx.compose.runtime.mutableStateOf
import androidx.compose.foundation.Canvas
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.TimeSource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import xyz.sattar.javid.proqueue.core.ui.LocalHazeState
import xyz.sattar.javid.proqueue.ui.theme.DarkBottomBar
import xyz.sattar.javid.proqueue.ui.theme.LightBottomBar
import xyz.sattar.javid.proqueue.ui.theme.LocalIsDarkTheme
import xyz.sattar.javid.proqueue.ui.theme.PrimaryPurpleDark
import xyz.sattar.javid.proqueue.feature.profile.UserViewModel
import org.koin.compose.viewmodel.koinViewModel
import androidx.compose.runtime.collectAsState
import dev.chrisbanes.haze.hazeEffect
import dev.chrisbanes.haze.materials.ExperimentalHazeMaterialsApi
import dev.chrisbanes.haze.materials.HazeMaterials

/** Max gap between two taps on the same tab for it to count as a double-tap. */
private val DoubleTapWindow = 350.milliseconds

/**
 * A rounded-rectangle (all four corners) with a cutout notch at the top center
 * for the floating home button. All corners are rounded so the bar reads as a
 * detached, floating island rather than a bar welded to the screen edge.
 */
class BottomBarShape(
    private val cutoutRadius: androidx.compose.ui.unit.Dp,
    private val cornerRadius: androidx.compose.ui.unit.Dp = 28.dp
) : Shape {
    override fun createOutline(
        size: Size,
        layoutDirection: LayoutDirection,
        density: Density
    ): Outline {
        val path = Path().apply {
            val width = size.width
            val height = size.height
            val centerX = width / 2

            val r = with(density) { cutoutRadius.toPx() }
            val cr = with(density) { cornerRadius.toPx() }
            val curveWidth = r * 2.5f

            // Start just after the top-left rounded corner.
            moveTo(cr, 0f)

            // Top edge up to the cutout.
            lineTo(centerX - curveWidth, 0f)
            cubicTo(
                centerX - r * 1.5f, 0f,
                centerX - r * 1.2f, r,
                centerX, r
            )
            cubicTo(
                centerX + r * 1.2f, r,
                centerX + r * 1.5f, 0f,
                centerX + curveWidth, 0f
            )

            // Top edge to the top-right corner, then round it.
            lineTo(width - cr, 0f)
            arcTo(Rect(width - 2 * cr, 0f, width, 2 * cr), -90f, 90f, false)

            // Right edge → bottom-right corner.
            lineTo(width, height - cr)
            arcTo(Rect(width - 2 * cr, height - 2 * cr, width, height), 0f, 90f, false)

            // Bottom edge → bottom-left corner.
            lineTo(cr, height)
            arcTo(Rect(0f, height - 2 * cr, 2 * cr, height), 90f, 90f, false)

            // Left edge → top-left corner.
            lineTo(0f, cr)
            arcTo(Rect(0f, 0f, 2 * cr, 2 * cr), 180f, 90f, false)

            close()
        }
        return Outline.Generic(path)
    }
}

@OptIn(ExperimentalHazeMaterialsApi::class)
@Composable
fun BottomNavigationBar(
    tabs: List<MainTab>,
    selectedTab: MainTab,
    onTabSelected: (MainTab) -> Unit,
    modifier: Modifier = Modifier
) {
    val navigationBarsPadding = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    val hazeState = LocalHazeState.current
    val haptic = LocalHapticFeedback.current

    // Two taps on the same tab inside DoubleTapWindow ask that tab to reload
    // (see TabRefreshRequests). Handled here rather than with a double-tap
    // gesture detector so the first tap still navigates instantly instead of
    // waiting out the double-tap timeout.
    val lastTap = remember { mutableStateOf<Pair<MainTab, TimeSource.Monotonic.ValueTimeMark>?>(null) }
    // Bumped on each double-tap of Home; the ball rolls once per bump. A single
    // tap only selects the tab.
    val homeRolls = remember { mutableIntStateOf(0) }

    // Tapping the tab you're already on is a no-op upstream, so only tick the
    // haptic when the selection actually changes.
    val selectTab: (MainTab) -> Unit = { tab ->
        val previous = lastTap.value
        val isDoubleTap = previous != null && previous.first == tab &&
            previous.second.elapsedNow() < DoubleTapWindow
        if (isDoubleTap) {
            // Reset so a third quick tap starts a new pair instead of firing again.
            lastTap.value = null
            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
            TabRefreshRequests.request(tab)
            if (tab is MainTab.Home) homeRolls.intValue++
        } else {
            lastTap.value = tab to TimeSource.Monotonic.markNow()
            if (tab != selectedTab) haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
        }
        onTabSelected(tab)
    }

    // Floating island: lifted above the system nav bar with side + bottom
    // margins so it visually detaches from the screen edges.
    val barShape = BottomBarShape(cutoutRadius = 44.dp, cornerRadius = 28.dp)
    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(bottom = navigationBarsPadding)
            .padding(horizontal = 16.dp, vertical = 12.dp)
            .wrapContentHeight(),
        contentAlignment = Alignment.BottomCenter
    ) {
        // Main Navigation Bar Background — frosted glass over the scrolling content.
        //
        // The bar has to read as a distinct object floating over the page. In
        // the light theme that means going meaningfully *darker* than the page:
        // it previously painted `surface` (white) and then surfaceContainerHigh
        // (#F0F0F0) against a #F9FAFB background — a ~3% step, which is not a
        // bar, it is a smudge. LightBottomBar is picked for this job alone (see
        // ui/theme/Color.kt) rather than borrowing a token the cards and
        // shimmers also use.
        //
        // Nearly opaque, because the frosted-glass tint is what washed the
        // previous attempt out; the haze still blurs what passes underneath.
        //
        // The shadow is drawn *before* the clip: Surface's own shadowElevation
        // sat inside the clipped layer and was cut away, which is why the bar
        // never cast anything. It is brand-tinted so it reads as a soft glow
        // under the island rather than a grey smear.
        //
        // The rim is a vertical gradient — stronger along the top edge (where
        // the bar meets the content scrolling behind it) and fading towards
        // the bottom — which gives the glass a lit edge. In light it is
        // primary-derived: a white highlight would vanish against the
        // near-white page, and a neutral one looked grey next to the purple.
        val isDark = LocalIsDarkTheme.current
        val barColor = if (isDark) DarkBottomBar else LightBottomBar
        val primary = MaterialTheme.colorScheme.primary
        val rimBrush = if (isDark) {
            Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.18f), Color.White.copy(alpha = 0.04f)))
        } else {
            Brush.verticalGradient(listOf(primary.copy(alpha = 0.34f), primary.copy(alpha = 0.10f)))
        }
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .height(72.dp)
                .shadow(
                    elevation = 18.dp,
                    shape = barShape,
                    ambientColor = primary.copy(alpha = if (isDark) 0.35f else 0.22f),
                    spotColor = primary.copy(alpha = if (isDark) 0.45f else 0.30f),
                )
                .clip(barShape)
                .hazeEffect(
                    state = hazeState,
                    style = HazeMaterials.ultraThin(barColor),
                )
                .border(
                    width = 1.dp,
                    brush = rimBrush,
                    shape = barShape,
                ),
            color = barColor.copy(alpha = 0.97f),
            shape = barShape,
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp),
                horizontalArrangement = Arrangement.SpaceAround,
                verticalAlignment = Alignment.CenterVertically
            ) {
                tabs.forEach { tab ->
                    val isSelected = selectedTab == tab

                    if (tab is MainTab.Home) {
                        Spacer(modifier = Modifier.width(90.dp))
                    } else {
                        StandardNavigationItem(
                            isSelected = isSelected,
                            tab = tab,
                            onClick = { selectTab(tab) }
                        )
                    }
                }
            }
        }

        // Floating Home Button
        tabs.find { it is MainTab.Home }?.let { homeTab ->
            HomeNavigationItem(
                isSelected = selectedTab is MainTab.Home,
                tab = homeTab,
                rollTrigger = homeRolls.intValue,
                modifier = Modifier
                    .padding(bottom = 28.dp)
                    .size(72.dp),
                onClick = { selectTab(homeTab) }
            )
        }
    }
}

@Composable
private fun StandardNavigationItem(
    isSelected: Boolean,
    tab: MainTab,
    onClick: () -> Unit
) {
    val primary = MaterialTheme.colorScheme.primary
    val contentColor by animateColorAsState(
        // Inactive tabs use a fairly strong on-surface tone so the icons stay
        // legible over the frosted-glass bar (the old 0.6α was nearly invisible).
        targetValue = if (isSelected) primary
        else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.75f),
        animationSpec = tween(300)
    )

    // Selected tab sits in a tinted pill that grows out from the icon, the
    // same "active indicator" language Material 3 uses, tuned to the bar.
    val pillWidth by animateDpAsState(
        targetValue = if (isSelected) 60.dp else 36.dp,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMediumLow)
    )
    val pillAlpha by animateFloatAsState(
        targetValue = if (isSelected) 1f else 0f,
        animationSpec = tween(250)
    )

    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val pressScale by animateFloatAsState(
        targetValue = if (isPressed) 0.88f else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy)
    )

    Column(
        modifier = Modifier
            .width(80.dp)
            .clip(RoundedCornerShape(20.dp))
            .clickable(interactionSource = interactionSource, indication = null, onClick = onClick)
            .padding(vertical = 8.dp)
            .graphicsLayer {
                scaleX = pressScale
                scaleY = pressScale
            },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(
            modifier = Modifier
                .width(pillWidth)
                .height(32.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(primary.copy(alpha = 0.16f * pillAlpha)),
            contentAlignment = Alignment.Center
        ) {
            if (tab is MainTab.Settings) {
                val userViewModel: UserViewModel = koinViewModel()
                val userState by userViewModel.uiState.collectAsState()

                val avatarModifier = if (isSelected) {
                    Modifier.background(Brush.linearGradient(listOf(primary, PrimaryPurpleDark)))
                } else {
                    Modifier
                        .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.12f))
                        .border(1.dp, contentColor.copy(alpha = 0.35f), CircleShape)
                }
                Box(
                    modifier = Modifier
                        .size(26.dp)
                        .clip(CircleShape)
                        .then(avatarModifier),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = userState.userName?.firstOrNull()?.uppercaseChar()?.toString() ?: "?",
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 12.sp),
                        color = if (isSelected) MaterialTheme.colorScheme.onPrimary else contentColor,
                        fontWeight = FontWeight.Bold
                    )
                }
            } else {
                Crossfade(targetState = isSelected, animationSpec = tween(200)) { selected ->
                    Icon(
                        imageVector = if (selected) tab.iconSelected else tab.iconUnSelected,
                        contentDescription = (tab.title),
                        tint = contentColor,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }
        }

        AnimatedVisibility(
            visible = isSelected,
            enter = fadeIn(tween(220, delayMillis = 60)) + expandVertically(),
            exit = fadeOut(tween(120)) + shrinkVertically()
        ) {
            Text(
                text = (tab.title),
                style = MaterialTheme.typography.labelSmall,
                color = contentColor,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(top = 4.dp)
            )
        }
    }
}

@Composable
private fun HomeNavigationItem(
    isSelected: Boolean,
    tab: MainTab,
    rollTrigger: Int,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    // The caller's modifier fixes the outer slot (72.dp) so the halo has room;
    // the ball itself animates inside it. Previously the size animation was
    // applied on top of a fixed .size(64.dp) and so never actually moved.
    val ballSize by animateDpAsState(
        targetValue = if (isSelected) 62.dp else 56.dp,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMediumLow)
    )
    val haloAlpha by animateFloatAsState(
        targetValue = if (isSelected) 1f else 0f,
        animationSpec = tween(350)
    )

    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val pressScale by animateFloatAsState(
        targetValue = if (isPressed) 0.9f else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy)
    )

    // One full roll of the ball each time [rollTrigger] is bumped
    // (0 → 1 = one revolution). Skipped for the initial value so the ball
    // doesn't roll when the bar first appears.
    val roll = remember { Animatable(0f) }
    LaunchedEffect(rollTrigger) {
        if (rollTrigger == 0) return@LaunchedEffect
        roll.snapTo(0f)
        roll.animateTo(1f, tween(durationMillis = 900, easing = FastOutSlowInEasing))
        roll.snapTo(0f)
    }

    val brand = MaterialTheme.colorScheme.primary
    val hopPx = with(androidx.compose.ui.platform.LocalDensity.current) { 8.dp.toPx() }

    Box(
        modifier = modifier.graphicsLayer {
            scaleX = pressScale
            scaleY = pressScale
        },
        contentAlignment = Alignment.Center
    ) {
        // Halo ring shown only while Home is the active tab.
        Box(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer { alpha = haloAlpha }
                .clip(CircleShape)
                .background(brand.copy(alpha = 0.20f))
        )

        Box(
            modifier = Modifier
                .size(ballSize)
                .graphicsLayer {
                    // A small hop while it rolls, with a touch of squash on
                    // the way up and back down so it lands like a ball.
                    val p = roll.value
                    val hop = sin(PI.toFloat() * p)
                    translationY = -hopPx * hop
                    val squash = 0.05f * sin(2f * PI.toFloat() * p)
                    scaleX = 1f + squash
                    scaleY = 1f - squash
                }
                .shadow(
                    elevation = if (isSelected) 14.dp else 8.dp,
                    shape = CircleShape,
                    ambientColor = brand,
                    spotColor = brand
                )
                .clip(CircleShape)
                .clickable(interactionSource = interactionSource, indication = null, onClick = onClick),
            contentAlignment = Alignment.Center
        ) {
            val description = (tab.title)
            Canvas(
                modifier = Modifier
                    .fillMaxSize()
                    .semantics { contentDescription = description }
            ) {
                drawNoobatyarBall(rotation = roll.value * 360f)
            }
        }
    }
}

private val BallLight = Color(0xFFC4B5FD)
private val BallMid = Color(0xFF8B5CF6)
private val BallDeep = Color(0xFF4C1D95)
private val MarkDot = Color(0xFFFBBF24)
private val MarkDotDeep = Color(0xFFF59E0B)

/**
 * The Home button drawn as a lit sphere with the Noobatyar mark (open ring,
 * check, amber dot) printed on its surface.
 *
 * [rotation] is the ball's spin around its vertical axis in degrees. The mark
 * sits at that longitude and is projected onto the disc: it slides sideways
 * by sin(θ) and is foreshortened by cos(θ), so it genuinely wraps round the
 * curve and disappears over the edge rather than flipping like a card. A
 * second copy half a turn away comes round the other side, so the ball never
 * looks blank mid-roll. The lighting (base gradient, rim shade, specular
 * glint) is fixed in screen space — it's the surface that turns, not the
 * light — which is what makes the motion read as a sphere.
 */
private fun DrawScope.drawNoobatyarBall(rotation: Float) {
    val r = size.minDimension / 2f
    val c = center

    // Body: lit from the top-left.
    drawCircle(
        brush = Brush.radialGradient(
            colors = listOf(BallLight, BallMid, BallDeep),
            center = Offset(c.x - r * 0.35f, c.y - r * 0.45f),
            radius = r * 1.7f
        ),
        radius = r,
        center = c
    )

    // Front and back copies of the mark.
    for (offset in floatArrayOf(0f, 180f)) {
        val phi = (rotation + offset) * (PI.toFloat() / 180f)
        val facing = cos(phi)
        if (facing <= 0.02f) continue
        withTransform({
            translate(left = r * 0.72f * sin(phi))
            scale(scaleX = facing, scaleY = 1f, pivot = c)
        }) {
            drawNoobatyarMark(r = r, alpha = facing.coerceIn(0f, 1f))
        }
    }

    // Terminator: darken toward the lower-right rim, over the mark too, so
    // the print curves away with the surface.
    drawCircle(
        brush = Brush.radialGradient(
            0.55f to Color.Transparent,
            1f to Color.Black.copy(alpha = 0.32f),
            center = Offset(c.x - r * 0.2f, c.y - r * 0.25f),
            radius = r * 1.25f
        ),
        radius = r,
        center = c
    )

    // Specular glint.
    drawCircle(
        brush = Brush.radialGradient(
            colors = listOf(Color.White.copy(alpha = 0.55f), Color.Transparent),
            center = Offset(c.x - r * 0.38f, c.y - r * 0.48f),
            radius = r * 0.42f
        ),
        radius = r,
        center = c
    )

    // Thin bright rim so the ball separates from the notch.
    drawCircle(
        color = Color.White.copy(alpha = 0.35f),
        radius = r - 0.75f * density,
        center = c,
        style = Stroke(width = 1.5f * density)
    )
}

/** The logo mark, centred on the canvas and sized relative to the ball radius [r]. */
private fun DrawScope.drawNoobatyarMark(r: Float, alpha: Float) {
    // Proportions lifted from main_icon.png (512px tile: ring radius ≈148,
    // stroke ≈34, check and dot as measured), scaled so the ring spans ~46%
    // of the ball's radius.
    val k = r * 0.46f / 148f
    // 8 units ABOVE the ball's centre (it was 8 below): the ring is the heavy
    // part, and centred geometrically the mark read as sinking toward the
    // bottom rim. Same 16-unit lift as the brand SVGs.
    val c = center.copy(y = center.y - 8f * k)
    val white = Color.White.copy(alpha = alpha)

    val ringR = 148f * k
    drawArc(
        color = white,
        startAngle = -60f,
        sweepAngle = 300f,
        useCenter = false,
        topLeft = Offset(c.x - ringR, c.y - ringR),
        size = Size(ringR * 2, ringR * 2),
        style = Stroke(width = 34f * k, cap = StrokeCap.Round)
    )

    val check = Path().apply {
        moveTo(c.x - 66f * k, c.y)
        lineTo(c.x - 20f * k, c.y + 48f * k)
        lineTo(c.x + 72f * k, c.y - 57f * k)
    }
    drawPath(
        path = check,
        color = white,
        style = Stroke(width = 38f * k, cap = StrokeCap.Round, join = StrokeJoin.Round)
    )

    val dotCenter = Offset(c.x, c.y - 150f * k)
    drawCircle(
        brush = Brush.verticalGradient(
            colors = listOf(MarkDot.copy(alpha = alpha), MarkDotDeep.copy(alpha = alpha)),
            startY = dotCenter.y - 28f * k,
            endY = dotCenter.y + 28f * k
        ),
        radius = 28f * k,
        center = dotCenter
    )
}
