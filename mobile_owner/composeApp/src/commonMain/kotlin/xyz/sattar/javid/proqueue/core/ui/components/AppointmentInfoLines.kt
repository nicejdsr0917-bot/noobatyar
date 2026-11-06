package xyz.sattar.javid.proqueue.core.ui.components

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import xyz.sattar.javid.proqueue.core.i18n.Str
import xyz.sattar.javid.proqueue.core.utils.mediaUrl
import xyz.sattar.javid.proqueue.domain.model.appointment.Appointment
import xyz.sattar.javid.proqueue.domain.model.business.BusinessCategory

/**
 * The deposit's tracking code: what the client typed for a card-to-card
 * payment, or the gateway's trackId. Null when no deposit was paid, so a
 * trackId left on an abandoned gateway attempt is never shown as a payment.
 */
fun Appointment.paymentTrackingCode(): String? =
    paymentReference?.trim()?.takeIf {
        it.isNotEmpty() && (depositPaymentMethod == "CARD" || depositPaymentMethod == "GATEWAY")
    }

/**
 * The supplementary insurance to show for this booking: only at a medical
 * business (doctor, dentist, health). A category this build does not know
 * is trusted to the server, which records an insurance only for those guilds.
 */
fun Appointment.visibleInsuranceName(businessCategory: String?): String? {
    val name = insuranceName?.trim()?.takeIf { it.isNotEmpty() } ?: return null
    val known = BusinessCategory.entries.firstOrNull { it.value == businessCategory }
    return if (known == null || known.group.takesInsurance) name else null
}

/**
 * The insurer's logo and name — «بیمه ملت» already says what it is, so no
 * label: the card stays one line shorter. Shield icon without a logo.
 */
@Composable
fun InsuranceLine(name: String?, logo: String?, modifier: Modifier = Modifier) {
    name ?: return
    Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically) {
        if (logo != null) {
            AsyncImage(
                model = mediaUrl(logo),
                contentDescription = Str.supplementary_insurance_label,
                modifier = Modifier.size(18.dp).clip(RoundedCornerShape(4.dp)),
                contentScale = ContentScale.Fit
            )
        } else {
            Icon(
                imageVector = Icons.Rounded.HealthAndSafety,
                contentDescription = null,
                modifier = Modifier.size(14.dp),
                tint = MaterialTheme.colorScheme.primary
            )
        }
        Spacer(modifier = Modifier.width(6.dp))
        InfoText(name)
    }
}

/** «پیگیری: …», the deposit's tracking code, selectable so the owner can copy it into their bank app. */
@Composable
fun PaymentTrackingLine(code: String?, modifier: Modifier = Modifier) {
    code ?: return
    SelectionContainer(modifier = modifier) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            InfoIcon(Icons.Rounded.ConfirmationNumber)
            Spacer(modifier = Modifier.width(6.dp))
            InfoText("${Str.payment_tracking_short}: $code")
        }
    }
}

@Composable
private fun InfoIcon(icon: ImageVector) {
    Icon(
        imageVector = icon,
        contentDescription = null,
        modifier = Modifier.size(14.dp),
        tint = MaterialTheme.colorScheme.primary
    )
}

@Composable
private fun InfoText(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis
    )
}
