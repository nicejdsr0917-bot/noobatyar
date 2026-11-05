package xyz.sattar.javid.proqueue.feature.share

import xyz.sattar.javid.proqueue.core.i18n.Str
import xyz.sattar.javid.proqueue.core.utils.toPersianDigits
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.widthIn
import xyz.sattar.javid.proqueue.core.ui.components.isWideLayout
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
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
 *  - *QR poster* → a PNG to print for the counter or post as content.
 *  - *Link* → plain text, for a chat.
 *  - *Story* → a PNG of the booking guide, for a story or a channel.
 *
 * Every image is a 14 × 21 cm portrait sheet at 300 dpi (see ShareCardKit),
 * since owners print these for a table stand.
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
                        text = Str.share_booking,
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
            val b = business
            if (b == null || link == null) {
                // uniqueCode is assigned by the server on creation, so a null
                // one means the business list hasn't loaded yet — not an error
                // state, and not something the owner can act on.
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        text = Str.select_business_first,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                return@Column
            }

            val selector: @Composable (Modifier) -> Unit = { m ->
                ShareModeSelector(selected = mode, onSelect = { mode = it }, modifier = m)
            }
            val cardPreview: @Composable () -> Unit = {
                    AnimatedContent(
                        targetState = mode,
                        transitionSpec = { fadeIn(tween(220)) togetherWith fadeOut(tween(160)) },
                        label = "share-card",
                    ) { current ->
                        when (current) {
                            ShareMode.QR -> ExportableCard(
                                controller = posterCapture,
                                designHeightDp = CARD_HEIGHT_DP,
                            ) { QrPosterCard(b, link) }

                            ShareMode.LINK -> LinkShareCard(b, link)

                            ShareMode.PROFILE -> ExportableCard(
                                controller = storyCapture,
                                designHeightDp = CARD_HEIGHT_DP,
                            ) { ProfileStoryCard(b, link) }

                            ShareMode.CARD ->
                                if (b.cardNumber.isBlank()) {
                                    MissingCardPrompt(onAdd = { addingCard = true })
                                } else {
                                    ExportableCard(
                                        controller = cardCapture,
                                        designHeightDp = CARD_HEIGHT_DP,
                                    ) { CardNumberPosterCard(b) }
                                }
                        }
                    }
            }
            val hintText: @Composable () -> Unit = {
                    Text(
                        text = when (mode) {
                            ShareMode.QR -> Str.great_for_instagram_posts_and
                            ShareMode.LINK -> Str.great_for_direct_message_to
                            ShareMode.PROFILE -> Str.great_for_instagram_and_whatsapp
                            ShareMode.CARD ->
                                if (b.cardNumber.isBlank()) Str.add_your_card_number_here
                                else Str.great_for_sending_to_customers
                        },
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                    )
            }
            val actionBar: @Composable () -> Unit = {
                ShareActionBar(
                    primaryLabel = when {
                        mode == ShareMode.LINK -> Str.send_link
                        mode == ShareMode.CARD && b.cardNumber.isBlank() -> Str.add_card_number
                        else -> Str.share_image
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
                                        bytes = encodeCardPng(cardCapture.capture()),
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
                                        bytes = encodeCardPng(bitmap),
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
                    copyLabel = if (mode == ShareMode.CARD && b.cardNumber.isNotBlank()) {
                        Str.copy_card_number
                    } else {
                        Str.copy_link
                    },
                )
            }

            if (isWideLayout()) {
                // Desktop: pick and act on one side, the card itself large on
                // the other, so the preview never scrolls away from the button
                // that shares it.
                Row(
                    modifier = Modifier.fillMaxSize().padding(horizontal = 20.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(24.dp)
                ) {
                    Column(
                        modifier = Modifier.width(360.dp).fillMaxHeight(),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        selector(Modifier.fillMaxWidth())
                        hintText()
                        Spacer(Modifier.weight(1f))
                        actionBar()
                    }
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .verticalScroll(rememberScrollState()),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Box(Modifier.widthIn(max = 460.dp)) { cardPreview() }
                    }
                }
            } else {
                selector(Modifier.padding(horizontal = 16.dp, vertical = 12.dp))
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    cardPreview()
                    Spacer(Modifier.height(8.dp))
                    hintText()
                    Spacer(Modifier.height(16.dp))
                }
                actionBar()
            }

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
    Str.tap_this_link_to_book(business.title, link)

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
        ShareModePill(Str.qr_code, Icons.Rounded.QrCode2, selected == ShareMode.QR,
            { onSelect(ShareMode.QR) }, Modifier.weight(1f))
        ShareModePill(Str.link, Icons.Rounded.Link, selected == ShareMode.LINK,
            { onSelect(ShareMode.LINK) }, Modifier.weight(1f))
        ShareModePill(Str.story, Icons.Rounded.Person, selected == ShareMode.PROFILE,
            { onSelect(ShareMode.PROFILE) }, Modifier.weight(1f))
        ShareModePill(Str.card_number, Icons.Rounded.CreditCard, selected == ShareMode.CARD,
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
        Icon(icon, contentDescription = null, tint = content, modifier = Modifier.size(14.dp))
        Spacer(Modifier.width(4.dp))
        // Four equal-weight pills, but the labels are not equal lengths — the
        // longest ("شماره کارت") overran its share and was cut mid-glyph, with
        // no ellipsis to show it. It fits at this size; the clamp is the
        // guard for a wider system font or a narrower phone.
        Text(
            text = label,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            color = content,
            maxLines = 1,
            softWrap = false,
            overflow = TextOverflow.Ellipsis,
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

        // One centre line for everything — logo, name, code, caption, link,
        // signature. This is printed and stood on a counter, where a block
        // that sits a few millimetres off the others is the first thing seen.
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = CARD_SIDE_PADDING, vertical = CARD_EDGE_PADDING),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            CardHeader(business)

            // The QR and its caption take whatever height the header and
            // footer leave, centred in it. The code is as large as fits, up to
            // 176dp (about 72 mm printed — readable from arm's length across a
            // counter); a business name that wraps to two lines shrinks it a
            // little instead of pushing the footer off the bottom of the sheet.
            BoxWithConstraints(
                modifier = Modifier.weight(1f).fillMaxWidth(),
                contentAlignment = Alignment.Center,
            ) {
                val qrSize = (maxHeight - QR_POSTER_RESERVED).coerceIn(120.dp, 176.dp)
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    // The QR sits on its own white panel: scanners read a code
                    // by contrast, and a coloured or textured quiet zone around
                    // it is the usual reason a code that looks fine fails to scan.
                    NeonHalo(shape = RoundedCornerShape(26.dp)) {
                        Surface(shape = RoundedCornerShape(26.dp), color = Color.White) {
                            Box(Modifier.padding(16.dp)) {
                                BookingQrCode(link, size = qrSize)
                            }
                        }
                    }

                    Spacer(Modifier.height(16.dp))

                    Text(
                        text = Str.scan_to_book_appointment,
                        fontSize = 19.sp,
                        lineHeight = 26.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        textAlign = TextAlign.Center,
                        maxLines = 1,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }

            CardFooter(link)
        }
    }
}

/** Height around the code inside the poster's middle band: the white panel's
 *  padding (2 × 16), the caption's gap and line (16 + 26), and a 16dp floor of
 *  breathing room above and below the group (2 × 16). */
private val QR_POSTER_RESERVED = 32.dp + 16.dp + 26.dp + 32.dp

@Composable
private fun BookingQrCode(link: String, size: Dp) {
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
        contentDescription = Str.booking_qr_code,
        modifier = Modifier.size(size),
    )
}

// ─────────────────────────── 2. link card ───────────────────────────

@Composable
private fun LinkShareCard(business: Business, link: String) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(28.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        shadowElevation = 4.dp
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(22.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            // The owner's own logo, not a link glyph: this tab is one of four
            // ways to hand out the same link, and the business it belongs to
            // is the one thing all four have to agree on.
            BusinessAvatar(
                business = business,
                size = 56,
                ring = true,
                ringColor = ShareViolet.copy(alpha = 0.14f),
            )

            Spacer(Modifier.height(12.dp))

            Text(
                text = business.title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center,
                maxLines = 2,
            )
            Spacer(Modifier.height(6.dp))
            Text(
                text = Str.put_this_link_in_your,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                lineHeight = 20.sp,
            )

            // Link URL display removed as requested.
        }
    }
}

// ─────────────────────────── 3. profile story ───────────────────────────

/**
 * The card an owner posts where their customers already are — an Instagram
 * story, a Bale channel, a WhatsApp status.
 *
 * Laid out like a profile on purpose: someone scrolling past recognises the
 * shape before they read a word of it. Under it is only the booking guide:
 * the steps a first-time customer goes through, in the site's real order.
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
                .padding(horizontal = CARD_SIDE_PADDING, vertical = CARD_EDGE_PADDING),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(Modifier.weight(0.5f))

            BusinessAvatar(business, size = 72, ring = true)

            Spacer(Modifier.height(12.dp))

            Text(
                text = business.title,
                fontSize = 21.sp,
                lineHeight = 28.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                textAlign = TextAlign.Center,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(16.dp))
            Spacer(Modifier.weight(0.5f))

            // The whole point of this card: how a customer books, step by
            // step, in the order the booking site actually walks them through
            // (profile → sign in with an SMS code → day and time → confirm).
            NeonHalo(shape = RoundedCornerShape(24.dp), modifier = Modifier.fillMaxWidth()) {
                Surface(
                    shape = RoundedCornerShape(24.dp),
                    color = Color.White,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(Modifier.padding(horizontal = 18.dp, vertical = 14.dp)) {
                        Text(
                            text = Str.how_do_i_book,
                            fontSize = 17.sp,
                            lineHeight = 24.sp,
                            fontWeight = FontWeight.Bold,
                            color = ShareVioletDeep,
                        )
                        Spacer(Modifier.height(8.dp))
                        GuideStep(1, Str.tap_link_in_bio_to)
                        GuideStep(2, Str.sign_in_with_your_mobile)
                        GuideStep(3, Str.pick_free_day_and_time)
                        GuideStep(4, Str.confirm_appointment_you_can_track)
                    }
                }
            }

            Spacer(Modifier.height(18.dp))
            Spacer(Modifier.weight(0.6f))

            CardFooter()
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
        // 24dp disc against a 22sp line: it sits on the step's first line,
        // not centred on the whole (possibly two-line) step.
        Box(
            modifier = Modifier
                .size(24.dp)
                .clip(CircleShape)
                .background(ShareViolet),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = number.toString().toPersianDigits(),
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White,
            )
        }
        Spacer(Modifier.width(10.dp))
        Text(
            text = text,
            fontSize = 13.5.sp,
            lineHeight = 22.sp,
            fontWeight = FontWeight.Medium,
            color = Color(0xFF2E2B45),
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
    append(Str.card_number_of_n(business.title))
    append(business.cardNumber.asCardGroups())
    if (business.cardOwnerName.isNotBlank()) append(Str.nin_name_of(business.cardOwnerName))
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
    socialLinks = business.socialLinks,
    address = business.address,
    latitude = business.latitude,
    longitude = business.longitude,
    locationPrivacy = business.locationPrivacy,
    cityCode = business.cityCode,
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
                .padding(horizontal = CARD_SIDE_PADDING, vertical = CARD_EDGE_PADDING),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            // The QR poster's header, measure for measure: the two cards are
            // flipped between in one tap strip, and a header that shifts
            // between them reads as the page jumping.
            CardHeader(business, label = Str.card_number_for_transfer)

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
                    ) {
                        // Logo first: in RTL that is the right-hand edge, so
                        // it reads logo → bank name, as on the plastic. Null
                        // whenever the BIN isn't in the (deliberately partial)
                        // table — no logo at all, never a guessed one next to
                        // a real card number.
                        bank?.let {
                            Image(
                                painter = painterResource(it.logo),
                                contentDescription = it.name,
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(CircleShape),
                                contentScale = ContentScale.Crop,
                            )
                            Spacer(Modifier.width(8.dp))
                        }
                        Text(
                            text = bank?.name ?: Str.bank_card,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                            maxLines = 1,
                            modifier = Modifier.weight(1f),
                        )
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

            Spacer(Modifier.height(16.dp))

            // Directly under the card and on a solid panel — the one
            // instruction on this image, so it gets the same contrast as the
            // number itself rather than 12sp at 80% alpha down by the footer.
            NeonHalo(shape = RoundedCornerShape(18.dp), glowRadius = 10.dp, modifier = Modifier.fillMaxWidth()) {
                Surface(
                    shape = RoundedCornerShape(18.dp),
                    color = Color.White,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        text = Str.please_transfer_amount_to_this,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = ShareVioletDeep,
                        textAlign = TextAlign.Center,
                        maxLines = 1,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 13.dp),
                    )
                }
            }

            Spacer(Modifier.height(16.dp))
            Spacer(Modifier.weight(1f))

            CardFooter()
        }
    }
}

/** Shown instead of the card when there is no number to put on one. Not an
 *  error: a business that takes cash or pays online never registers a card,
 *  and this tab is the first place many owners will think to add one. */
@Composable
private fun MissingCardPrompt(onAdd: () -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(28.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        shadowElevation = 4.dp
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
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
                text = Str.you_havent_added_card_number,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Spacer(Modifier.height(6.dp))
            Text(
                text = Str.add_your_card_number_and,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                lineHeight = 21.sp,
            )
        }
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
            Text(Str.card_number, fontWeight = FontWeight.Bold)
        },
        text = {
            Column {
                AppTextField(
                    value = number,
                    onValueChange = { number = it.filter { c -> c.isDigit() } },
                    label = Str.card_number_16_digits,
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
                    label = Str.account_holder_name,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            Text(
                text = Str.accept,
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
                text = Str.cancel,
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
    copyLabel: String,
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
                    contentDescription = copyLabel,
                    tint = if (copied) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(22.dp),
                )
            }
        }
    }
}
