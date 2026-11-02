package xyz.sattar.javid.proqueue.core.ui.icons

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.VectorGroup
import androidx.compose.ui.graphics.vector.VectorPath

/**
 * The same icon with its shapes filled in as well as stroked: Lucide draws
 * outlines only, and a saved bookmark or a rated star is read as "on" by
 * being solid. Tinting still comes from the Icon's `tint`.
 */
@Composable
fun ImageVector.filled(): ImageVector = remember(this) {
    val source = this
    ImageVector.Builder(
        name = source.name + "-filled",
        defaultWidth = source.defaultWidth,
        defaultHeight = source.defaultHeight,
        viewportWidth = source.viewportWidth,
        viewportHeight = source.viewportHeight,
    ).apply {
        fun add(group: VectorGroup) {
            for (node in group) {
                when (node) {
                    is VectorGroup -> add(node)
                    is VectorPath -> addPath(
                        pathData = node.pathData,
                        pathFillType = node.pathFillType,
                        name = node.name,
                        fill = SolidColor(androidx.compose.ui.graphics.Color.Black),
                        fillAlpha = 1f,
                        stroke = node.stroke,
                        strokeAlpha = node.strokeAlpha,
                        strokeLineWidth = node.strokeLineWidth,
                        strokeLineCap = node.strokeLineCap,
                        strokeLineJoin = node.strokeLineJoin,
                        strokeLineMiter = node.strokeLineMiter,
                    )
                }
            }
        }
        add(source.root)
    }.build()
}
