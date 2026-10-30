package xyz.sattar.javid.proqueue.feature

import androidx.compose.ui.geometry.Offset
import xyz.sattar.javid.proqueue.core.ui.components.map.GeoPoint
import xyz.sattar.javid.proqueue.core.ui.components.map.distanceKmTo
import xyz.sattar.javid.proqueue.feature.explore.boxAround
import xyz.sattar.javid.proqueue.feature.explore.circlePoints
import xyz.sattar.javid.proqueue.feature.explore.clusterOnScreen
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ExploreMapCameraTest {

    @Test
    fun pinsCloserThanTheThresholdFoldTogether() {
        val points = listOf(
            1L to Offset(100f, 100f),
            2L to Offset(120f, 110f),
            3L to Offset(400f, 400f),
            4L to Offset(130f, 90f),
        )
        assertEquals(listOf(listOf(1L, 2L, 4L), listOf(3L)), clusterOnScreen(points, threshold = 44f))
    }

    @Test
    fun theSelectedPinNeverFoldsAway() {
        val points = listOf(1L to Offset(100f, 100f), 2L to Offset(110f, 100f), 3L to Offset(115f, 100f))
        val groups = clusterOnScreen(points, threshold = 44f, exempt = 2L)
        assertEquals(listOf(listOf(1L, 3L), listOf(2L)), groups)
    }

    @Test
    fun farApartPinsStayAlone() {
        val points = listOf(1L to Offset(0f, 0f), 2L to Offset(100f, 0f))
        assertEquals(listOf(listOf(1L), listOf(2L)), clusterOnScreen(points, threshold = 44f))
        assertTrue(clusterOnScreen(emptyList(), 44f).isEmpty())
    }

    @Test
    fun aLonePinGetsAMinimumSpanAroundIt() {
        val tehran = GeoPoint(35.70, 51.34)
        val box = boxAround(listOf(tehran), minSpanKm = 2.0)
        val west = GeoPoint(tehran.latitude, box.west)
        val east = GeoPoint(tehran.latitude, box.east)
        val south = GeoPoint(box.south, tehran.longitude)
        val north = GeoPoint(box.north, tehran.longitude)
        assertTrue(abs(west.distanceKmTo(east) - 2.0) < 0.05, "width ${west.distanceKmTo(east)}")
        assertTrue(abs(south.distanceKmTo(north) - 2.0) < 0.05, "height ${south.distanceKmTo(north)}")
    }

    @Test
    fun aWideSpreadKeepsItsOwnBounds() {
        val a = GeoPoint(35.60, 51.20)
        val b = GeoPoint(35.80, 51.50)
        val box = boxAround(listOf(a, b), minSpanKm = 2.0)
        assertEquals(51.20, box.west, 1e-9)
        assertEquals(51.50, box.east, 1e-9)
        assertEquals(35.60, box.south, 1e-9)
        assertEquals(35.80, box.north, 1e-9)
    }

    @Test
    fun theRadiusRingIsClosedAndAtTheRadius() {
        val c = GeoPoint(35.70, 51.34)
        val ring = circlePoints(c, 5.0)
        assertEquals(ring.first().latitude, ring.last().latitude, 1e-9)
        assertEquals(ring.first().longitude, ring.last().longitude, 1e-9)
        ring.forEach { assertTrue(abs(c.distanceKmTo(it) - 5.0) < 0.05, "${c.distanceKmTo(it)}") }
    }
}
