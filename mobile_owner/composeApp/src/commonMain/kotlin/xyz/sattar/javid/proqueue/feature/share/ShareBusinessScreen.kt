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
import androidx.compose.foundation.layout.aspectRatio
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
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.CreditCard
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Link
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.QrCode2
import androidx.compose.material.icons.rounded.Storefront
import androidx.compose.material3.AlertDialog
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
import androidx.compose.ui.text.input.KeyboardType
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
import org.koin.compose.viewmodel.koinViewModel
import xyz.sattar.javid.proqueue.core.state.BusinessStateHolder
import xyz.sattar.javid.proqueue.core.ui.Capturable
import xyz.sattar.javid.proqueue.core.ui.CaptureController
import xyz.sattar.javid.proqueue.core.ui.components.AppButton
import xyz.sattar.javid.proqueue.core.ui.components.AppTextField
import xyz.sattar.javid.proqueue.core.ui.utils.CardNumberVisualTransformation
import xyz.sattar.javid.proqueue.core.ui.rememberCaptureController
import xyz.sattar.javid.proqueue.core.utils.AppConfig
import xyz.sattar.javid.proqueue.core.utils.detectIranianBank
import xyz.sattar.javid.proqueue.core.utils.encodePng
import xyz.sattar.javid.proqueue.core.utils.shareImage
import xyz.sattar.javid.proqueue.core.utils.shareText
import xyz.sattar.javid.proqueue.domain.model.business.Business
import xyz.sattar.javid.proqueue.feature.createBusiness.CreateBusinessIntent
import xyz.sattar.javid.proqueue.feature.createBusiness.CreateBusinessViewModel

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

private enum class ShareMode { QR, LINK, PROFILE, CARD }

@Composable
fun ShareBusinessScreen(onNavigateBack: () -> Unit) {
    val business by BusinessStateHolder.selectedBusiness.collectAsState()
    var mode by remember { mutableStateOf(ShareMode.QR) }
    var exporting by remember { mutableStateOf(false) }

    // One controller per visual card, held at screen level so switching tabs
    // doesn't drop the layer that is about to be captured.
    val posterCapture = rememberCaptureController()
    val storyCapture = rememberCaptureController()
    val cardCapture = rememberCaptureController()
    val scope = rememberCoroutineScope()

    // Saving a card number from here goes through the same view model the
    // advanced-settings screen uses, so there is one code path that writes a
    // business — and it needs the business loaded first, since the update
    // takes its id and its untouched fields from the view model's own state.
    val formViewModel: CreateBusinessViewModel = koinViewModel()
    var addingCard by remember { mutableStateOf(false) }

    val link = business?.uniqueCode?.let { "${AppConfig.bookingBaseUrl}/b/$it" }

    LaunchedEffect(business?.id) {
        business?.id?.takeIf { it != 0L }?.let {
            formViewModel.sendIntent(CreateBusinessIntent.LoadBusiness(it))
        }
    }

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

                        ShareMode.CARD ->
                            if (b.cardNumber.isBlank()) {
                                MissingCardPrompt(onAdd = { addingCard = true })
                            } else {
                                ExportableCard(
                                    controller = cardCapture,
                                    designHeightDp = POSTER_HEIGHT_DP,
                                ) { CardNumberPosterCard(b) }
                            }
                    }
                }
                Spacer(Modifier.height(8.dp))
                Text(
                    text = when (mode) {
                        ShareMode.QR -> "مناسب پست اینستاگرام و چاپ روی میز پذیرش"
                        ShareMode.LINK -> "مناسب پیام مستقیم به مشتری"
                        ShareMode.PROFILE -> "مناسب استوری اینستاگرام و واتساپ"
                        ShareMode.CARD ->
                            if (b.cardNumber.isBlank()) "برای واریز بیعانه، شماره کارت‌تان را اینجا ثبت کنید"
                            else "مناسب ارسال به مشتری برای واریز بیعانه"
                    },
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(16.dp))
            }

            ShareActionBar(
                primaryLabel = when {
                    mode == ShareMode.LINK -> "ارسال لینک"
                    mode == ShareMode.CARD && b.cardNumber.isBlank() -> "ثبت شماره کارت"
                    else -> "اشتراک‌گذاری تصویر"
                },
                exporting = exporting,
                onShare = {
                    when (mode) {
                        ShareMode.LINK -> shareText(shareMessage(b, link))
                        ShareMode.CARD -> if (b.cardNumber.isBlank()) {
                            addingCard = true
                        } else {
                            exporting = true
                            scope.launch {
                                shareImage(
                                    bytes = encodePng(cardCapture.capture()),
                                    fileName = "noobatyar-card-${b.uniqueCode}.png",
                                    caption = cardShareMessage(b),
                                )
                                exporting = false
                            }
                        }

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
                // The copy button copies whatever the visible card is *about*:
                // on the card-number tab that is the card number, not the
                // booking link the other three tabs are built around.
                copyValue = if (mode == ShareMode.CARD && b.cardNumber.isNotBlank()) {
                    b.cardNumber
                } else {
                    link
                },
            )

            if (addingCard) {
                AddCardDialog(
                    initialNumber = b.cardNumber,
                    initialOwner = b.cardOwnerName.ifBlank { b.title },
                    onDismiss = { addingCard = false },
                    onSave = { number, owner ->
                        formViewModel.sendIntent(saveCardIntent(b, number, owner))
                        addingCard = false
                    },
                )
            }
        }
    }
}

/** The message the link travels inside. A bare URL arrives in a chat with no
 *  idea what it is — and a customer who doesn't recognise it doesn't tap it. */
private fun shareMessage(business: Business, link: String): String =
    "برای رزرو نوبت در «${business.title}» روی این لینک بزنید:\n$link"

// ─────────────────────── export scaffolding ───────────────────────

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
        ShareModePill("شماره کارت", Icons.Rounded.CreditCard, selected == ShareMode.CARD,
            { onSelect(ShareMode.CARD) }, Modifier.weight(1f))
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
            .padding(vertical = 10.dp, horizontal = 2.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, tint = content, modifier = Modifier.size(15.dp))
        Spacer(Modifier.width(4.dp))
        Text(
            text = label,
            fontSize = 11.sp,
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
                .padding(start = 26.dp, end = 26.dp, top = 24.dp, bottom = 62.dp),
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
            NeonHalo(shape = RoundedCornerShape(26.dp)) {
                Surface(shape = RoundedCornerShape(26.dp), color = Color.White) {
                    Box(Modifier.padding(16.dp)) {
                        BookingQrCode(link, size = 148)
                    }
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

            Spacer(Modifier.weight(1f))

            NeonGlass(
                shape = RoundedCornerShape(12.dp),
                glowRadius = 8.dp,
                glowWidth = 2.dp,
            ) {
                LtrText(
                    text = link.shortForDisplay(),
                    fontSize = 12.sp,
                    weight = FontWeight.SemiBold,
                    color = Color.White,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
                )
            }

        }

        CardSignatureCorner(padding = 22.dp)
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
            NeonGlass(shape = RoundedCornerShape(12.dp), glowRadius = 7.dp, glowWidth = 2.dp) {
                Text(
                    text = "آموزش نوبت‌دهی",
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
            NeonHalo(shape = RoundedCornerShape(22.dp), modifier = Modifier.fillMaxWidth()) {
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
                    // Written for where this is actually read: a story or a
                    // post on the business's own page. "Tap the link" means
                    // nothing there — Instagram doesn't make story text
                    // tappable — so step one is the route that does work,
                    // which is the link in the page's bio.
                    GuideStep(1, "روی لینک داخل بایو بزنید.")
                    GuideStep(2, "خدمت، روز و ساعت دلخواه‌تان را انتخاب کنید.")
                    GuideStep(3, "شماره موبایل‌تان را وارد کنید؛ نوبت ثبت می‌شود.")
                }
              }
            }

            Spacer(Modifier.weight(0.6f))

            // Footer: the code and the link on the reading side, the maker's
            // mark alone in the opposite corner. This card can't take the
            // overlaid CardSignatureCorner the other three use — the bottom
            // left is where the QR would otherwise sit — so the row places
            // the two against each other instead.
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.Bottom,
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    NeonHalo(shape = RoundedCornerShape(14.dp), glowRadius = 10.dp) {
                        Surface(shape = RoundedCornerShape(14.dp), color = Color.White) {
                            Box(Modifier.padding(7.dp)) {
                                BookingQrCode(link, size = 64)
                            }
                        }
                    }
                    Spacer(Modifier.width(10.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "لینک نوبت‌دهی، داخل بایو",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White.copy(alpha = 0.8f),
                            maxLines = 1,
                        )
                        Spacer(Modifier.height(3.dp))
                        LtrText(
                            text = link.shortForDisplay(),
                            fontSize = 12.sp,
                            weight = FontWeight.Bold,
                            color = Color.White,
                        )
                    }
                }

                Spacer(Modifier.width(16.dp))

                AppSignature(onDark = true)
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

// ─────────────────────────── 4. card number ───────────────────────────

/** 6037 9974 5678 9004 — the grouping printed on the card itself, which is
 *  how anyone reads a card number back to check it. */
private fun String.asCardGroups(): String =
    filter { it.isDigit() }.chunked(4).joinToString("  ")

private fun cardShareMessage(business: Business): String = buildString {
    append("شماره کارت ${business.title}\n")
    append(business.cardNumber.asCardGroups())
    if (business.cardOwnerName.isNotBlank()) append("\nبه نام ${business.cardOwnerName}")
    detectIranianBank(business.cardNumber)?.let { append("\n${it.name}") }
}

/**
 * Writes the card number back onto the business.
 *
 * Every other field is copied off the business unchanged. That is not
 * defensive padding: the update is a full write, and a field left out here
 * would be saved as its default — which is exactly how an earlier version of
 * the advanced-settings screen silently wiped a business's whole service
 * menu each time anything on it was saved.
 */
private fun saveCardIntent(
    business: Business,
    cardNumber: String,
    cardOwnerName: String,
) = CreateBusinessIntent.CreateBusiness(
    title = business.title,
    category = business.category,
    phone = business.phone,
    extraPhones = business.extraPhones,
    address = business.address,
    defaultProgress = business.defaultServiceDuration.toString(),
    workStartHour = business.workStartHour,
    workEndHour = business.workEndHour,
    workStartMinute = business.workStartMinute,
    workEndMinute = business.workEndMinute,
    closedWeekdays = business.closedWeekdays,
    allowAnonymousView = business.allowAnonymousView,
    notifyOwnerBySms = business.notifyOwnerBySms,
    bio = business.bio,
    logoBytes = business.logoBytes,
    maxAppointmentsPerHour = business.maxAppointmentsPerHour,
    depositMode = business.depositMode ?: "NONE",
    depositAmount = business.depositAmount ?: 0,
    // Registering a card here also means the owner intends to be paid by
    // card, so CARD joins the accepted methods rather than the owner having
    // to go and tick it in advanced settings before the number does anything.
    acceptedPaymentMethods = (business.acceptedPaymentMethods.orEmpty().toSet() + "CARD")
        .joinToString(","),
    cardNumber = cardNumber,
    cardOwnerName = cardOwnerName,
    merchantId = business.merchantId,
    paymentLink = business.paymentLink,
    noticeEnabled = business.noticeEnabled,
    noticeMessage = business.noticeMessage,
    reminderDelivery = business.reminderDelivery,
    services = business.services,
    allowClientAddService = business.allowClientAddService,
)

@Composable
private fun CardNumberPosterCard(business: Business) {
    val bank = detectIranianBank(business.cardNumber)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Brush.linearGradient(listOf(ShareViolet, ShareVioletDeep))),
    ) {
        CardBackdrop(Modifier.fillMaxSize())

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(start = 26.dp, end = 26.dp, top = 26.dp, bottom = 62.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = "شماره کارت برای واریز",
                fontSize = 12.sp,
                color = Color.White.copy(alpha = 0.75f),
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = business.title,
                fontSize = 19.sp,
                lineHeight = 26.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                textAlign = TextAlign.Center,
                maxLines = 2,
            )

            Spacer(Modifier.weight(1f))

            // Laid out as the card itself — 85.6 × 53.98 mm, the ISO/IEC 7810
            // ID-1 ratio every bank card in the world is cut to. Someone
            // checking a number against the plastic in their hand is doing a
            // visual diff; matching the object makes that diff trivial.
            NeonGlass(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(1.95f),
                shape = RoundedCornerShape(20.dp),
                fill = Color.White.copy(alpha = 0.17f),
                glowRadius = 16.dp,
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 18.dp, vertical = 16.dp),
                    verticalArrangement = Arrangement.SpaceBetween,
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text(
                            text = bank?.name ?: "کارت بانکی",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                            maxLines = 1,
                            modifier = Modifier.weight(1f, fill = false),
                        )
                        // Null whenever the BIN isn't in the (deliberately
                        // partial) table — no logo at all, never a guessed
                        // one next to a real card number.
                        bank?.let {
                            Spacer(Modifier.width(8.dp))
                            Image(
                                painter = painterResource(it.logo),
                                contentDescription = it.name,
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(CircleShape),
                                contentScale = ContentScale.Crop,
                            )
                        }
                    }

                    // Number and holder as one block at the foot of the card,
                    // the way they are embossed on the plastic. The "به نام"
                    // label that used to sit above the name is gone: a name
                    // under a card number is already understood as the
                    // holder, and labelling it reads like a form field.
                    Column {
                        LtrText(
                            text = business.cardNumber.asCardGroups(),
                            fontSize = 20.sp,
                            weight = FontWeight.Bold,
                            color = Color.White,
                            letterSpacing = 1.sp,
                        )
                        Spacer(Modifier.height(6.dp))
                        Text(
                            text = business.cardOwnerName.ifBlank { business.title },
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = Color.White.copy(alpha = 0.92f),
                            maxLines = 1,
                        )
                    }
                }
            }

            Spacer(Modifier.weight(1f))

            // One line. The two-line version ("...و تصویر رسید را برای ما
            // بفرستید") wrapped, and a wrapped sentence under a card is the
            // part of the image that looks unfinished.
            Text(
                text = "مبلغ را به این کارت واریز کنید.",
                fontSize = 12.sp,
                color = Color.White.copy(alpha = 0.82f),
                textAlign = TextAlign.Center,
                maxLines = 1,
            )
        }

        CardSignatureCorner(padding = 22.dp)
    }
}

/** Shown instead of the card when there is no number to put on one. Not an
 *  error: a business that takes cash or pays online never registers a card,
 *  and this tab is the first place many owners will think to add one. */
@Composable
private fun MissingCardPrompt(onAdd: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(MaterialTheme.colorScheme.surface)
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(24.dp))
            .padding(26.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier = Modifier
                .size(62.dp)
                .clip(CircleShape)
                .background(ShareViolet.copy(alpha = 0.12f))
                .clickable(onClick = onAdd),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Rounded.Add, null, tint = ShareViolet, modifier = Modifier.size(30.dp))
        }
        Spacer(Modifier.height(14.dp))
        Text(
            text = "هنوز شماره کارتی ثبت نکرده‌اید",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Spacer(Modifier.height(6.dp))
        Text(
            text = "شماره کارت و نام صاحب حساب را ثبت کنید تا کارت آماده‌ی ارسال ساخته شود. همین شماره برای دریافت بیعانه هم استفاده می‌شود.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            lineHeight = 21.sp,
        )
    }
}

@Composable
private fun AddCardDialog(
    initialNumber: String,
    initialOwner: String,
    onDismiss: () -> Unit,
    onSave: (number: String, owner: String) -> Unit,
) {
    var number by remember { mutableStateOf(initialNumber) }
    var owner by remember { mutableStateOf(initialOwner) }
    val digits = number.filter { it.isDigit() }
    val bank = detectIranianBank(number)
    val valid = digits.length == 16 && owner.isNotBlank()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text("شماره کارت", fontWeight = FontWeight.Bold)
        },
        text = {
            Column {
                AppTextField(
                    value = number,
                    onValueChange = { number = it.filter { c -> c.isDigit() } },
                    label = "شماره کارت (۱۶ رقم)",
                    keyboardType = KeyboardType.Number,
                    maxLength = 16,
                    visualTransformation = CardNumberVisualTransformation(),
                    modifier = Modifier.fillMaxWidth(),
                    // The bank appearing as the digits go in is the same
                    // live confirmation the advanced-settings field gives.
                    leadingIcon = bank?.let {
                        {
                            Image(
                                painter = painterResource(it.logo),
                                contentDescription = it.name,
                                modifier = Modifier.size(28.dp).clip(CircleShape),
                                contentScale = ContentScale.Crop,
                            )
                        }
                    },
                )
                Spacer(Modifier.height(12.dp))
                AppTextField(
                    value = owner,
                    onValueChange = { owner = it },
                    label = "نام صاحب حساب",
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            Text(
                text = "ثبت",
                fontWeight = FontWeight.Bold,
                color = if (valid) ShareViolet else MaterialTheme.colorScheme.outline,
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .clickable(enabled = valid) { onSave(digits, owner.trim()) }
                    .padding(horizontal = 14.dp, vertical = 10.dp),
            )
        },
        dismissButton = {
            Text(
                text = "انصراف",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .clickable(onClick = onDismiss)
                    .padding(horizontal = 14.dp, vertical = 10.dp),
            )
        },
    )
}

// ─────────────────────────── shared pieces ───────────────────────────

@Composable
private fun ShareActionBar(
    primaryLabel: String,
    exporting: Boolean,
    onShare: () -> Unit,
    copyValue: String,
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
                text = primaryLabel,
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
                        clipboard.setText(buildAnnotatedString { append(copyValue) })
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
