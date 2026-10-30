package xyz.sattar.javid.proqueue.core.location

import xyz.sattar.javid.proqueue.feature.discovery.toPersianDigits
import kotlin.math.max
import kotlin.math.roundToInt

/** City streets wind: a drive is about this much longer than the straight line. */
const val ROAD_FACTOR = 1.35

/** Average speed across town in ordinary traffic. */
const val CITY_SPEED_KMH = 25.0

/** The estimate is a range this wide: traffic decides where in it the trip lands. */
const val DRIVE_RANGE_MINUTES = 30

/**
 * A rough drive, from the straight-line distance alone (no routing service:
 * none answers reliably from Iran without a key). The low end assumes clear
 * roads, the high end is [DRIVE_RANGE_MINUTES] later.
 */
data class DriveEstimate(val roadKm: Double, val minMinutes: Int, val maxMinutes: Int)

fun driveEstimate(straightKm: Double): DriveEstimate {
    val roadKm = straightKm * ROAD_FACTOR
    val typical = roadKm / CITY_SPEED_KMH * 60.0
    // Clear roads take about ten minutes off a typical trip; never under five.
    val low = max(5, roundToFive(typical - 10.0))
    return DriveEstimate(roadKm, low, low + DRIVE_RANGE_MINUTES)
}

private fun roundToFive(minutes: Double): Int = (minutes / 5.0).roundToInt() * 5

/** «حدود ۲۰ تا ۵۰ دقیقه با ماشین» */
fun DriveEstimate.label(): String =
    "حدود ${minMinutes.toPersianDigits()} تا ${maxMinutes.toPersianDigits()} دقیقه با ماشین"
