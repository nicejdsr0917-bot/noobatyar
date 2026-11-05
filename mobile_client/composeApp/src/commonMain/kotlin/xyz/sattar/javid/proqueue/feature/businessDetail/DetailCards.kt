package xyz.sattar.javid.proqueue.feature.businessDetail

import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import xyz.sattar.javid.proqueue.feature.booking.enterStagger
import xyz.sattar.javid.proqueue.feature.booking.pressScale
import xyz.sattar.javid.proqueue.feature.discovery.ChevronIcon
import xyz.sattar.javid.proqueue.feature.discovery.Space

/** Grouped-list cards: rounder than controls, so a section reads as one object. */
internal val CardShape = RoundedCornerShape(16.dp)

/**
 * One section of the page as an inset card on the grey page, the way native
 * grouped lists look. [bleed] content (horizontal rows) runs edge to edge
 * inside the card and pads itself, so its first item is never clipped.
 */
@Composable
internal fun GroupCard(
    title: String?,
    index: Int,
    modifier: Modifier = Modifier,
    trailing: (@Composable () -> Unit)? = null,
    bleed: (@Composable () -> Unit)? = null,
    content: (@Composable ColumnScope.() -> Unit)? = null,
) {
    Surface(
        shape = CardShape,
        color = MaterialTheme.colorScheme.surface,
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = Space.page)
            .enterStagger(index, stepMillis = 50),
    ) {
        Column(Modifier.padding(vertical = Space.lg)) {
            if (title != null) {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = Space.lg).heightIn(min = 28.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                    trailing?.invoke()
                }
                Spacer(Modifier.padding(top = Space.md))
            }
            bleed?.invoke()
            if (content != null) {
                Column(Modifier.fillMaxWidth().padding(horizontal = Space.lg), content = content)
            }
        }
    }
}

/** The padding a [GroupCard] bleed row gives its first and last item. */
internal val BleedPadding = PaddingValues(horizontal = Space.lg)

/** A tappable settings-style row: icon, text, optional value, chevron. */
@Composable
internal fun LinkRow(
    icon: ImageVector,
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    tint: Color = MaterialTheme.colorScheme.onSurfaceVariant,
    /** For a warning row (report), the title takes the same colour as the icon. */
    titleColor: Color = Color.Unspecified,
    onClick: (() -> Unit)?,
) {
    val interaction = remember { MutableInteractionSource() }
    val body: @Composable () -> Unit = {
        Row(
            Modifier.fillMaxWidth().heightIn(min = 52.dp).padding(horizontal = Space.lg, vertical = Space.sm),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(Space.md))
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.bodyMedium, color = titleColor)
                if (subtitle != null) {
                    Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            if (onClick != null) {
                Icon(ChevronIcon, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(20.dp))
            }
        }
    }
    if (onClick == null) {
        Column(modifier) { body() }
    } else {
        Surface(
            onClick = onClick,
            color = Color.Transparent,
            interactionSource = interaction,
            modifier = modifier.fillMaxWidth().pressScale(interaction, 0.985f),
        ) { body() }
    }
}
