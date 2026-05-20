package com.astraf.hrgpslogger

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class ElevationConfig(
    val smoothingWindowPoints: Int = 5,
    val minClimbDeltaMeters: Float = 3f,
    val maxReasonableAltitudeJumpMeters: Float = 50f,
)

data class ElevationState(
    val smoothedAltitude: Float? = null,
    val currentMinAltitude: Float? = null,
    val totalClimbMeters: Float = 0f,
)

data class ElevationDebugStats(
    val pointsWithAltitude: Int = 0,
    val pointsWithoutAltitude: Int = 0,
    val rawAltitude: Float? = null,
    val smoothedAltitude: Float? = null,
    val totalClimbMeters: Float = 0f,
    val ignoredSmallElevationChanges: Int = 0,
)

class ElevationClimbTracker(
    private val config: ElevationConfig = ElevationConfig(),
) {
    private val _state = MutableStateFlow(ElevationState())
    val state: StateFlow<ElevationState> = _state.asStateFlow()

    private val _debugStats = MutableStateFlow(ElevationDebugStats())
    val debugStats: StateFlow<ElevationDebugStats> = _debugStats.asStateFlow()

    private val recentAltitudes = ArrayDeque<Double>()
    private var currentMinAltitude: Float? = null
    private var totalClimbMeters = 0f
    private var lastSmoothedAltitude: Float? = null
    private var pointsWithAltitude = 0
    private var pointsWithoutAltitude = 0
    private var ignoredSmallElevationChanges = 0

    fun reset() {
        recentAltitudes.clear()
        currentMinAltitude = null
        totalClimbMeters = 0f
        lastSmoothedAltitude = null
        pointsWithAltitude = 0
        pointsWithoutAltitude = 0
        ignoredSmallElevationChanges = 0
        publish()
    }

    fun restoreFromAcceptedPoints(points: List<AcceptedGpsPoint>) {
        reset()
        points.forEach { onAcceptedPoint(it) }
    }

    fun onAcceptedPoint(point: AcceptedGpsPoint) {
        val rawAltitude = point.altitudeMeters
        if (rawAltitude == null) {
            pointsWithoutAltitude++
            publish(rawAltitude = null)
            return
        }

        pointsWithAltitude++
        recentAltitudes.addLast(rawAltitude)
        while (recentAltitudes.size > config.smoothingWindowPoints) {
            recentAltitudes.removeFirst()
        }

        if (recentAltitudes.size == 1) {
            currentMinAltitude = rawAltitude.toFloat()
            publish(rawAltitude = rawAltitude.toFloat())
            return
        }

        if (recentAltitudes.size < MIN_ALTITUDE_POINTS_FOR_CLIMB) {
            currentMinAltitude = minOf(
                currentMinAltitude ?: rawAltitude.toFloat(),
                rawAltitude.toFloat(),
            )
            publish(rawAltitude = rawAltitude.toFloat())
            return
        }

        val smoothed = (recentAltitudes.sum() / recentAltitudes.size).toFloat()
        val raw = rawAltitude.toFloat()

        if (lastSmoothedAltitude != null) {
            val jump = kotlin.math.abs(smoothed - lastSmoothedAltitude!!)
            if (jump > config.maxReasonableAltitudeJumpMeters) {
                currentMinAltitude = minOf(smoothed, raw)
                lastSmoothedAltitude = smoothed
                publish(rawAltitude = raw, smoothedAltitude = smoothed)
                return
            }
        }

        if (currentMinAltitude == null) {
            currentMinAltitude = minOf(smoothed, raw)
            lastSmoothedAltitude = smoothed
            publish(rawAltitude = raw, smoothedAltitude = smoothed)
            return
        }

        val valley = minOf(smoothed, raw)
        if (valley < currentMinAltitude!!) {
            currentMinAltitude = valley
        }

        if (smoothed > currentMinAltitude!!) {
            val delta = smoothed - currentMinAltitude!!
            if (delta >= config.minClimbDeltaMeters) {
                totalClimbMeters += delta
                currentMinAltitude = smoothed
            } else {
                ignoredSmallElevationChanges++
            }
        }

        lastSmoothedAltitude = smoothed
        publish(rawAltitude = raw, smoothedAltitude = smoothed)
    }

    fun hasAltitudeData(): Boolean = pointsWithAltitude > 0

    private fun publish(
        rawAltitude: Float? = _debugStats.value.rawAltitude,
        smoothedAltitude: Float? = _state.value.smoothedAltitude,
    ) {
        _state.value = ElevationState(
            smoothedAltitude = smoothedAltitude,
            currentMinAltitude = currentMinAltitude,
            totalClimbMeters = totalClimbMeters,
        )
        _debugStats.value = ElevationDebugStats(
            pointsWithAltitude = pointsWithAltitude,
            pointsWithoutAltitude = pointsWithoutAltitude,
            rawAltitude = rawAltitude,
            smoothedAltitude = smoothedAltitude,
            totalClimbMeters = totalClimbMeters,
            ignoredSmallElevationChanges = ignoredSmallElevationChanges,
        )
    }

    companion object {
        private const val MIN_ALTITUDE_POINTS_FOR_CLIMB = 2

        fun computeTotalClimbMeters(
            points: List<AcceptedGpsPoint>,
            config: ElevationConfig = ElevationConfig(),
        ): Float? {
            if (points.none { it.altitudeMeters != null }) return null
            val tracker = ElevationClimbTracker(config)
            tracker.restoreFromAcceptedPoints(points)
            return tracker.state.value.totalClimbMeters
        }
    }
}
