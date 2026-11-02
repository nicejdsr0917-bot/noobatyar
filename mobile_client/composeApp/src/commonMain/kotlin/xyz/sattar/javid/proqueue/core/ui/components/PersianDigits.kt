package xyz.sattar.javid.proqueue.core.ui.components

import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import xyz.sattar.javid.proqueue.core.utils.toPersianDigits

/**
 * Shows the digits of a numeric field as Persian ones, like the rest of the
 * app, while the field still holds (and sends) what was typed. One character
 * in, one out, so the cursor needs no mapping.
 */
val PersianDigits = VisualTransformation { text ->
    TransformedText(AnnotatedString(text.text.toPersianDigits()), OffsetMapping.Identity)
}
