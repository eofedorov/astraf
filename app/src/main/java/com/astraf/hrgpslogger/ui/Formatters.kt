package com.astraf.hrgpslogger.ui

import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.roundToInt

private val timeFormatter = DateTimeFormatter.ofPattern("HH:mm:ss", Locale.getDefault())
private val dateTimeFormatter = DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm", Locale.getDefault())

fun formatDuration(millis: Long): String {
    val totalSeconds = (millis / 1000).coerceAtLeast(0)
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60
    return if (hours > 0) {
        String.format(Locale.getDefault(), "%d:%02d:%02d", hours, minutes, seconds)
    } else {
        String.format(Locale.getDefault(), "%02d:%02d", minutes, seconds)
    }
}

fun formatSpeedKmh(speed: Float?): String =
    speed?.let { "${"%.1f".format(Locale.getDefault(), it)} км/ч" } ?: "—"

fun formatSpeedKmhNumber(speed: Float?): String =
    speed?.let { "%.1f".format(Locale.getDefault(), it) } ?: "—"

fun formatDistanceMeters(meters: Double): String {
    return if (meters >= 1000.0) {
        "${"%.2f".format(Locale.getDefault(), meters / 1000.0)} км"
    } else {
        "${meters.roundToInt()} м"
    }
}

fun formatDistanceNumber(meters: Double): String =
    if (meters >= 1000.0) {
        "%.2f".format(Locale.getDefault(), meters / 1000.0)
    } else {
        meters.roundToInt().toString()
    }

fun formatDistanceUnit(meters: Double): String =
    if (meters >= 1000.0) "км" else "м"

fun formatCurrentTime(): String =
    timeFormatter.format(Instant.now().atZone(ZoneId.systemDefault()))

fun formatTrackDateTime(millis: Long): String =
    dateTimeFormatter.format(Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()))

fun formatHeartRateCompact(bpm: Int?): String =
    if (bpm != null) "❤️$bpm" else "❤️—"

fun formatDistanceKmShort(meters: Double): String {
    return if (meters >= 1000.0) {
        "${"%.1f".format(Locale.getDefault(), meters / 1000.0)} km"
    } else {
        "${meters.roundToInt()} m"
    }
}

fun formatAvgSpeedShort(speedKmh: Float?): String =
    speedKmh?.let { "${"%.1f".format(Locale.getDefault(), it)} avg" } ?: "— avg"

fun buildRideCompactMetricsLine(
    currentSpeedKmh: Float?,
    heartRateBpm: Int?,
    distanceMeters: Double,
    averageSpeedKmh: Float?,
    durationMillis: Long,
): String = listOf(
    formatSpeedKmh(currentSpeedKmh),
    formatHeartRateCompact(heartRateBpm),
    formatDistanceKmShort(distanceMeters),
    formatAvgSpeedShort(averageSpeedKmh),
    formatDuration(durationMillis),
).joinToString(" | ")

fun buildGpsStatusLine(
    waitingForGps: Boolean,
    rawAccuracyMeters: Float?,
    debugLine: String,
): String {
    if (waitingForGps) {
        val accuracy = rawAccuracyMeters?.let { "±${it.roundToInt()} м" } ?: "нет fix"
        return "Ожидание GPS ($accuracy) · $debugLine"
    }
    return debugLine
}

fun buildGpsDebugLine(
    raw: Int,
    accepted: Int,
    rejected: Int,
    segments: Int,
): String = "GPS: $accepted/$raw acc, rej $rejected, seg $segments"
