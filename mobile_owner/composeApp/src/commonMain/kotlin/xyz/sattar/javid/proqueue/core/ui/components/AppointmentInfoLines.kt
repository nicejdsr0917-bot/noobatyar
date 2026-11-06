package xyz.sattar.javid.proqueue.core.ui.components

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ConfirmationNumber
import androidx.compose.material.icons.rounded.HealthAndSafety
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import xyz.sattar.javid.proqueue.core.i18n.Str
import xyz.sattar.javid.proqueue.domain.model.appointment.Appointment

/**
 * The deposit's tracking code: what the client typed for a card-to-card
 * payment, or the gateway's trackId. Null when no deposit was paid, so a
 * trackId left on an abandoned gateway attempt is never shown as a payment.
 */
fun Appointment.paymentTrackingCode(): String? =
    paymentReference?.trim()?.takeIf {
        it.isNotEmpty() && (depositPaymentMethod == "CARD" || depositPaymentMethod == "GATEWAY")
    }

/** «بیمهٔ تکمیلی: …» — draws nothing when the client picked none. */
@Composable
fun InsuranceLine(name: String?, modifier: Modifier = Modifier) {
    val value = name?.trim()?.takeIf { it.isNotEmpty() } ?: return
    InfoLine(Icons.Rounded.HealthAndSafety, "${Str.supplementary_insurance_label}: $value", modifier)
}

/** «کد پیگیری پرداخت: …», selectable so the owner can copy it into their bank app. */
@Composable
fun PaymentTrackingLine(code: String?, modifier: Modifier = Modifier) {
    code ?: return
    SelectionContainer(modifier = modifier) {
        InfoLine(Icons.Rounded.ConfirmationNumber, "${Str.payment_tracking_label}: $code")
    }
}

@Composable
private fun InfoLine(icon: ImageVector, text: String, modifier: Modifier = Modifier) {
    Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            modifier = Modifier.size(14.dp),
            tint = MaterialTheme.colorScheme.primary
        )
        Spacer(modifier = Modifier.width(6.dp))
        Text(
            text = text,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
    }
}
