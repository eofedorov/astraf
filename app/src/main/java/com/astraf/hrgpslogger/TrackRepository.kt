package com.astraf.hrgpslogger

import android.content.Context
import java.io.File
import java.time.Instant
import java.time.format.DateTimeParseException
import kotlin.math.cos
import kotlin.math.sqrt

data class TrackSummary(
    val fileName: String,
    val filePath: String,
    val startedAtMillis: Long,
    val pointCount: Int,
    val durationMillis: Long?,
    val distanceMeters: Double?,
    val isActive: Boolean,
)

class TrackRepository(private val context: Context) {

    fun listTracks(activeFilePath: String?): List<TrackSummary> {
        val files = context.filesDir.listFiles { file ->
            file.isFile && file.name.startsWith(TRACK_PREFIX) && file.name.endsWith(".csv")
        } ?: return emptyList()

        return files
            .sortedByDescending { it.lastModified() }
            .map { file ->
                val parsed = parseTrack(file)
                parsed.copy(
                    isActive = activeFilePath != null && file.absolutePath == activeFilePath,
                )
            }
    }

    private fun parseTrack(file: File): TrackSummary {
        val startedAtMillis = file.name
            .removePrefix(TRACK_PREFIX)
            .removeSuffix(".csv")
            .toLongOrNull()
            ?: file.lastModified()

        if (file.length() == 0L) {
            return TrackSummary(
                fileName = file.name,
                filePath = file.absolutePath,
                startedAtMillis = startedAtMillis,
                pointCount = 0,
                durationMillis = null,
                distanceMeters = null,
                isActive = false,
            )
        }

        var pointCount = 0
        var firstTimestampMillis: Long? = null
        var lastTimestampMillis: Long? = null
        var lastLat: Double? = null
        var lastLon: Double? = null
        var distanceMeters = 0.0

        file.bufferedReader().useLines { lines ->
            lines.drop(1).forEach { line ->
                if (line.isBlank()) return@forEach
                val parts = line.split(',')
                if (parts.size < 4) return@forEach

                val timestampMillis = parseTimestamp(parts[0]) ?: return@forEach
                val lat = parts[2].toDoubleOrNull()
                val lon = parts[3].toDoubleOrNull()

                pointCount++
                if (firstTimestampMillis == null) firstTimestampMillis = timestampMillis
                lastTimestampMillis = timestampMillis

                if (lat != null && lon != null && lastLat != null && lastLon != null) {
                    distanceMeters += segmentDistanceMeters(lastLat!!, lastLon!!, lat, lon)
                }
                if (lat != null && lon != null) {
                    lastLat = lat
                    lastLon = lon
                }
            }
        }

        val duration = if (firstTimestampMillis != null && lastTimestampMillis != null) {
            (lastTimestampMillis!! - firstTimestampMillis!!).coerceAtLeast(0L)
        } else {
            null
        }

        return TrackSummary(
            fileName = file.name,
            filePath = file.absolutePath,
            startedAtMillis = firstTimestampMillis ?: startedAtMillis,
            pointCount = pointCount,
            durationMillis = duration,
            distanceMeters = if (pointCount > 1) distanceMeters else null,
            isActive = false,
        )
    }

    private fun parseTimestamp(raw: String): Long? {
        return try {
            Instant.parse(raw.trim()).toEpochMilli()
        } catch (_: DateTimeParseException) {
            null
        }
    }

    private fun segmentDistanceMeters(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val latMidRad = Math.toRadians((lat1 + lat2) * 0.5)
        val dLon = Math.toRadians(lon2 - lon1)
        val dLat = Math.toRadians(lat2 - lat1)
        val dx = dLon * cos(latMidRad) * EARTH_RADIUS_M
        val dy = dLat * EARTH_RADIUS_M
        return sqrt(dx * dx + dy * dy)
    }

    companion object {
        private const val TRACK_PREFIX = "hr_gps_"
        private const val EARTH_RADIUS_M = 6_371_000.0
    }
}
