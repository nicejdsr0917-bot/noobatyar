package xyz.sattar.javid.proqueue.core.navigation.navHost

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.unit.dp
import xyz.sattar.javid.proqueue.core.navigation.MainTab

/**
 * The solid look of a selected tab: Lucide has outlines only, so the same icon
 * is drawn over a fill of its own shape, and its inner details (compass needle,
 * calendar days, the person) are drawn back on top in [cutout] so they still read.
 */
@Composable
internal fun SolidTabIcon(tab: MainTab, color: Color, cutout: Color, modifier: Modifier = Modifier) {
    val (fill, details) = solidLayers(tab)
    Box(modifier.size(24.dp)) {
        Icon(fill, null, tint = color, modifier = Modifier.matchParentSize())
        Icon(tab.iconSelected, null, tint = color, modifier = Modifier.matchParentSize())
        details?.let { Icon(it, null, tint = cutout, modifier = Modifier.matchParentSize()) }
    }
}

private fun solidLayers(tab: MainTab): Pair<ImageVector, ImageVector?> = when (tab) {
    MainTab.Explore -> compassFill to compassNeedle
    MainTab.Appointments -> calendarFill to calendarDays
    MainTab.Saved -> bookmarkFill to null
    MainTab.More -> userFill to userCutout
}

// Path data follows the Lucide 24×24 originals of Compass, CalendarDays, Bookmark and CircleUserRound.
private val compassFill = vector("compass-fill") { fill("M2 12a10 10 0 1 0 20 0a10 10 0 1 0 -20 0z") }
private val compassNeedle = vector("compass-needle") {
    val needle = "m16.24 7.76-1.804 5.411a2 2 0 0 1-1.265 1.265L7.76 16.24l1.804-5.411a2 2 0 0 1 1.265-1.265z"
    fill(needle)
    stroke(needle)
}
private val calendarFill = vector("calendar-fill") {
    fill("M5 4h14a2 2 0 0 1 2 2v14a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2V6a2 2 0 0 1 2-2z")
}
private val calendarDays = vector("calendar-days") {
    stroke("M5 10h14M8 14h.01M12 14h.01M16 14h.01M8 18h.01M12 18h.01M16 18h.01")
}
private val bookmarkFill = vector("bookmark-fill") { fill("m19 21-7-4-7 4V5a2 2 0 0 1 2-2h10a2 2 0 0 1 2 2v16z") }

private val userFill = vector("user-fill") { fill("M2 12a10 10 0 1 0 20 0a10 10 0 1 0 -20 0z") }
private val userCutout = vector("user-cutout") {
    fill("M8 10a4 4 0 1 0 8 0a4 4 0 1 0 -8 0z")
    fill("M7 19a2 2 0 0 1 2-2h6a2 2 0 0 1 2 2v.6A9 9 0 0 1 7 19.6z")
}

private fun vector(name: String, build: ImageVector.Builder.() -> Unit): ImageVector =
    ImageVector.Builder(name, 24.dp, 24.dp, 24f, 24f).apply(build).build()

private fun ImageVector.Builder.fill(d: String) {
    addPath(addPathNodes(d), fill = SolidColor(Color.Black))
}

private fun ImageVector.Builder.stroke(d: String) {
    addPath(
        addPathNodes(d),
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round,
    )
}
