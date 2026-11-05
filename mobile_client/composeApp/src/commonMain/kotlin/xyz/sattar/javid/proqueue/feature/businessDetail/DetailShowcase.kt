package xyz.sattar.javid.proqueue.feature.businessDetail

import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.EllipsisVertical
import com.composables.icons.lucide.Flag
import com.composables.icons.lucide.MessageSquareText
import com.composables.icons.lucide.Pencil
import com.composables.icons.lucide.Star
import com.composables.icons.lucide.X
import xyz.sattar.javid.proqueue.core.ui.icons.filled
import androidx.compose.foundation.Image
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.graphics.Brush
import xyz.sattar.javid.proqueue.feature.booking.enterStagger
import xyz.sattar.javid.proqueue.feature.booking.pressScale
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil3.compose.AsyncImage
import org.jetbrains.compose.resources.painterResource
import proqueue.composeapp.generated.resources.Res
import proqueue.composeapp.generated.resources.main_icon
import xyz.sattar.javid.proqueue.core.utils.AppConfig
import xyz.sattar.javid.proqueue.core.utils.DateTimeUtils
import xyz.sattar.javid.proqueue.data.remoteDataSource.social.model.CertificateDto
import xyz.sattar.javid.proqueue.data.remoteDataSource.social.model.MyReviewDto
import xyz.sattar.javid.proqueue.data.remoteDataSource.social.model.PortfolioItemDto
import xyz.sattar.javid.proqueue.data.remoteDataSource.social.model.ReviewDto
import xyz.sattar.javid.proqueue.data.remoteDataSource.social.model.ReviewSummaryDto
import xyz.sattar.javid.proqueue.feature.discovery.Hairline
import xyz.sattar.javid.proqueue.feature.discovery.Radius
import xyz.sattar.javid.proqueue.feature.discovery.Space
import xyz.sattar.javid.proqueue.feature.discovery.toPersianDigits
import xyz.sattar.javid.proqueue.ui.theme.WarningColor

/** Media paths from the API may be relative to the server root, like logos. */
private fun mediaUrl(path: String?): String? =
    path?.takeIf { it.isNotBlank() }?.let { if (it.startsWith("http")) it else "${AppConfig.baseUrl}$it" }

/**
 * A showcase image with the Noobatyar mark overlaid in a corner. Drawn at
 * display time, never baked into the file. Once the image size is known the box
 * takes its exact aspect ratio, so the mark sits on the picture's own corner
 * even when the picture is letterboxed.
 */
@Composable
internal fun WatermarkedImage(
    url: String?,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    markSize: Dp = 20.dp,
    fit: Boolean = false,
) {
    var ratio by remember(url) { mutableStateOf<Float?>(null) }
    val boxModifier = if (fit) {
        val r = ratio
        if (r != null && r > 0f) modifier.aspectRatio(r) else modifier
    } else modifier
    Box(boxModifier) {
        AsyncImage(
            model = url,
            contentDescription = contentDescription,
            contentScale = if (fit) ContentScale.Fit else ContentScale.Crop,
            onSuccess = { s ->
                val size = s.painter.intrinsicSize
                if (size.width > 0f && size.height > 0f) ratio = size.width / size.height
            },
            modifier = Modifier.fillMaxSize()
        )
        Image(
            painter = painterResource(Res.drawable.main_icon),
            contentDescription = null,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(6.dp)
                .size(markSize)
                .alpha(0.55f)
        )
    }
}

// ── Portfolio ────────────────────────────────────────────────────────────────

@Composable
internal fun PortfolioSection(items: List<PortfolioItemDto>, index: Int) {
    var viewing by remember { mutableStateOf<Int?>(null) }
    GroupCard(
        title = "نمونه‌کار",
        index = index,
        trailing = { CountLabel(items.size) },
        bleed = {
            LazyRow(contentPadding = BleedPadding, horizontalArrangement = Arrangement.spacedBy(Space.sm)) {
                itemsIndexed(items, key = { _, it -> it.id }) { i, item ->
                    ShowcaseTile(
                        url = mediaUrl(item.thumbnail ?: item.image),
                        caption = item.caption,
                        modifier = Modifier.size(136.dp).enterStagger(i, stepMillis = 40),
                        onClick = { viewing = i },
                    )
                }
            }
        },
    )
    viewing?.let { start -> PortfolioViewer(items, start, onClose = { viewing = null }) }
}

@Composable
internal fun CountLabel(count: Int) {
    Text(
        "${count.toPersianDigits()} مورد",
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

/** A square thumbnail that shrinks under the finger, with its caption on a soft scrim. */
@Composable
private fun ShowcaseTile(url: String?, caption: String?, modifier: Modifier, onClick: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    Box(
        modifier
            .pressScale(interaction, 0.95f)
            .clip(Radius.md)
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .clickable(interactionSource = interaction, indication = null, onClick = onClick)
    ) {
        WatermarkedImage(url = url, contentDescription = caption, modifier = Modifier.fillMaxSize(), markSize = 16.dp)
        caption?.takeIf { it.isNotBlank() }?.let {
            Box(
                Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.55f))))
                    .padding(start = Space.sm, end = 28.dp, top = Space.lg, bottom = Space.sm)
            ) {
                Text(it, color = Color.White, style = MaterialTheme.typography.labelMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

@Composable
private fun PortfolioViewer(items: List<PortfolioItemDto>, startIndex: Int, onClose: () -> Unit) {
    Dialog(
        onDismissRequest = onClose,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        val pager = rememberPagerState(initialPage = startIndex) { items.size }
        Box(Modifier.fillMaxSize().background(Color.Black)) {
            HorizontalPager(pager, Modifier.fillMaxSize()) { page ->
                val item = items[page]
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    WatermarkedImage(
                        url = mediaUrl(item.image),
                        contentDescription = item.caption,
                        modifier = Modifier.fillMaxWidth().fillMaxHeight(),
                        markSize = 32.dp,
                        fit = true
                    )
                }
            }
            IconButton(onClick = onClose, modifier = Modifier.align(Alignment.TopEnd).padding(Space.md)) {
                Icon(Lucide.X, contentDescription = "بستن", tint = Color.White)
            }
            Column(Modifier.align(Alignment.BottomCenter).padding(Space.lg), horizontalAlignment = Alignment.CenterHorizontally) {
                items[pager.currentPage].caption?.takeIf { it.isNotBlank() }?.let {
                    Text(it, color = Color.White, style = MaterialTheme.typography.bodyMedium)
                }
                Text(
                    "${(pager.currentPage + 1).toPersianDigits()} از ${items.size.toPersianDigits()}",
                    color = Color.White.copy(alpha = 0.7f),
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }
    }
}

// ── Certificates ─────────────────────────────────────────────────────────────

@Composable
internal fun CertificatesSection(items: List<CertificateDto>, index: Int) {
    var viewing by remember { mutableStateOf<Int?>(null) }
    GroupCard(
        title = "مدارک و گواهینامه‌ها",
        index = index,
        trailing = { CountLabel(items.size) },
        bleed = {
            LazyRow(contentPadding = BleedPadding, horizontalArrangement = Arrangement.spacedBy(Space.md)) {
                itemsIndexed(items, key = { _, it -> it.id }) { i, cert ->
                    CertificateTile(cert, Modifier.enterStagger(i, stepMillis = 40)) { viewing = i }
                }
            }
        },
    )
    viewing?.let { start ->
        // Same full-screen viewer as the portfolio; certificates carry a title instead of a caption.
        PortfolioViewer(
            items = items.map { PortfolioItemDto(it.id, it.image, it.thumbnail, it.title) },
            startIndex = start,
            onClose = { viewing = null }
        )
    }
}

/** Fixed size: the image, one line of title, one line of issuer and year. */
@Composable
private fun CertificateTile(cert: CertificateDto, modifier: Modifier, onClick: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    Column(
        modifier
            .width(124.dp)
            .pressScale(interaction, 0.96f)
            .clickable(interactionSource = interaction, indication = null, onClick = onClick)
    ) {
        WatermarkedImage(
            url = mediaUrl(cert.thumbnail ?: cert.image),
            contentDescription = cert.title,
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(4f / 5f)
                .clip(Radius.md)
                .background(MaterialTheme.colorScheme.surfaceVariant),
            markSize = 16.dp
        )
        Spacer(Modifier.height(Space.sm))
        Text(cert.title, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
        val sub = listOfNotNull(cert.issuer?.takeIf { it.isNotBlank() }, cert.year?.toString()).joinToString(" · ")
        Text(
            sub.toPersianDigits(),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

// ── Reviews ──────────────────────────────────────────────────────────────────

@Composable
internal fun StarRow(rating: Int, size: Dp = 16.dp) {
    Row {
        repeat(5) { i ->
            Icon(
                if (i < rating) Lucide.Star.filled() else Lucide.Star,
                contentDescription = null,
                tint = WarningColor,
                modifier = Modifier.size(size)
            )
        }
    }
}

@Composable
internal fun ReviewsSection(
    state: BusinessSocialState,
    index: Int,
    onWrite: () -> Unit,
    onMore: () -> Unit,
    onReport: (ReviewDto) -> Unit,
) {
    val summary = state.summary ?: return
    GroupCard(
        title = "نظرات",
        index = index,
        trailing = {
            TextButton(onClick = onWrite, contentPadding = PaddingValues(horizontal = Space.sm)) {
                Icon(Lucide.MessageSquareText, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(Space.xs))
                Text(if (state.myReview != null) "ویرایش نظر من" else "ثبت نظر")
            }
        },
    ) {
        if (summary.count > 0) ReviewSummary(summary)
        else Text("نظری ثبت نشده؛ اولین نفر باشید.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        state.reviews.forEachIndexed { i, review ->
            Spacer(Modifier.height(Space.md))
            Hairline()
            ReviewRow(
                review,
                isMine = state.myReview?.id == review.id,
                modifier = Modifier.enterStagger(i, key = review.id),
                onReport = { onReport(review) },
                onEdit = onWrite,
            )
        }
        if (state.hasMoreReviews) {
            Spacer(Modifier.height(Space.md))
            OutlinedButton(
                onClick = onMore,
                enabled = !state.isLoadingMoreReviews,
                shape = Radius.md,
                modifier = Modifier.fillMaxWidth(),
            ) {
                if (state.isLoadingMoreReviews) {
                    CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                    Spacer(Modifier.width(Space.sm))
                }
                Text("نظرات بیشتر")
            }
        }
    }
}

@Composable
private fun ReviewSummary(summary: ReviewSummaryDto) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.width(88.dp)) {
            Text(formatRatingAverage(summary.avg), style = MaterialTheme.typography.displaySmall, fontWeight = FontWeight.Bold)
            StarRow(kotlin.math.round(summary.avg).toInt(), 14.dp)
            Spacer(Modifier.height(2.dp))
            Text(
                "${summary.count.toPersianDigits()} نظر",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Spacer(Modifier.width(Space.lg))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            for (star in 5 downTo 1) {
                val n = summary.histogram[star.toString()] ?: 0
                val target = if (summary.count > 0) (n.toFloat() / summary.count).coerceIn(0f, 1f) else 0f
                // Bars grow in once, from empty, when the section first shows.
                var shown by remember { mutableStateOf(0f) }
                LaunchedEffect(target) { shown = target }
                val fraction by animateFloatAsState(shown, tween(600, delayMillis = (5 - star) * 60), label = "histogram")
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(star.toPersianDigits(), style = MaterialTheme.typography.labelSmall, modifier = Modifier.width(14.dp))
                    Box(
                        Modifier.weight(1f).height(6.dp).clip(CircleShape).background(MaterialTheme.colorScheme.surfaceVariant)
                    ) {
                        Box(Modifier.fillMaxWidth(fraction).fillMaxHeight().clip(CircleShape).background(WarningColor))
                    }
                }
            }
        }
    }
}

@Composable
private fun ReviewRow(review: ReviewDto, isMine: Boolean, modifier: Modifier, onReport: () -> Unit, onEdit: () -> Unit) {
    val name = review.visitorName?.takeIf { it.isNotBlank() } ?: "کاربر نوبت‌یار"
    var menu by remember { mutableStateOf(false) }
    Row(modifier.fillMaxWidth().padding(top = Space.md), verticalAlignment = Alignment.Top) {
        Box(
            Modifier
                .size(36.dp)
                .clip(CircleShape)
                .background(if (isMine) MaterialTheme.colorScheme.primary.copy(alpha = 0.12f) else MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center,
        ) {
            if (review.visitorAvatar != null) {
                coil3.compose.AsyncImage(
                    model = review.visitorAvatar,
                    contentDescription = null,
                    contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                    modifier = Modifier.size(36.dp),
                )
            } else {
                Text(
                    name.trim().take(1),
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    color = if (isMine) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Spacer(Modifier.width(Space.md))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(name, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
                if (isMine) {
                    Spacer(Modifier.width(Space.xs))
                    Text(
                        "شما",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier
                            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.10f), CircleShape)
                            .padding(horizontal = 6.dp, vertical = 1.dp)
                    )
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                StarRow(review.rating, 13.dp)
                val millis = DateTimeUtils.parseIsoToEpochMillis(review.createdAt)
                if (millis > 0) {
                    Spacer(Modifier.width(Space.sm))
                    Text(
                        DateTimeUtils.getJalaliDate(millis).toPersianDigits(),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            review.text?.takeIf { it.isNotBlank() }?.let {
                Spacer(Modifier.height(Space.xs))
                Text(it, style = MaterialTheme.typography.bodyMedium)
            }
        }
        Box {
            IconButton(onClick = { menu = true }, modifier = Modifier.size(32.dp)) {
                Icon(Lucide.EllipsisVertical, contentDescription = "گزینه‌ها", tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(18.dp))
            }
            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                if (isMine) {
                    DropdownMenuItem(
                        text = { Text("ویرایش نظر") },
                        leadingIcon = { Icon(Lucide.Pencil, null) },
                        onClick = { menu = false; onEdit() },
                    )
                } else {
                    DropdownMenuItem(
                        text = { Text("گزارش این نظر") },
                        leadingIcon = { Icon(Lucide.Flag, null) },
                        onClick = { menu = false; onReport() },
                    )
                }
            }
        }
    }
}

/** Create / edit dialog; [existing] is the visitor's own review when editing. */
@Composable
internal fun ReviewDialog(
    existing: MyReviewDto?,
    initialRating: Int = 0,
    isSaving: Boolean,
    onDismiss: () -> Unit,
    onSubmit: (rating: Int, text: String) -> Unit,
    onDelete: () -> Unit,
) {
    var rating by remember { mutableStateOf(existing?.rating ?: initialRating) }
    var text by remember { mutableStateOf(existing?.text.orEmpty()) }
    var confirmDelete by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (existing != null) "ویرایش نظر من" else "ثبت نظر") },
        text = {
            Column {
                Row(horizontalArrangement = Arrangement.spacedBy(Space.xs)) {
                    for (star in 1..5) {
                        Icon(
                            if (star <= rating) Lucide.Star.filled() else Lucide.Star,
                            contentDescription = "${star.toPersianDigits()} ستاره",
                            tint = WarningColor,
                            modifier = Modifier.size(36.dp).clickable { rating = star }
                        )
                    }
                }
                Spacer(Modifier.height(Space.md))
                OutlinedTextField(
                    value = text,
                    onValueChange = { if (it.length <= MAX_REVIEW_TEXT) text = it },
                    label = { Text("نظر شما (اختیاری)") },
                    supportingText = { Text("${text.length.toPersianDigits()}/${MAX_REVIEW_TEXT.toPersianDigits()}") },
                    minLines = 3,
                    shape = Radius.md,
                    modifier = Modifier.fillMaxWidth()
                )
                if (existing != null) {
                    TextButton(onClick = { confirmDelete = true }, enabled = !isSaving, contentPadding = PaddingValues(0.dp)) {
                        Text("حذف نظر من", color = MaterialTheme.colorScheme.error)
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onSubmit(rating, text) }, enabled = rating in 1..5 && !isSaving) {
                Text(if (isSaving) "در حال ارسال…" else "ثبت")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("انصراف") } }
    )
    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("حذف نظر") },
            text = { Text("نظر شما حذف شود؟") },
            confirmButton = {
                TextButton(onClick = { confirmDelete = false; onDelete() }) {
                    Text("حذف", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("انصراف") } }
        )
    }
}

@Composable
internal fun ReviewReportDialog(
    isSubmitting: Boolean,
    onDismiss: () -> Unit,
    onSubmit: (reason: String, detail: String) -> Unit,
) {
    var reason by remember { mutableStateOf(reviewReportReasons.first().first) }
    var detail by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("گزارش این نظر") },
        text = {
            Column {
                reviewReportReasons.forEach { (code, label) ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .selectable(selected = reason == code, role = Role.RadioButton, onClick = { reason = code })
                            .padding(vertical = Space.xs),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(selected = reason == code, onClick = null)
                        Spacer(Modifier.width(Space.sm))
                        Text(label, style = MaterialTheme.typography.bodyMedium)
                    }
                }
                Spacer(Modifier.height(Space.sm))
                OutlinedTextField(
                    value = detail,
                    onValueChange = { if (it.length <= 500) detail = it },
                    label = { Text("توضیح (اختیاری)") },
                    minLines = 2,
                    shape = Radius.md,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onSubmit(reason, detail.trim()) }, enabled = !isSubmitting) {
                Text(if (isSubmitting) "در حال ارسال…" else "ارسال گزارش")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("انصراف") } }
    )
}
