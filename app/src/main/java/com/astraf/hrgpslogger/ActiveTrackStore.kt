package com.astraf.hrgpslogger

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File

class ActiveTrackStore {

    private val _segments = MutableStateFlow<List<TrackSegment>>(emptyList())
    val segments: StateFlow<List<TrackSegment>> = _segments.asStateFlow()

    fun appendAccepted(point: AcceptedGpsPoint, newSegment: Boolean) {
        val current = _segments.value.toMutableList()
        val geo = point.toGeoPoint()
        val lastSegment = current.lastOrNull()

        if (lastSegment == null || newSegment || lastSegment.id != point.segmentId) {
            val existingIdx = current.indexOfFirst { it.id == point.segmentId }
            if (existingIdx >= 0) {
                val segment = current[existingIdx]
                if (shouldAppend(segment.points.lastOrNull(), geo)) {
                    current[existingIdx] = segment.copy(points = segment.points + geo)
                }
            } else {
                current.add(TrackSegment(id = point.segmentId, points = listOf(geo)))
            }
        } else {
            if (shouldAppend(lastSegment.points.lastOrNull(), geo)) {
                current[current.lastIndex] = lastSegment.copy(points = lastSegment.points + geo)
            }
        }
        _segments.value = current
    }

    fun clear() {
        _segments.value = emptyList()
    }

    fun restoreFromCsv(file: File) {
        restoreFromAcceptedPoints(TrackCsvParser.parseAcceptedPoints(file))
    }

    fun restoreFromAcceptedPoints(points: List<AcceptedGpsPoint>) {
        clear()
        points.forEachIndexed { index, point ->
            val newSegment = index == 0 || point.segmentId != points[index - 1].segmentId
            appendAccepted(point, newSegment = newSegment)
        }
    }

    private fun shouldAppend(last: GeoPoint?, point: GeoPoint): Boolean {
        if (last == null) return true
        return last.latitude != point.latitude || last.longitude != point.longitude
    }
}
