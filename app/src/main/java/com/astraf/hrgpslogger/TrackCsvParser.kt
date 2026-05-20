package com.astraf.hrgpslogger

import java.io.File
import java.time.Instant
import java.time.format.DateTimeParseException

object TrackCsvParser {

    fun parseAcceptedPoints(file: File): List<AcceptedGpsPoint> =
        parseSamples(file).map { it.point }

    fun parseSamples(file: File): List<TrackCsvSample> {
        if (!file.exists()) return emptyList()

        val samples = mutableListOf<TrackCsvSample>()
        file.bufferedReader().useLines { lines ->
            lines.drop(1).forEach { line ->
                parseSample(line)?.let { samples.add(it) }
            }
        }
        return samples
    }

    fun parseSample(line: String): TrackCsvSample? {
        val point = parseAcceptedPoint(line) ?: return null
        val parts = line.split(',')
        val bpm = parts.getOrNull(6)?.trim()?.toIntOrNull()
        return TrackCsvSample(point = point, bpm = bpm)
    }

    fun parseAcceptedPoint(line: String): AcceptedGpsPoint? {
        if (line.isBlank()) return null
        val parts = line.split(',')
        if (parts.size < 5) return null

        val timestampMillis = parseTimestamp(parts[0]) ?: return null
        val segmentId = parts[1].toIntOrNull() ?: return null
        val lat = parts[2].toDoubleOrNull() ?: return null
        val lon = parts[3].toDoubleOrNull() ?: return null
        val accuracy = parts[4].toFloatOrNull() ?: return null
        val derivedSpeed = parts.getOrNull(5)?.toFloatOrNull()

        return AcceptedGpsPoint(
            latitude = lat,
            longitude = lon,
            timestampMillis = timestampMillis,
            accuracyMeters = accuracy,
            derivedSpeedKmh = derivedSpeed,
            segmentId = segmentId,
        )
    }

    fun segmentsCount(points: List<AcceptedGpsPoint>): Int {
        if (points.isEmpty()) return 0
        return points.map { it.segmentId }.distinct().size
    }

    fun distanceMeters(points: List<AcceptedGpsPoint>): Double {
        var total = 0.0
        for (index in 1 until points.size) {
            val previous = points[index - 1]
            val current = points[index]
            if (previous.segmentId == current.segmentId) {
                total += GeoDistance.distanceMeters(previous, current)
            }
        }
        return total
    }

    private fun parseTimestamp(raw: String): Long? {
        return try {
            Instant.parse(raw.trim()).toEpochMilli()
        } catch (_: DateTimeParseException) {
            null
        }
    }
}
