package xyz.sattar.javid.proqueue.core.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import xyz.sattar.javid.proqueue.core.ui.LocalWindowSize
import xyz.sattar.javid.proqueue.core.ui.WindowSize

/*
 * Adaptive building blocks for screens that must read well on a phone and on
 * a desktop browser from ONE layout.
 *
 * The web branch's first pass split each screen into a PhoneContent and a
 * WebContent copy. That doubles every later change and the two copies drift,
 * so screens now describe their content once, as blocks, and these helpers
 * decide the arrangement: stacked on Compact, side by side from Medium up.
 */

/** True from [WindowSize.Medium] up — anywhere a second column fits. */
@Composable
fun isWideLayout(): Boolean = LocalWindowSize.current != WindowSize.Compact

/** Horizontal page padding: phones keep their edge-to-edge 16dp, wide
 *  windows get more air so blocks don't touch the sidebar. */
@Composable
fun adaptivePagePadding(): Dp = when (LocalWindowSize.current) {
    WindowSize.Compact -> 16.dp
    WindowSize.Medium -> 20.dp
    WindowSize.Expanded -> 28.dp
}

/**
 * Two columns on a wide window, one stacked column on a phone.
 *
 * [start] comes first in reading order, so on a phone it is on top, and under
 * the app's forced RTL it is the right-hand column on desktop. Put the
 * primary content there.
 */
@Composable
fun AdaptiveColumns(
    modifier: Modifier = Modifier,
    startWeight: Float = 1f,
    endWeight: Float = 1f,
    spacing: Dp = 16.dp,
    start: @Composable ColumnScope.() -> Unit,
    end: @Composable ColumnScope.() -> Unit
) {
    if (isWideLayout()) {
        Row(
            modifier = modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(spacing),
            verticalAlignment = Alignment.Top
        ) {
            Column(Modifier.weight(startWeight), verticalArrangement = Arrangement.spacedBy(spacing), content = start)
            Column(Modifier.weight(endWeight), verticalArrangement = Arrangement.spacedBy(spacing), content = end)
        }
    } else {
        Column(
            modifier = modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(spacing)
        ) {
            start()
            end()
        }
    }
}

/**
 * Lays [count] equally sized cells out in rows. One column per row on
 * Compact unless [compactColumns] says otherwise, [mediumColumns] and
 * [expandedColumns] above that. Rows are filled right-to-left under RTL, and
 * a short last row keeps cell width instead of stretching.
 */
@Composable
fun AdaptiveGrid(
    count: Int,
    modifier: Modifier = Modifier,
    compactColumns: Int = 1,
    mediumColumns: Int = 2,
    expandedColumns: Int = 3,
    spacing: Dp = 12.dp,
    cell: @Composable RowScope.(index: Int) -> Unit
) {
    val columns = when (LocalWindowSize.current) {
        WindowSize.Compact -> compactColumns
        WindowSize.Medium -> mediumColumns
        WindowSize.Expanded -> expandedColumns
    }.coerceAtLeast(1)
    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(spacing)) {
        (0 until count).chunked(columns).forEach { rowIndices ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(spacing),
                verticalAlignment = Alignment.Top
            ) {
                rowIndices.forEach { index ->
                    Box(Modifier.weight(1f)) { Row { cell(index) } }
                }
                repeat(columns - rowIndices.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}

/**
 * A titled block: the unit screens are divided into. Groups related controls
 * under one heading so a desktop page reads as a set of panels rather than
 * one long form, and a phone page as clearly separated sections.
 */
@Composable
fun BlockCard(
    title: String,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    subtitle: String? = null,
    trailing: (@Composable RowScope.() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (icon != null) {
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.14f)
                    ) {
                        Icon(
                            imageVector = icon,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(7.dp).size(18.dp)
                        )
                    }
                    Spacer(Modifier.width(10.dp))
                }
                Column(Modifier.weight(1f)) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    if (subtitle != null) {
                        Text(
                            text = subtitle,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                trailing?.invoke(this)
            }
            Spacer(Modifier.padding(top = 12.dp))
            content()
        }
    }
}
