package com.astraf.hrgpslogger

import android.content.Context
import java.io.File
import kotlin.math.max

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

        val points = TrackCsvParser.parseAcceptedPoints(file)
        val duration = if (points.size >= 2) {
            (points.last().timestampMillis - points.first().timestampMillis).coerceAtLeast(0L)
        } else {
            null
        }

        return TrackSummary(
            fileName = file.name,
            filePath = file.absolutePath,
            startedAtMillis = points.firstOrNull()?.timestampMillis ?: startedAtMillis,
            pointCount = points.size,
            durationMillis = duration,
            distanceMeters = if (points.size > 1) TrackCsvParser.distanceMeters(points) else null,
            isActive = false,
        )
    }

    companion object {
        private const val TRACK_PREFIX = "hr_gps_"
    }
}
