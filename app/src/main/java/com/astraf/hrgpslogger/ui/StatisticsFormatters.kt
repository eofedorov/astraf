package com.astraf.hrgpslogger.ui

import java.util.Locale
import kotlin.math.roundToInt

fun formatStatsDistance(meters: Double): Pair<String, String> {
    val km = meters / 1000.0
    return if (km < 100.0) {
        String.format(Locale.getDefault(), "%.1f", km) to "км"
    } else {
        String.format(Locale.getDefault(), "%.0f", km) to "км"
    }
}

fun formatStatsMovingTime(millis: Long): String {
    val totalMinutes = (millis / 60_000).coerceAtLeast(0)
    val hours = totalMinutes / 60
    val minutes = totalMinutes % 60
    return if (hours > 0) {
        "$hours ч $minutes мин"
    } else {
        "$minutes мин"
    }
}

fun formatStatsRideCount(count: Int): Pair<String, String> =
    count.toString() to pluralRides(count)

fun formatStatsClimbMeters(meters: Float): Pair<String, String> =
    meters.roundToInt().toString() to "м"

fun formatStatsSpeedKmh(speed: Float?): Pair<String, String> {
    if (speed == null) return "—" to ""
    return String.format(Locale.getDefault(), "%.1f", speed) to "км/ч"
}

fun formatStatsTooltipDistance(meters: Double): String {
    val km = meters / 1000.0
    return if (km < 100.0) {
        String.format(Locale.getDefault(), "%.1f км", km)
    } else {
        String.format(Locale.getDefault(), "%.0f км", km)
    }
}

fun formatStatsTooltipRideCount(count: Int): String = when {
    count % 10 == 1 && count % 100 != 11 -> "$count поездка"
    count % 10 in 2..4 && count % 100 !in 12..14 -> "$count поездки"
    else -> "$count поездок"
}

private fun pluralRides(count: Int): String = when {
    count % 10 == 1 && count % 100 != 11 -> "поездка"
    count % 10 in 2..4 && count % 100 !in 12..14 -> "поездки"
    else -> "поездок"
}
