package xyz.sattar.javid.proqueue.feature.share

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Link
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.QrCode2
import androidx.compose.material.icons.rounded.Storefront
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import io.github.alexzhirkevich.qrose.options.QrBallShape
import io.github.alexzhirkevich.qrose.options.QrBrush
import io.github.alexzhirkevich.qrose.options.QrErrorCorrectionLevel
import io.github.alexzhirkevich.qrose.options.QrFrameShape
import io.github.alexzhirkevich.qrose.options.QrPixelShape
import io.github.alexzhirkevich.qrose.options.brush
import io.github.alexzhirkevich.qrose.options.circle
import io.github.alexzhirkevich.qrose.options.roundCorners
import io.github.alexzhirkevich.qrose.options.solid
import io.github.alexzhirkevich.qrose.rememberQrCodePainter
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.painterResource
import proqueue.composeapp.generated.resources.Res
import proqueue.composeapp.generated.resources.main_icon
import xyz.sattar.javid.proqueue.core.state.BusinessStateHolder
import xyz.sattar.javid.proqueue.core.ui.Capturable
import xyz.sattar.javid.proqueue.core.ui.CaptureController
import xyz.sattar.javid.proqueue.core.ui.components.AppButton
import xyz.sattar.javid.proqueue.core.ui.rememberCaptureController
import xyz.sattar.javid.proqueue.core.utils.AppConfig
import xyz.sattar.javid.proqueue.core.utils.encodePng
import xyz.sattar.javid.proqueue.core.utils.shareImage
import xyz.sattar.javid.proqueue.core.utils.shareText
import xyz.sattar.javid.proqueue.domain.model.business.Business

/**
 * The one place an owner goes to hand their booking link to a customer.
 *
 * Three ways to send the same link, because the owner is in three different
 * situations — and, crucially, **each one shares in its own medium**:
 *
 *  - *QR poster* → a 1080×1350 PNG, the square-ish format an Instagram feed
 *    post wants. Something to print for the counter or post as content.
 *  - *Link* → plain text, for a chat.
 *  - *Profile story* → a 1080×1920 PNG, exactly Instagram/WhatsApp story size,
 *    which is where these businesses' customers actually are.
 *
 * A text share of a link is useless to an owner whose whole audience is on
 * Instagram: you cannot post a sentence there. So the two visual options
 * export real images ([shareImage]), rendered from the very composables shown
 * on screen via [Capturable] — what the owner previews is pixel-for-pixel what
 * their customer receives.
 *
 * All three read the currently selected business from [BusinessStateHolder]
 * rather than taking arguments, so the card always describes the business Home
 * is showing.
 */

// ── Card geometry ────────────────────────────────────────────────────────────
//
// The cards are laid out in a fixed design space (360dp wide) under a pinned
// density of 3, so a card exports at exactly 1080px wide on *every* device —
// not at whatever the handset's own density happens to give. Instagram wants
// 1080; a 2.0-density phone would otherwise export a soft 720px image, and a
// 3.5-density one would waste bytes at 1260.
private const val CARD_DESIGN_WIDTH_DP = 360f
private const val CARD_EXPORT_DENSITY = 3f
private const val POSTER_HEIGHT_DP = 450f   // 4:5  → 1080×1350
private const val STORY_HEIGHT_DP = 640f    // 9:16 → 1080×1920

// Brand violets, fixed rather than theme roles: a shared card has to look the
// same to the customer who receives it regardless of the owner's light/dark
// setting, and the theme only overrides `primary` anyway (see AboutUsScreen's
// HeroViolets for the same reasoning).
private val ShareVioletDeep = Color(0xFF4C1D95)
private val ShareVioletDark = Color(0xFF5B21B6)
private val ShareViolet = Color(0xFF7C3AED)
private val ShareVioletLight = Color(0xFFA855F7)

private enum class ShareMode { QR, LINK, PROFILE }

@Composable
fun ShareBusinessScreen(onNavigateBack: () -> Unit) {
    val business by BusinessStateHolder.selectedBusiness.collectAsState()
    var mode by remember { mutableStateOf(ShareMode.QR) }
    var exporting by remember { mutableStateOf(false) }

    // One controller per visual card, held at screen level so switching tabs
    // doesn't drop the layer that is about to be captured.
    val posterCapture = rememberCaptureController()
    val storyCapture = rememberCaptureController()
    val scope = rememberCoroutineScope()

    val link = business?.uniqueCode?.let { "${AppConfig.bookingBaseUrl}/b/$it" }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "اشتراک‌گذاری نوبت‌دهی",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = null)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                ),
            )
        },
        containerColor = MaterialTheme.colorScheme.background,
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            ShareModeSelector(
                selected = mode,
                onSelect = { mode = it },
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            )

            val b = business
            if (b == null || link == null) {
                // uniqueCode is assigned by the server on creation, so a null
                // one means the business list hasn't loaded yet — not an error
                // state, and not something the owner can act on.
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        text = "ابتدا یک کسب‌وکار انتخاب کنید",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                return@Column
            }

            Column(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                AnimatedContent(
                    targetState = mode,
                    transitionSpec = { fadeIn(tween(220)) togetherWith fadeOut(tween(160)) },
                    label = "share-card",
                ) { current ->
                    when (current) {
                        ShareMode.QR -> ExportableCard(
                            controller = posterCapture,
                            designHeightDp = POSTER_HEIGHT_DP,
                        ) { QrPosterCard(b, link) }

                        ShareMode.LINK -> LinkShareCard(link)

                        ShareMode.PROFILE -> ExportableCard(
                            controller = storyCapture,
                            designHeightDp = STORY_HEIGHT_DP,
                        ) { ProfileStoryCard(b, link) }
                    }
                }
                Spacer(Modifier.height(8.dp))
                Text(
                    text = when (mode) {
                        ShareMode.QR -> "مناسب پست اینستاگرام و چاپ روی میز پذیرش"
                        ShareMode.LINK -> "مناسب پیام مستقیم به مشتری"
                        ShareMode.PROFILE -> "مناسب استوری اینستاگرام و واتساپ"
                    },
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(16.dp))
            }

            ShareActionBar(
                mode = mode,
                exporting = exporting,
                onShare = {
                    when (mode) {
                        ShareMode.LINK -> shareText(shareMessage(b, link))
                        else -> {
                            exporting = true
                            scope.launch {
                                val controller =
                                    if (mode == ShareMode.QR) posterCapture else storyCapture
                                val bitmap = controller.capture()
                                shareImage(
                                    bytes = encodePng(bitmap),
                                    fileName = if (mode == ShareMode.QR) {
                                        "noobatyar-qr-${b.uniqueCode}.png"
                                    } else {
                                        "noobatyar-story-${b.uniqueCode}.png"
                                    },
                                    caption = shareMessage(b, link),
                                )
                                exporting = false
                            }
                        }
                    }
                },
                link = link,
            )
        }
    }
}

/** The message the link travels inside. A bare URL arrives in a chat with no
 *  idea what it is — and a customer who doesn't recognise it doesn't tap it. */
private fun shareMessage(business: Business, link: String): String =
    "برای رزرو نوبت در «${business.title}» روی این لینک بزنید:\n$link"

// ─────────────────────── export scaffolding ───────────────────────

/**
 * Lays [content] out in the fixed design space described at the top of this
 * file and scales the *preview* down to fit the screen, leaving the captured
 * pixels at full export size.
 *
 * The density override also pins `fontScale` to 1: a shared card is a picture
 * being sent to someone else, so the owner's own accessibility text size must
 * not reflow it — that is the difference between an export that always looks
 * the same and one that silently breaks on some phones.
 */
@Composable
private fun ExportableCard(
    controller: CaptureController,
    designHeightDp: Float,
    content: @Composable () -> Unit,
) {
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val outerDensity = LocalDensity.current
        val availableWidthPx = with(outerDensity) { maxWidth.toPx() }
        val exportWidthPx = CARD_DESIGN_WIDTH_DP * CARD_EXPORT_DENSITY
        val scale = availableWidthPx / exportWidthPx
        val previewHeight =
            with(outerDensity) { (designHeightDp * CARD_EXPORT_DENSITY * scale).toDp() }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(previewHeight),
        ) {
            CompositionLocalProvider(
                LocalDensity provides Density(CARD_EXPORT_DENSITY, fontScale = 1f)
            ) {
                // Scaled about its centre, which is also where the Box puts
                // an oversized child: the preview box is exactly the scaled
                // size, so the two cancel out and the card lands flush inside
                // it. (Scaling about the top-left instead leaves the card
                // offset by half the overflow — it hangs off the side.)
                Box(
                    modifier = Modifier
                        .requiredSize(CARD_DESIGN_WIDTH_DP.dp, designHeightDp.dp)
                        .graphicsLayer {
                            scaleX = scale
                            scaleY = scale
                        },
                ) {
                    Capturable(controller) { content() }
                }
            }
        }
    }
}

// ─────────────────────────── mode selector ───────────────────────────

@Composable
private fun ShareModeSelector(
    selected: ShareMode,
    onSelect: (ShareMode) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
            .padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        ShareModePill("کد QR", Icons.Rounded.QrCode2, selected == ShareMode.QR,
            { onSelect(ShareMode.QR) }, Modifier.weight(1f))
        ShareModePill("لینک", Icons.Rounded.Link, selected == ShareMode.LINK,
            { onSelect(ShareMode.LINK) }, Modifier.weight(1f))
        ShareModePill("پروفایل", Icons.Rounded.Person, selected == ShareMode.PROFILE,
            { onSelect(ShareMode.PROFILE) }, Modifier.weight(1f))
    }
}

@Composable
private fun ShareModePill(
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    active: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val content = if (active) Color.White else MaterialTheme.colorScheme.onSurfaceVariant
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(if (active) ShareViolet else Color.Transparent)
            .clickable(onClick = onClick)
            .padding(vertical = 10.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, tint = content, modifier = Modifier.size(17.dp))
        Spacer(Modifier.width(6.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Bold,
            color = content,
            maxLines = 1,
        )
    }
}

// ─────────────────────────── 1. QR poster ───────────────────────────

@Composable
private fun QrPosterCard(business: Business, link: String) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Brush.linearGradient(listOf(ShareViolet, ShareVioletDeep))),
    ) {
        CardBackdrop(Modifier.fillMaxSize())

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 26.dp, vertical = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                BusinessAvatar(business, size = 44, ring = true)
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        text = business.title,
                        fontSize = 17.sp,
                        lineHeight = 22.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        maxLines = 2,
                    )
                    Text(
                        text = business.categoryLabel,
                        fontSize = 12.sp,
                        color = Color.White.copy(alpha = 0.72f),
                        maxLines = 1,
                    )
                }
            }

            Spacer(Modifier.weight(1f))

            // The QR sits on its own white panel: scanners read a code by
            // contrast, and a coloured or textured quiet zone around it is the
            // usual reason a code that looks fine fails to scan.
            Surface(shape = RoundedCornerShape(26.dp), color = Color.White) {
                Box(Modifier.padding(16.dp)) {
                    BookingQrCode(link, size = 168)
                }
            }

            Spacer(Modifier.height(16.dp))

            Text(
                text = "برای رزرو نوبت اسکن کنید",
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(6.dp))
            Text(
                text = "دوربین گوشی را روی کد بگیرید — بدون تماس، بدون انتظار",
                fontSize = 12.sp,
                lineHeight = 19.sp,
                color = Color.White.copy(alpha = 0.78f),
                textAlign = TextAlign.Center,
            )

            Spacer(Modifier.weight(1f))

            Surface(
                shape = RoundedCornerShape(12.dp),
                color = Color.White.copy(alpha = 0.14f),
            ) {
                LtrText(
                    text = link.shortForDisplay(),
                    fontSize = 12.sp,
                    weight = FontWeight.SemiBold,
                    color = Color.White,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
                )
            }

            Spacer(Modifier.height(14.dp))
            AppSignature(onDark = true)
        }
    }
}

@Composable
private fun BookingQrCode(link: String, size: Int) {
    val painter = rememberQrCodePainter(link) {
        // Medium, not Auto: Auto resolves to L, and these codes are meant to
        // be scanned off a printed card or someone else's phone screen at an
        // angle — the conditions L is least forgiving of.
        errorCorrectionLevel = QrErrorCorrectionLevel.Medium
        shapes {
            darkPixel = QrPixelShape.roundCorners()
            ball = QrBallShape.circle()
            frame = QrFrameShape.roundCorners(.25f)
        }
        colors {
            dark = QrBrush.brush {
                Brush.linearGradient(
                    listOf(ShareVioletDeep, ShareVioletLight),
                    end = Offset(it, it),
                )
            }
            frame = QrBrush.solid(ShareVioletDeep)
            ball = QrBrush.solid(ShareViolet)
        }
    }
    Image(
        painter = painter,
        contentDescription = "کد QR نوبت‌دهی",
        modifier = Modifier.size(size.dp),
    )
}

// ─────────────────────────── 2. link card ───────────────────────────

@Composable
private fun LinkShareCard(link: String) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(MaterialTheme.colorScheme.surface)
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(24.dp))
            .padding(22.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier = Modifier
                .size(56.dp)
                .clip(CircleShape)
                .background(ShareViolet.copy(alpha = 0.12f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Rounded.Link, null, tint = ShareViolet, modifier = Modifier.size(26.dp))
        }

        Spacer(Modifier.height(14.dp))

        Text(
            text = "لینک اختصاصی نوبت‌دهی شما",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Spacer(Modifier.height(6.dp))
        Text(
            text = "این لینک را در بیو، کانال یا پیام مستقیم بگذارید؛ مشتری با یک کلیک صفحه نوبت‌دهی شما را می‌بیند.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            lineHeight = 20.sp,
        )

        Spacer(Modifier.height(16.dp))

        Surface(
            shape = RoundedCornerShape(14.dp),
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(
                text = link,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                color = ShareViolet,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 14.dp),
            )
        }
    }
}

// ─────────────────────────── 3. profile story ───────────────────────────

/**
 * The card an owner posts where their customers already are — an Instagram
 * story, a Bale channel, a WhatsApp status.
 *
 * Laid out like a profile on purpose: someone scrolling past recognises the
 * shape before they read a word of it. Under the profile, the three-step guide
 * answers the question that actually stops people booking online for the first
 * time — not "what is this" but "what happens after I tap". The small QR in
 * the footer is there because a story is *screenshotted* as often as it is
 * tapped, and a screenshot of a link is useless while a screenshot of a QR
 * still works.
 */
@Composable
private fun ProfileStoryCard(business: Business, link: String) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Brush.linearGradient(listOf(ShareVioletLight, ShareVioletDeep))),
    ) {
        CardBackdrop(Modifier.fillMaxSize())

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 26.dp, vertical = 28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Surface(shape = RoundedCornerShape(20.dp), color = Color.White.copy(alpha = 0.16f)) {
                Box(Modifier.padding(horizontal = 12.dp, vertical = 6.dp)) {
                    AppSignature(onDark = true)
                }
            }

            Spacer(Modifier.weight(0.6f))

            BusinessAvatar(business, size = 104, ring = true)

            Spacer(Modifier.height(14.dp))

            Text(
                text = business.title,
                fontSize = 23.sp,
                lineHeight = 30.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                textAlign = TextAlign.Center,
                maxLines = 2,
            )
            Spacer(Modifier.height(8.dp))
            Surface(shape = RoundedCornerShape(10.dp), color = Color.White.copy(alpha = 0.18f)) {
                Text(
                    text = business.categoryLabel,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                )
            }

            if (business.bio.isNotBlank()) {
                Spacer(Modifier.height(12.dp))
                Text(
                    text = business.bio.trim(),
                    fontSize = 13.sp,
                    lineHeight = 22.sp,
                    color = Color.White.copy(alpha = 0.85f),
                    textAlign = TextAlign.Center,
                    // Clamped, unlike the booking page's own "درباره‌ی ما"
                    // section: that page can scroll, a story is one fixed
                    // frame, and a bio long enough to fill it would push the
                    // guide and the QR off the bottom of the image.
                    maxLines = 3,
                )
            }

            Spacer(Modifier.weight(0.7f))

            // White card for the guide: the one part a first-time customer
            // actually has to read, so it gets the highest contrast on the
            // whole story instead of being another line of text on violet.
            Surface(
                shape = RoundedCornerShape(22.dp),
                color = Color.White,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(Modifier.padding(horizontal = 18.dp, vertical = 16.dp)) {
                    Text(
                        text = "چطور نوبت بگیرم؟",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = ShareVioletDeep,
                    )
                    Spacer(Modifier.height(10.dp))
                    GuideStep(1, "روی لینک زیر بزنید یا کد را اسکن کنید.")
                    GuideStep(2, "خدمت، روز و ساعت دلخواه‌تان را انتخاب کنید.")
                    GuideStep(3, "شماره موبایل‌تان را وارد کنید؛ نوبت ثبت می‌شود.")
                }
            }

            Spacer(Modifier.weight(0.6f))

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Surface(shape = RoundedCornerShape(14.dp), color = Color.White) {
                    Box(Modifier.padding(7.dp)) {
                        BookingQrCode(link, size = 74)
                    }
                }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        text = "رزرو نوبت آنلاین",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White.copy(alpha = 0.8f),
                    )
                    Spacer(Modifier.height(3.dp))
                    LtrText(
                        text = link.shortForDisplay(),
                        fontSize = 13.sp,
                        weight = FontWeight.Bold,
                        color = Color.White,
                    )
                }
            }
        }
    }
}

@Composable
private fun GuideStep(number: Int, text: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Box(
            modifier = Modifier
                .size(22.dp)
                .clip(CircleShape)
                .background(ShareViolet.copy(alpha = 0.14f)),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = "۰۱۲۳۴۵۶۷۸۹"[number].toString(),
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                color = ShareViolet,
            )
        }
        Spacer(Modifier.width(9.dp))
        Text(
            text = text,
            fontSize = 12.sp,
            lineHeight = 21.sp,
            color = Color(0xFF3F3D56),
            modifier = Modifier.weight(1f),
        )
    }
}

// ─────────────────────────── shared pieces ───────────────────────────

/**
 * The backdrop the cards are built on: a diagonal violet ramp, two off-centre
 * light sources, a pair of thin concentric rings, and a vignette along the
 * bottom edge.
 *
 * A single linear gradient reads as a coloured rectangle — fine for a screen,
 * not for something posted next to other people's content. The highlights give
 * the surface a light source, the rings give it a subject, and the vignette
 * keeps the footer text (link, signature) legible against the brightest part
 * of the ramp instead of fighting it.
 *
 * Drawn rather than composed so every measurement is a fraction of the card's
 * own size — the same code produces the 4:5 poster and the 9:16 story without
 * a second set of numbers.
 */
@Composable
private fun CardBackdrop(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier.drawBehind {
            val w = size.width
            val h = size.height

            // Warm highlight, upper-trailing corner.
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(Color.White.copy(alpha = 0.22f), Color.Transparent),
                    center = Offset(w * 0.86f, h * 0.06f),
                    radius = w * 0.85f,
                ),
                radius = w * 0.85f,
                center = Offset(w * 0.86f, h * 0.06f),
            )
            // Cooler bloom, lower-leading corner.
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(ShareVioletLight.copy(alpha = 0.55f), Color.Transparent),
                    center = Offset(w * 0.08f, h * 0.72f),
                    radius = w * 0.95f,
                ),
                radius = w * 0.95f,
                center = Offset(w * 0.08f, h * 0.72f),
            )

            // Two hairline rings, deliberately running off the edges: a shape
            // that is fully contained reads as a sticker placed on the card,
            // one that is cropped reads as the card being a window onto
            // something larger.
            val ringCenter = Offset(w * 0.92f, h * 0.18f)
            drawCircle(
                color = Color.White.copy(alpha = 0.16f),
                radius = w * 0.46f,
                center = ringCenter,
                style = Stroke(width = 1.5.dp.toPx()),
            )
            drawCircle(
                color = Color.White.copy(alpha = 0.10f),
                radius = w * 0.66f,
                center = ringCenter,
                style = Stroke(width = 1.5.dp.toPx()),
            )

            // Vignette: the lower third carries the link and the signature.
            drawRect(
                brush = Brush.verticalGradient(
                    colors = listOf(Color.Transparent, ShareVioletDeep.copy(alpha = 0.55f)),
                    startY = h * 0.55f,
                    endY = h,
                )
            )
        }
    )
}

/** Drops the scheme — "noobatyar.ir/b/CODE" is what a person reads off a
 *  poster, and the https:// in front of it is nine characters of noise. */
private fun String.shortForDisplay(): String =
    removePrefix("https://").removePrefix("http://")

/**
 * A URL rendered left-to-right even though the surrounding app is RTL.
 *
 * Without this the bidi algorithm reorders the URL's own segments around the
 * slashes — "10.0.2.2:3000/b/DENTDEMO" renders as "b/DENTDEMO/10.0.2.2:3000",
 * which is not just ugly but wrong: someone typing it in off a printed poster
 * gets a dead link.
 */
@Composable
private fun LtrText(
    text: String,
    fontSize: androidx.compose.ui.unit.TextUnit,
    weight: FontWeight,
    color: Color,
    modifier: Modifier = Modifier,
) {
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
        Text(
            text = text,
            fontSize = fontSize,
            fontWeight = weight,
            color = color,
            maxLines = 1,
            modifier = modifier,
        )
    }
}

@Composable
private fun BusinessAvatar(business: Business, size: Int, ring: Boolean = false) {
    val inner: @Composable () -> Unit = {
        Box(
            modifier = Modifier
                .size(size.dp)
                .clip(CircleShape)
                .background(Color.White),
            contentAlignment = Alignment.Center,
        ) {
            if (business.logoPath.isNotEmpty()) {
                val url = if (business.logoPath.startsWith("http")) business.logoPath
                else "${AppConfig.baseUrl}${business.logoPath}"
                AsyncImage(
                    model = url,
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop,
                )
            } else {
                Icon(
                    Icons.Rounded.Storefront,
                    contentDescription = null,
                    tint = ShareViolet,
                    modifier = Modifier.size((size * 0.45f).dp),
                )
            }
        }
    }

    if (!ring) {
        inner()
        return
    }
    Box(
        modifier = Modifier
            .size((size + 8).dp)
            .clip(CircleShape)
            .background(Color.White.copy(alpha = 0.28f)),
        contentAlignment = Alignment.Center,
    ) {
        inner()
    }
}

/**
 * Noobatyar's mark at the foot of a shared card.
 *
 * A signature, not a watermark: an app icon blown up behind the content at 10%
 * alpha doesn't read as branding, it reads as a rendering artefact sitting
 * behind the business's own logo. Small, legible, and clearly a credit line is
 * both quieter *and* more recognisable. The card belongs to the business; this
 * only says what made it.
 */
@Composable
private fun AppSignature(onDark: Boolean) {
    val tone = if (onDark) Color.White else MaterialTheme.colorScheme.onSurfaceVariant
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.alpha(if (onDark) 0.62f else 0.55f),
    ) {
        Image(
            painter = painterResource(Res.drawable.main_icon),
            contentDescription = null,
            modifier = Modifier.size(15.dp),
        )
        Spacer(Modifier.width(5.dp))
        Text(text = "نوبت‌یار", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = tone)
    }
}

@Composable
private fun ShareActionBar(
    mode: ShareMode,
    exporting: Boolean,
    onShare: () -> Unit,
    link: String,
) {
    val clipboard = LocalClipboardManager.current
    var copied by remember { mutableStateOf(false) }

    LaunchedEffect(copied) {
        if (copied) {
            delay(2000)
            copied = false
        }
    }

    Surface(color = MaterialTheme.colorScheme.background, tonalElevation = 3.dp) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            AppButton(
                text = if (mode == ShareMode.LINK) "ارسال لینک" else "اشتراک‌گذاری تصویر",
                onClick = onShare,
                isLoading = exporting,
                modifier = Modifier.weight(1f),
            )

            Box(
                modifier = Modifier
                    .size(56.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(
                        if (copied) ShareViolet else MaterialTheme.colorScheme.surfaceVariant
                    )
                    .clickable {
                        clipboard.setText(buildAnnotatedString { append(link) })
                        copied = true
                    },
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = if (copied) Icons.Rounded.Check else Icons.Rounded.ContentCopy,
                    contentDescription = "کپی لینک",
                    tint = if (copied) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(22.dp),
                )
            }
        }
    }
}
