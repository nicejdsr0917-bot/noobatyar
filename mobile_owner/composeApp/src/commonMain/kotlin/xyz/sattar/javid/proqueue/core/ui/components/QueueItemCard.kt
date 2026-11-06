package xyz.sattar.javid.proqueue.core.ui.components

import xyz.sattar.javid.proqueue.core.i18n.Str
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Message
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.rounded.Call
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import org.jetbrains.compose.resources.painterResource
import proqueue.composeapp.generated.resources.Res
import proqueue.composeapp.generated.resources.whatsapp
import xyz.sattar.javid.proqueue.core.state.BusinessStateHolder
import xyz.sattar.javid.proqueue.core.utils.DateTimeUtils
import xyz.sattar.javid.proqueue.core.utils.formatPhoneNumberForAction
import xyz.sattar.javid.proqueue.core.utils.openPhoneDial
import xyz.sattar.javid.proqueue.core.utils.openSms
import xyz.sattar.javid.proqueue.core.utils.openTelegram
import xyz.sattar.javid.proqueue.core.utils.openWhatsApp
import xyz.sattar.javid.proqueue.feature.home.QueueItem
import xyz.sattar.javid.proqueue.feature.share.ReminderCardDialog

@Composable
fun QueueItemCard(
    item: QueueItem,
    onRemove: () -> Unit,
    onComplete: () -> Unit,
    onNoShow: () -> Unit,
    onSendMessage: (appointmentId: Long, type: String, content: String, businessTitle: String) -> Unit,
    onItemClick: () -> Unit = {},
    onGenerateMessage: (Long, String, String, String, Long, String, Int?) -> String
) {
    val isDark = !MaterialTheme.colorScheme.surface.let { color ->
        (color.red * 0.299 + color.green * 0.587 + color.blue * 0.114) > 0.5
    }
    val isPending = item.appointment.status == "PENDING_APPROVAL" || item.appointment.status == "PENDING_VERIFICATION"
    var showReminderCard by remember { mutableStateOf(false) }
    val isPendingVerification = item.appointment.status == "PENDING_VERIFICATION"

    // Build the reminder message once and dispatch it through the chosen channel.
    val sendVia: (String, (String, String) -> Unit) -> Unit = { channel, open ->
        val business = BusinessStateHolder.selectedBusiness.value
        val businessTitle = business?.title ?: "--"
        val businessAddress = business?.address ?: "--"
        val message = onGenerateMessage(
            item.appointment.businessId,
            businessTitle,
            businessAddress,
            item.visitorName,
            item.appointment.appointmentDate,
            item.waitingText,
            item.appointment.serviceDuration,
        )
        open(formatPhoneNumberForAction(item.visitorPhone), message)
        onSendMessage(item.appointment.id, channel, message, businessTitle)
    }

    Card(
        onClick = onItemClick,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer
        ),
        border = CardDefaults.outlinedCardBorder().copy(
            brush = androidx.compose.ui.graphics.SolidColor(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        // Three bands, each on its own line so nothing competes for width:
        // who (name + ⋮ menu), when (time and how long until it), and — last,
        // full width — the one thing to do with this booking. Squeezing the
        // action button onto the name's line left the name, the menu and the
        // button fighting over one row.
        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp)) {
            val startTimeOnly = DateTimeUtils.formatTime(item.estimatedStartTime)
            val endTimeOnly = DateTimeUtils.formatTime(item.estimatedEndTime)
            val todayStart = DateTimeUtils.startOfTodayMillis()
            val isToday = item.estimatedStartTime in todayStart until todayStart + 24L * 60 * 60 * 1000

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = item.visitorName,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false)
                )
                SourceBadge(item.appointment.source, modifier = Modifier.padding(horizontal = 8.dp))
                FirstVisitBadge(item.appointment.firstVisitDiscount)
                Spacer(modifier = Modifier.weight(1f))

                var showMenu by remember { mutableStateOf(false) }
                Box {
                    IconButton(onClick = { showMenu = true }, modifier = Modifier.size(36.dp)) {
                        Icon(
                            imageVector = Icons.Rounded.MoreVert,
                            contentDescription = Str.contact_options,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                        // --- Contact the visitor ---
                        DropdownMenuItem(
                            text = { Text(Str.sms) },
                            leadingIcon = { Icon(Icons.AutoMirrored.Rounded.Message, contentDescription = null) },
                            onClick = {
                                showMenu = false
                                sendVia("SMS", ::openSms)
                            }
                        )
                        DropdownMenuItem(
                            text = { Text(Str.whatsapp) },
                            leadingIcon = { Icon(painterResource(Res.drawable.whatsapp), contentDescription = null) },
                            onClick = {
                                showMenu = false
                                sendVia("WHATSAPP", ::openWhatsApp)
                            }
                        )
                        DropdownMenuItem(
                            text = { Text(Str.telegram) },
                            leadingIcon = { Icon(Icons.AutoMirrored.Rounded.Send, contentDescription = null) },
                            onClick = {
                                showMenu = false
                                sendVia("TELEGRAM", ::openTelegram)
                            }
                        )
                        DropdownMenuItem(
                            text = { Text(Str.phone_call) },
                            leadingIcon = { Icon(Icons.Rounded.Call, contentDescription = null) },
                            trailingIcon = {
                                Text(
                                    text = item.visitorPhone,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            },
                            onClick = {
                                showMenu = false
                                openPhoneDial(item.visitorPhone)
                            }
                        )
                        // The same reminder the three entries above send as
                        // text, as a picture instead — see ReminderCardDialog
                        // for why that is worth a separate entry rather than
                        // an option inside one of them.
                        DropdownMenuItem(
                            text = { Text(Str.reminder_card) },
                            leadingIcon = { Icon(Icons.Rounded.Image, contentDescription = null) },
                            onClick = {
                                showMenu = false
                                showReminderCard = true
                            }
                        )

                        // --- Destructive / secondary status actions ---
                        HorizontalDivider()
                        if (isPending) {
                            DropdownMenuItem(
                                text = { Text(Str.reject_action, color = MaterialTheme.colorScheme.error) },
                                leadingIcon = { Icon(Icons.Rounded.Close, contentDescription = null, tint = MaterialTheme.colorScheme.error) },
                                onClick = {
                                    showMenu = false
                                    onNoShow()
                                }
                            )
                        } else {
                            DropdownMenuItem(
                                text = { Text(Str.no_show_action, color = MaterialTheme.colorScheme.error) },
                                leadingIcon = { Icon(Icons.Rounded.Close, contentDescription = null, tint = MaterialTheme.colorScheme.error) },
                                onClick = {
                                    showMenu = false
                                    onNoShow()
                                }
                            )
                            DropdownMenuItem(
                                text = { Text(Str.delete_appointment, color = MaterialTheme.colorScheme.error) },
                                leadingIcon = { Icon(Icons.Rounded.Delete, contentDescription = null, tint = MaterialTheme.colorScheme.error) },
                                onClick = {
                                    showMenu = false
                                    onRemove()
                                }
                            )
                        }
                    }
                }

            }

            Spacer(modifier = Modifier.height(6.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = "$endTimeOnly ${Str.to_label} $startTimeOnly",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                    maxLines = 1
                )
                if (!isToday) {
                    Text(
                        text = DateTimeUtils.getJalaliDate(item.estimatedStartTime),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1
                    )
                }

                Spacer(modifier = Modifier.weight(1f))

                val badgeBgColor = when {
                    item.overdue -> if (isDark) Color(0xFFB71C1C).copy(alpha = 0.4f) else Color(0xFFFFEBEE)
                    else -> MaterialTheme.colorScheme.secondaryContainer.copy(alpha = if (isDark) 0.4f else 0.7f)
                }
                val badgeContentColor = when {
                    item.overdue -> if (isDark) Color(0xFFEF9A9A) else Color(0xFFC62828)
                    else -> MaterialTheme.colorScheme.onSecondaryContainer
                }
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = badgeBgColor
                ) {
                    Text(
                        text = item.waitingText,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = badgeContentColor,
                        maxLines = 1
                    )
                }
            }

            // Medical guilds: the insurance the client will use, and the
            // deposit's tracking code to check against the bank, each shown
            // only when the booking has one.
            val insuranceName = item.appointment.insuranceName?.takeIf { it.isNotBlank() }
            val trackingCode = item.appointment.paymentTrackingCode()
            if (insuranceName != null || trackingCode != null) {
                Spacer(modifier = Modifier.height(10.dp))
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    InsuranceLine(insuranceName)
                    PaymentTrackingLine(trackingCode)
                }
            }

            // Folded to one line; see CollapsibleReceipt.
            item.appointment.paymentReceipt?.let { receipt ->
                Spacer(modifier = Modifier.height(12.dp))
                CollapsibleReceipt(receipt = receipt)
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Primary action — the one thing an owner does most on a queue
            // row — on its own line, full width, where the thumb lands.
            FilledTonalButton(
                onClick = onComplete,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth().height(42.dp),
            ) {
                Icon(
                    imageVector = Icons.Rounded.Check,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = when {
                        isPendingVerification -> Str.verify_receipt
                        isPending -> Str.approve_action
                        else -> Str.complete_action
                    },
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1
                )
            }
        }
    }

    if (showReminderCard) {
        ReminderCardDialog(
            visitorName = item.visitorName,
            // The booked time, not estimatedStartTime: the queue's estimate
            // shifts as the day runs late, and a reminder that quietly told
            // the customer a different hour than the one they booked would be
            // worse than no reminder.
            appointmentDate = item.appointment.appointmentDate,
            services = item.appointment.selectedServices,
            onDismiss = { showReminderCard = false },
        )
    }
}
