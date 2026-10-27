package xyz.sattar.javid.proqueue.feature.discovery

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

/**
 * Outlined tooth for the dental category. Material's icon set has none (its
 * nearest, Vaccines, is a syringe), so it is drawn here at the same 24-unit
 * grid and ~1.8 stroke as the Outlined family it sits next to.
 */
val ToothOutlined: ImageVector by lazy {
    ImageVector.Builder(
        name = "ToothOutlined",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f,
    ).apply {
        path(
            fill = null,
            stroke = SolidColor(Color.Black),
            strokeLineWidth = 1.8f,
            strokeLineCap = StrokeCap.Round,
            strokeLineJoin = StrokeJoin.Round,
        ) {
            moveTo(7.6f, 3.2f)
            curveTo(5.2f, 3.2f, 3.6f, 5.1f, 3.6f, 7.6f)
            curveTo(3.6f, 10.0f, 4.6f, 11.6f, 5.1f, 13.6f)
            curveTo(5.7f, 16.1f, 5.9f, 20.8f, 7.9f, 20.8f)
            curveTo(9.7f, 20.8f, 9.7f, 15.9f, 12.0f, 15.9f)
            curveTo(14.3f, 15.9f, 14.3f, 20.8f, 16.1f, 20.8f)
            curveTo(18.1f, 20.8f, 18.3f, 16.1f, 18.9f, 13.6f)
            curveTo(19.4f, 11.6f, 20.4f, 10.0f, 20.4f, 7.6f)
            curveTo(20.4f, 5.1f, 18.8f, 3.2f, 16.4f, 3.2f)
            curveTo(14.5f, 3.2f, 13.6f, 4.2f, 12.0f, 4.2f)
            curveTo(10.4f, 4.2f, 9.5f, 3.2f, 7.6f, 3.2f)
            close()
        }
    }.build()
}
