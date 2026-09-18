package xyz.sattar.javid.proqueue.core.ui.components


import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import xyz.sattar.javid.proqueue.core.utils.toPersianDigits
import androidx.compose.ui.unit.dp
import xyz.sattar.javid.proqueue.core.utils.toEnglishDigits

@Composable
fun AppTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String? = null,
    maxLine: Int = 1,
    maxLength: Int = Int.MAX_VALUE,
    /** Shows a "used / allowed" count under the field. Only meaningful
     *  alongside [maxLength]: without a limit there is nothing to count
     *  towards, and the number would just be noise. */
    showCounter: Boolean = false,
    modifier: Modifier = Modifier,
    isError: Boolean = false,
    errorMessage: String? = null,
    keyboardType: KeyboardType = KeyboardType.Text,
    leadingIcon: @Composable (() -> Unit)? = null,
    trailingIcon: @Composable (() -> Unit)? = null,
    readOnly: Boolean = false,
    onClick: (() -> Unit)? = null,
    enabled: Boolean = true,
    placeholder: String? = null,
    visualTransformation: VisualTransformation? = null,
) {
    val isPassword = keyboardType == KeyboardType.Password
    // Hidden by default on every mount, including a field the user already
    // toggled open — re-hiding a password field whenever its screen leaves
    // and comes back is the safer default. Ephemeral by design: nothing
    // downstream needs to know whether the dots are showing.
    var passwordVisible by remember { mutableStateOf(false) }
    val transformation = visualTransformation ?: when {
        !isPassword -> VisualTransformation.None
        passwordVisible -> VisualTransformation.None
        else -> PasswordVisualTransformation()
    }
    // A password field gets the show/hide toggle instead of the internal
    // clear button below — the two occupy the same slot, and "reveal what
    // you typed" already doubles as a way to check it before clearing by
    // hand. Only when the caller left both trailingIcon and
    // visualTransformation alone: a caller supplying its own transformation
    // is managing visibility itself.
    val effectiveTrailingIcon = if (isPassword && visualTransformation == null) {
        {
            IconButton(onClick = { passwordVisible = !passwordVisible }) {
                Icon(
                    imageVector = if (passwordVisible) Icons.Rounded.VisibilityOff else Icons.Rounded.Visibility,
                    contentDescription = if (passwordVisible) "پنهان کردن رمز عبور" else "نمایش رمز عبور",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    } else {
        trailingIcon
    }
    AppTextFieldInternal(
        value = value,
        onValueChange = onValueChange,
        label = label,
        maxLine = maxLine,
        maxLength = maxLength,
        showCounter = showCounter,
        modifier = modifier,
        isError = isError,
        errorMessage = errorMessage,
        keyboardType = keyboardType,
        leadingIcon = leadingIcon,
        trailingIcon = effectiveTrailingIcon,
        readOnly = readOnly,
        onClick = onClick,
        enabled = enabled,
        placeholder = placeholder,
        visualTransformation = transformation,
        // The internal clear-button fallback only applies when this is false —
        // see AppTextFieldInternal's trailingIcon logic below.
        suppressClearButton = isPassword,
    )
}

@Composable
private fun AppTextFieldInternal(
    value: String,
    onValueChange: (String) -> Unit,
    label: String? = null,
    maxLine: Int = 1,
    maxLength: Int = Int.MAX_VALUE,
    /** Shows a "used / allowed" count under the field. Only meaningful
     *  alongside [maxLength]: without a limit there is nothing to count
     *  towards, and the number would just be noise. */
    showCounter: Boolean = false,
    modifier: Modifier = Modifier,
    isError: Boolean = false,
    errorMessage: String? = null,
    keyboardType: KeyboardType = KeyboardType.Text,
    leadingIcon: @Composable (() -> Unit)? = null,
    trailingIcon: @Composable (() -> Unit)? = null,
    readOnly: Boolean = false,
    onClick: (() -> Unit)? = null,
    enabled: Boolean,
    placeholder: String? = null,
    visualTransformation: VisualTransformation = VisualTransformation.None,
    /** True for a password field, where [trailingIcon] is already the
     *  show/hide toggle — the clear button below would otherwise silently
     *  replace it every time the field is non-empty, i.e. always. */
    suppressClearButton: Boolean = false,
) {
    Column(modifier = modifier) {
        if (label != null) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 8.dp)
            )
        }

        OutlinedTextField(
            value = value,
            onValueChange = { newValue ->
                // Truncated, not rejected: the old `if (newValue.length <=
                // maxLength)` guard dropped the entire edit whenever it pushed
                // the field over the limit — including a paste, which is how
                // it read as "paste doesn't work" on any capped field (bio at
                // 50 chars, for one). Pasting 40 chars into a field that
                // already had 20 (well under most people's idea of "too
                // long") silently did nothing. Truncating keeps as much of
                // the paste as fits, which is what every other text field
                // anywhere does.
                val limited = newValue.take(maxLength)
                val finalValue = if (
                    keyboardType == KeyboardType.Number ||
                    keyboardType == KeyboardType.NumberPassword ||
                    keyboardType == KeyboardType.Phone ||
                    keyboardType == KeyboardType.Decimal
                ) {
                    limited.toEnglishDigits()
                } else {
                    limited
                }
                onValueChange(finalValue)
            },
            placeholder = placeholder?.let { { Text(it) } },
            modifier = Modifier
                .fillMaxWidth()
                .then(
                    if (onClick != null) {
                        Modifier.clickable(onClick = onClick)
                    } else Modifier
            ),
            readOnly = readOnly,
            isError = isError,
            leadingIcon = leadingIcon,
            maxLines = maxLine,
            trailingIcon = if (!suppressClearButton && value.isNotEmpty() && !readOnly && enabled) {
                {
                    IconButton(
                        onClick = { onValueChange("") },
                        modifier = Modifier.size(25.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Close,
                            contentDescription = "Clear",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            } else {
                trailingIcon
            },
            keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = MaterialTheme.colorScheme.primary,
                unfocusedBorderColor = MaterialTheme.colorScheme.outline,
                errorBorderColor = MaterialTheme.colorScheme.error,
                focusedContainerColor = MaterialTheme.colorScheme.surface,
                unfocusedContainerColor = MaterialTheme.colorScheme.surface
            ),
            shape = RoundedCornerShape(12.dp),
            singleLine = maxLine == 1,
            enabled = enabled,
            visualTransformation = visualTransformation,
        )

        // Error and counter share one row so neither shifts the fields below
        // when it appears: the row is laid out whenever either is showing.
        if ((isError && errorMessage != null) || showCounter) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = if (isError && errorMessage != null) errorMessage else "",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.weight(1f).padding(start = 4.dp)
                )
                if (showCounter) {
                    Text(
                        text = "${value.length.toString().toPersianDigits()}" +
                                " / ${maxLength.toString().toPersianDigits()}",
                        style = MaterialTheme.typography.bodySmall,
                        // Turns the same colour as the field's own error state as
                        // the owner approaches the cap, so "you are near the end"
                        // is visible before the field silently stops accepting.
                        color = if (value.length >= maxLength) {
                            MaterialTheme.colorScheme.error
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                        modifier = Modifier.padding(end = 4.dp)
                    )
                }
            }
        }
    }
}
