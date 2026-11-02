package xyz.sattar.javid.proqueue.core.location

import xyz.sattar.javid.proqueue.feature.discovery.toPersianDigits
import kotlin.math.max
import kotlin.math.roundToInt

/** City streets wind: a drive is about this much longer than the straight line. */
const val ROAD_FACTOR = 1.35

/** Average speed across town in ordinary traffic. */
const val CITY_SPEED_KMH = 25.0

/** The estimate is the typical drive give or take this many minutes: traffic decides where it lands. */
const val DRIVE_MARGIN_MINUTES = 10

/**
 * A rough drive, from the straight-line distance alone (no routing service:
 * none answers reliably from Iran without a key). The typical drive is the
 * middle of the range, [DRIVE_MARGIN_MINUTES] either side.
 */
data class DriveEstimate(val roadKm: Double, val minMinutes: Int, val maxMinutes: Int)

fun driveEstimate(straightKm: Double): DriveEstimate {
    val roadKm = straightKm * ROAD_FACTOR
    val typical = roadKm / CITY_SPEED_KMH * 60.0
    val middle = max(5, roundToFive(typical))
    return DriveEstimate(roadKm, max(5, middle - DRIVE_MARGIN_MINUTES), middle + DRIVE_MARGIN_MINUTES)
}

private fun roundToFive(minutes: Double): Int = (minutes / 5.0).roundToInt() * 5

/** «حدود ۲۰ تا ۴۰ دقیقه با ماشین» */
fun DriveEstimate.label(): String =
    "حدود ${minMinutes.toPersianDigits()} تا ${maxMinutes.toPersianDigits()} دقیقه با ماشین"
