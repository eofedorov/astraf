package com.astraf.hrgpslogger

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File
import java.time.Instant
import java.time.format.DateTimeParseException

data class GeoPoint(
    val latitude: Double,
    val longitude: Double,
)

class ActiveTrackStore {

    private val _points = MutableStateFlow<List<GeoPoint>>(emptyList())
    val points: StateFlow<List<GeoPoint>> = _points.asStateFlow()

    fun append(sample: LocationSample, phase: RecordingPhase) {
        if (phase != RecordingPhase.Recording) return
        if (sample.accuracyMeters > MAX_ACCURACY_M) return
        val point = GeoPoint(sample.latitude, sample.longitude)
        val current = _points.value
        val last = current.lastOrNull()
        if (last != null &&
            last.latitude == point.latitude &&
            last.longitude == point.longitude
        ) {
            return
        }
        _points.value = current + point
    }

    fun clear() {
        _points.value = emptyList()
    }

    fun restoreFromCsv(file: File) {
        if (!file.exists()) {
            clear()
            return
        }
        val loaded = mutableListOf<GeoPoint>()
        file.bufferedReader().useLines { lines ->
            lines.drop(1).forEach { line ->
                if (line.isBlank()) return@forEach
                val parts = line.split(',')
                if (parts.size < 4) return@forEach
                if (parseTimestamp(parts[0]) == null) return@forEach
                val lat = parts[2].toDoubleOrNull() ?: return@forEach
                val lon = parts[3].toDoubleOrNull() ?: return@forEach
                val point = GeoPoint(lat, lon)
                val last = loaded.lastOrNull()
                if (last == null || last.latitude != point.latitude || last.longitude != point.longitude) {
                    loaded.add(point)
                }
            }
        }
        _points.value = loaded
    }

    private fun parseTimestamp(raw: String): Long? {
        return try {
            Instant.parse(raw.trim()).toEpochMilli()
        } catch (_: DateTimeParseException) {
            null
        }
    }

    companion object {
        private const val MAX_ACCURACY_M = 25f
    }
}
