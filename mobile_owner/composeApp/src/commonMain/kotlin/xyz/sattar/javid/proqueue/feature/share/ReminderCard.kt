package xyz.sattar.javid.proqueue.feature.share

import xyz.sattar.javid.proqueue.core.ui.components.AdaptiveSheet
import androidx.compose.foundation.background
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.AbsoluteAlignment
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import xyz.sattar.javid.proqueue.core.state.BusinessStateHolder
import xyz.sattar.javid.proqueue.core.ui.components.AppButton
import xyz.sattar.javid.proqueue.core.ui.rememberCaptureController
import xyz.sattar.javid.proqueue.core.utils.DateTimeUtils
import xyz.sattar.javid.proqueue.core.utils.encodePng
import xyz.sattar.javid.proqueue.core.utils.shareImage
import xyz.sattar.javid.proqueue.core.utils.toPersianDigits
import xyz.sattar.javid.proqueue.domain.model.business.Business
import androidx.compose.runtime.collectAsState

/**
 * "Your appointment is in two days" — as a picture.
 *
 * The app could already send that as an SMS or a WhatsApp message, and still
 * does; this is the same reminder in the form the customer actually keeps. A
 * line of text scrolls away in a chat within an hour, while an image is
 * saved, pinned and re-opened — and it arrives carrying the business's name
 * and the exact date and time, so the customer never has to scroll back to
 * find out when they said they'd come.
 *
 * Built from the same [ShareCardKit] pieces as the booking-link cards, at the
 * same 1080×1350, for the same reason those share one kit: two cards that
 * drift apart within one release look like two different products.
 */

/**
 * How long until [appointmentDate], phrased the way a person would say it.
 *
 * Deliberately not [DateTimeUtils.calculateWaitingTime], which is written for
 * the queue on Home, where everything is hours away: it reports purely in
 * hours and minutes, so a reminder sent three days ahead would read "۷۲
 * ساعت" — technically right, and not how anyone talks. Past a day this
 * counts in days, and it stops at two units, since "۳ روز و ۲ ساعت و ۱۴
 * دقیقه" is a stopwatch, not a reminder.
 */
internal fun remainingUntil(appointmentDate: Long): String {
    val diff = appointmentDate - DateTimeUtils.systemCurrentMilliseconds()
    if (diff <= 0) return "زمان نوبت فرا رسیده"

    val totalMinutes = diff / 60_000
    val days = totalMinutes / (60 * 24)
    val hours = (totalMinutes / 60) % 24
    val minutes = totalMinutes % 60

    fun fa(n: Long) = n.toString().toPersianDigits()

    return when {
        days > 0 && hours > 0 -> "${fa(days)} روز و ${fa(hours)} ساعت"
        days > 0 -> "${fa(days)} روز"
        hours > 0 && minutes > 0 -> "${fa(hours)} ساعت و ${fa(minutes)} دقیقه"
        hours > 0 -> "${fa(hours)} ساعت"
        else -> "${fa(minutes)} دقیقه"
    }
}

/** "چهارشنبه ۲۷ شهریور" — weekday and day carry the meaning; the year is
 *  noise on a reminder for something happening this week. */
private fun reminderDate(millis: Long): String {
    val parts = DateTimeUtils.getJalaliDateParts(millis)
    val month = DateTimeUtils.jalaliMonthName(parts.month - 1)
    return "${DateTimeUtils.getDayOfWeekName(millis)} ${parts.dayOfMonth.toString().toPersianDigits()} $month"
}

private fun reminderShareText(
    business: Business?,
    visitorName: String,
    appointmentDate: Long,
): String = buildString {
    append("$visitorName عزیز، یادآوری نوبت شما")
    business?.let { append(" در «${it.title}»") }
    append(":\n")
    append("${reminderDate(appointmentDate)} ساعت ${DateTimeUtils.formatTime(appointmentDate).toPersianDigits()}")
    append("\n(${remainingUntil(appointmentDate)} دیگر)")
}

/**
 * Preview + share sheet for one appointment's reminder card.
 *
 * A dialog rather than a route: it is opened from a row in a list, and the
 * owner's place in that list is the context they come back to. It is also why
 * the card is previewed at all instead of being exported straight from the
 * menu — the owner is about to send this to a named customer, and should see
 * what they are sending.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReminderCardDialog(
    visitorName: String,
    appointmentDate: Long,
    services: String?,
    onDismiss: () -> Unit,
) {
    val business by BusinessStateHolder.selectedBusiness.collectAsState()
    val capture = rememberCaptureController()
    val scope = rememberCoroutineScope()
    var exporting by remember { mutableStateOf(false) }

    // A bottom sheet, not a dialog: this is opened from a row in a list and
    // is a step in that flow rather than an interruption of it — it slides up
    // from where the tap happened, and a drag down is the same "not now" the
    // owner would have expressed by tapping outside a dialog.
    AdaptiveSheet(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface,
        // Straight to fully expanded, and scrollable inside: the card is a
        // 4:5 image and the two buttons sit under it, which is taller than
        // the half-height a sheet opens at by default — the owner would have
        // had to drag the sheet up before they could reach "share".
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(bottom = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            ExportableCard(controller = capture, designHeightDp = POSTER_HEIGHT_DP) {
                ReminderPosterCard(
                    business = business,
                    visitorName = visitorName,
                    appointmentDate = appointmentDate,
                    services = services,
                )
            }

            Spacer(Modifier.height(16.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                AppButton(
                    text = "انصراف",
                    onClick = onDismiss,
                    isOutlined = true,
                    modifier = Modifier.weight(1f),
                )
                AppButton(
                    text = "اشتراک‌گذاری تصویر",
                    isLoading = exporting,
                    onClick = {
                        exporting = true
                        scope.launch {
                            shareImage(
                                bytes = encodePng(capture.capture()),
                                fileName = "noobatyar-reminder-$appointmentDate.png",
                                caption = reminderShareText(business, visitorName, appointmentDate),
                            )
                            exporting = false
                            onDismiss()
                        }
                    },
                    modifier = Modifier.weight(1.4f),
                )
            }
        }
    }
}

@Composable
private fun ReminderPosterCard(
    business: Business?,
    visitorName: String,
    appointmentDate: Long,
    services: String?,
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Brush.linearGradient(listOf(ShareViolet, ShareVioletDeep))),
    ) {
        CardBackdrop(Modifier.fillMaxSize())

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(start = 24.dp, end = 24.dp, top = 16.dp, bottom = 18.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            if (business != null) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    BusinessAvatar(business, size = 34, ring = true)
                    Spacer(Modifier.width(9.dp))
                    Text(
                        text = business.title,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        maxLines = 1,
                        modifier = Modifier.weight(1f),
                    )
                }
            }

            Spacer(Modifier.weight(1f))

            NeonGlass(shape = RoundedCornerShape(20.dp), glowRadius = 8.dp, glowWidth = 2.dp) {
                Text(
                    text = "یادآوری نوبت",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
                )
            }

            Spacer(Modifier.height(10.dp))

            // The customer's own name, at the top of the card and larger than
            // anything else on it: this is sent to one person, and a reminder
            // that opens with the recipient's name is read, while one that
            // opens with the business's name looks like an advertisement.
            Text(
                text = visitorName,
                fontSize = 20.sp,
                lineHeight = 26.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                textAlign = TextAlign.Center,
                maxLines = 2,
            )
            Spacer(Modifier.height(14.dp))

            NeonGlass(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                glowRadius = 12.dp,
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    ReminderFact(
                        icon = Icons.Rounded.CalendarMonth,
                        label = "تاریخ",
                        value = reminderDate(appointmentDate),
                        modifier = Modifier.weight(1f),
                    )
                    Box(
                        Modifier
                            .width(1.dp)
                            .height(38.dp)
                            .background(Color.White.copy(alpha = 0.25f))
                    )
                    ReminderFact(
                        icon = Icons.Rounded.Schedule,
                        label = "ساعت",
                        value = DateTimeUtils.formatTime(appointmentDate).toPersianDigits(),
                        modifier = Modifier.weight(1f),
                    )
                }
            }

            if (!services.isNullOrBlank()) {
                Spacer(Modifier.height(8.dp))
                Text(
                    text = services.split(",").joinToString(" • ") { it.trim() },
                    fontSize = 12.sp,
                    lineHeight = 20.sp,
                    color = Color.White.copy(alpha = 0.85f),
                    textAlign = TextAlign.Center,
                    maxLines = 2,
                )
            }

            Spacer(Modifier.height(10.dp))

            // The countdown gets the solid panel and the darkest text on the
            // card. "How long do I have" is the one thing the customer opens
            // this to check; everything above it is what they already know.
            NeonHalo(shape = RoundedCornerShape(22.dp), modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color.White, RoundedCornerShape(22.dp))
                        .padding(vertical = 11.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(
                        text = remainingUntil(appointmentDate),
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        color = ShareVioletDeep,
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = "تا نوبت شما باقی مانده",
                        fontSize = 12.sp,
                        color = ShareVioletDark.copy(alpha = 0.75f),
                    )
                }
            }

            Spacer(Modifier.weight(1f))

            // The address gets the card's full width — squeezed beside the
            // signature it broke after "طبقه" and left "سوم" alone on a line,
            // which is the kind of detail that makes a generated image look
            // generated. The mark then sits on its own line below it, in the
            // corner the eye reaches last.
            if (business?.address?.isNotBlank() == true) {
                Text(
                    text = business.address,
                    fontSize = 12.sp,
                    lineHeight = 19.sp,
                    fontWeight = FontWeight.Medium,
                    color = Color.White.copy(alpha = 0.95f),
                    textAlign = TextAlign.Center,
                    // Readable at a glance, unlike the 11sp at 75% alpha this
                    // replaced: the date and time are also in the chat this
                    // image arrives in, but the address is the line a customer
                    // opens the picture again at the door to read.
                    maxLines = 2,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(8.dp))
            }

            // AbsoluteAlignment, not Start: the card is RTL, where Start is
            // the right-hand edge — this mark belongs on the physical left.
            Box(modifier = Modifier.fillMaxWidth()) {
                Box(Modifier.align(AbsoluteAlignment.CenterLeft)) {
                    AppSignature(onDark = true)
                }
            }
        }
    }
}

@Composable
private fun ReminderFact(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    value: String,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                icon,
                contentDescription = null,
                tint = Color.White.copy(alpha = 0.8f),
                modifier = Modifier.size(14.dp),
            )
            Spacer(Modifier.width(4.dp))
            Text(text = label, fontSize = 11.sp, color = Color.White.copy(alpha = 0.8f))
        }
        Spacer(Modifier.height(5.dp))
        Text(
            text = value,
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
            color = Color.White,
            textAlign = TextAlign.Center,
            maxLines = 1,
        )
    }
}
