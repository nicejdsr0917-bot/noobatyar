package xyz.sattar.javid.proqueue.feature.createBusiness

import xyz.sattar.javid.proqueue.core.i18n.Str
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AddCircleOutline
import androidx.compose.material.icons.rounded.Phone
import androidx.compose.material.icons.rounded.RemoveCircleOutline
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import xyz.sattar.javid.proqueue.core.ui.components.AppTextField

/**
 * A repeatable list of additional phone numbers, alongside the business's
 * single primary `phone` field elsewhere on this form.
 *
 * A plain `List<String>` rather than a richer per-entry model (label, type):
 * the ask was "more than one number", not a typed directory — validation on
 * each entry is identical to the primary phone (backend/business/serializers.py
 * `validate_extra_phones`), and a blank row is simply dropped on save rather
 * than blocking the rest of the form.
 */
@Composable
fun ExtraPhonesField(
    extraPhones: List<String>,
    onExtraPhones: (List<String>) -> Unit,
    enabled: Boolean,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        extraPhones.forEachIndexed { index, value ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                AppTextField(
                    enabled = enabled,
                    maxLength = 11,
                    value = value,
                    onValueChange = { updated ->
                        onExtraPhones(extraPhones.toMutableList().also { it[index] = updated })
                    },
                    label = Str.another_phone_number,
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Rounded.Phone,
                            contentDescription = null,
                            modifier = Modifier.size(24.dp)
                        )
                    },
                    modifier = Modifier.weight(1f),
                    keyboardType = KeyboardType.Phone
                )
                IconButton(
                    enabled = enabled,
                    onClick = { onExtraPhones(extraPhones.toMutableList().also { it.removeAt(index) }) }
                ) {
                    Icon(
                        imageVector = Icons.Rounded.RemoveCircleOutline,
                        contentDescription = Str.remove_number,
                        tint = MaterialTheme.colorScheme.error
                    )
                }
            }
        }

        TextButton(
            enabled = enabled,
            onClick = { onExtraPhones(extraPhones + "") }
        ) {
            Icon(
                imageVector = Icons.Rounded.AddCircleOutline,
                contentDescription = null,
                modifier = Modifier.size(18.dp)
            )
            Text(
                text = Str.add_another_phone_number,
                style = MaterialTheme.typography.labelLarge,
                modifier = Modifier
            )
        }
    }
}
