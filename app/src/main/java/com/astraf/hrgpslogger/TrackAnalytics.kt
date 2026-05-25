package com.astraf.hrgpslogger

object TrackAnalytics {

    private const val MPS_TO_KMH = 3.6f
    private const val MOVING_MIN_KMH = 1.5f
    private const val MIN_CHART_POINTS = 2

    fun buildSegments(points: List<AcceptedGpsPoint>): List<TrackSegment> {
        if (points.isEmpty()) return emptyList()
        val segments = mutableListOf<TrackSegment>()
        var currentId = points.first().segmentId
        var currentPoints = mutableListOf<GeoPoint>()

        fun flush() {
            if (currentPoints.isNotEmpty()) {
                segments.add(TrackSegment(id = currentId, points = currentPoints.toList()))
            }
        }

        points.forEachIndexed { index, point ->
            val geo = point.toGeoPoint()
            val newSegment = index == 0 || point.segmentId != points[index - 1].segmentId
            if (newSegment && index > 0) {
                flush()
                currentPoints = mutableListOf()
                currentId = point.segmentId
            }
            val last = currentPoints.lastOrNull()
            if (last == null || last.latitude != geo.latitude || last.longitude != geo.longitude) {
                currentPoints.add(geo)
            }
        }
        flush()
        return segments
    }

    fun computeDistanceMeters(points: List<AcceptedGpsPoint>): Double? {
        if (points.size < 2) return if (points.isEmpty()) null else 0.0
        return TrackCsvParser.distanceMeters(points)
    }

    fun computeDurationMillis(points: List<AcceptedGpsPoint>): Long? {
        if (points.isEmpty()) return null
        if (points.size == 1) return 0L
        return (points.last().timestampMillis - points.first().timestampMillis).coerceAtLeast(0L)
    }

    fun computeMovingTimeMillis(points: List<AcceptedGpsPoint>): Long? {
        if (points.size < 2) return null
        var movingTimeMillis = 0L
        for (index in 1 until points.size) {
            val previous = points[index - 1]
            val current = points[index]
            if (previous.segmentId != current.segmentId) continue
            val deltaMs = current.timestampMillis - previous.timestampMillis
            if (deltaMs <= 0L) continue
            val segmentDistance = GeoDistance.distanceMeters(previous, current)
            val speedKmh = current.derivedSpeedKmh
                ?: (segmentDistance / (deltaMs / 1000.0) * MPS_TO_KMH).toFloat()
            if (speedKmh >= MOVING_MIN_KMH) {
                movingTimeMillis += deltaMs
            }
        }
        return movingTimeMillis.takeIf { it > 0L }
    }

    fun computeAverageSpeedKmh(points: List<AcceptedGpsPoint>): Float? {
        if (points.size < 2) return null
        val movingTimeMillis = computeMovingTimeMillis(points) ?: return null
        var totalDistance = 0.0
        for (index in 1 until points.size) {
            val previous = points[index - 1]
            val current = points[index]
            if (previous.segmentId != current.segmentId) continue
            totalDistance += GeoDistance.distanceMeters(previous, current)
        }
        return (totalDistance / (movingTimeMillis / 1000.0) * MPS_TO_KMH).toFloat()
    }

    fun computeMaxSpeedKmh(points: List<AcceptedGpsPoint>): Float? {
        var max = 0f
        var hasSpeed = false
        for (index in 1 until points.size) {
            val previous = points[index - 1]
            val current = points[index]
            if (previous.segmentId != current.segmentId) continue
            val speed = speedKmhBetween(previous, current) ?: continue
            if (speed > max) {
                max = speed
                hasSpeed = true
            }
        }
        points.forEach { point ->
            point.derivedSpeedKmh?.coerceAtLeast(0f)?.let { derived ->
                if (derived > max) {
                    max = derived
                    hasSpeed = true
                }
            }
        }
        return max.takeIf { hasSpeed && it > 0f }
    }

    fun computeHeartRateStats(samples: List<TrackCsvSample>): Pair<Int?, Int?> {
        val readings = samples.mapNotNull { it.bpm }
        if (readings.isEmpty()) return null to null
        return readings.average().toInt() to readings.maxOrNull()
    }

    fun buildSpeedSeries(samples: List<TrackCsvSample>): List<TrackChartPoint> {
        val points = samples.map { it.point }
        if (points.size < MIN_CHART_POINTS) return emptyList()
        val start = points.first().timestampMillis
        return buildList {
            add(TrackChartPoint(elapsedMillis = 0L, value = 0f))
            for (index in 1 until points.size) {
                val previous = points[index - 1]
                val current = points[index]
                val elapsed = (current.timestampMillis - start).coerceAtLeast(0L)
                if (previous.segmentId != current.segmentId) {
                    add(TrackChartPoint(elapsedMillis = elapsed, value = 0f))
                    continue
                }
                val speed = speedKmhBetween(previous, current) ?: continue
                add(TrackChartPoint(elapsedMillis = elapsed, value = speed))
            }
        }
    }

    private fun speedKmhBetween(previous: AcceptedGpsPoint, current: AcceptedGpsPoint): Float? {
        if (previous.segmentId != current.segmentId) return null
        val raw = current.derivedSpeedKmh ?: run {
            val deltaMs = current.timestampMillis - previous.timestampMillis
            if (deltaMs <= 0L) return null
            (GeoDistance.distanceMeters(previous, current) / (deltaMs / 1000.0) * MPS_TO_KMH).toFloat()
        }
        return raw.coerceAtLeast(0f)
    }

    fun buildHeartRateSeries(samples: List<TrackCsvSample>): List<TrackChartPoint> {
        val withHr = samples.filter { it.bpm != null }
        if (withHr.size < MIN_CHART_POINTS) return emptyList()
        val start = samples.first().point.timestampMillis
        return withHr.map { sample ->
            TrackChartPoint(
                elapsedMillis = (sample.point.timestampMillis - start).coerceAtLeast(0L),
                value = sample.bpm!!.toFloat(),
            )
        }
    }

    fun buildElevationSeries(samples: List<TrackCsvSample>): List<TrackChartPoint> {
        val withAlt = samples.filter { it.point.altitudeMeters != null }
        if (withAlt.size < MIN_CHART_POINTS) return emptyList()
        val start = samples.first().point.timestampMillis
        return withAlt.map { sample ->
            TrackChartPoint(
                elapsedMillis = (sample.point.timestampMillis - start).coerceAtLeast(0L),
                value = sample.point.altitudeMeters!!.toFloat(),
            )
        }
    }

    fun resolveStatus(
        fileExists: Boolean,
        readable: Boolean,
        pointCount: Int,
        isActive: Boolean,
    ): TrackDetailStatus = when {
        !fileExists -> TrackDetailStatus.FileNotFound
        !readable -> TrackDetailStatus.Unreadable
        isActive -> TrackDetailStatus.RecordingActive
        pointCount == 0 -> TrackDetailStatus.InsufficientData
        else -> TrackDetailStatus.Completed
    }
}
