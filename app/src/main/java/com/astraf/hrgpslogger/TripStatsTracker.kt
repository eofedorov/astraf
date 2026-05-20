package com.astraf.hrgpslogger

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

data class TripStats(
    val startedAtMillis: Long? = null,
    val durationMillis: Long = 0L,
    val movingTimeMillis: Long = 0L,
    val currentSpeedKmh: Float? = null,
    val averageSpeedKmh: Float? = null,
    val maxSpeedKmh: Float = 0f,
    val distanceMeters: Double = 0.0,
)

class TripStatsTracker {

    private val _stats = MutableStateFlow(TripStats())
    val stats: StateFlow<TripStats> = _stats.asStateFlow()

    private var tickJob: Job? = null
    private var startedAtMillis: Long? = null
    private var lastAccepted: AcceptedGpsPoint? = null
    private var totalDistanceMeters: Double = 0.0
    private var movingTimeMillis: Long = 0L
    private var maxSpeedKmh: Float = 0f
    private var pausedDurationMillis: Long = 0L
    private var pauseStartedAtMillis: Long? = null
    private var waitingGpsDurationMillis: Long = 0L
    private var waitingGpsStartedAtMillis: Long? = null

    fun restorePausedAnchor(startedAtMillis: Long) {
        this.startedAtMillis = startedAtMillis
        publishStats(currentSpeedKmh = null)
    }

    fun restoreFromAcceptedPoints(points: List<AcceptedGpsPoint>, startedAtMillis: Long) {
        resetInternal()
        this.startedAtMillis = startedAtMillis
        points.forEachIndexed { index, point ->
            val newSegment = index == 0 || point.segmentId != points[index - 1].segmentId
            onAcceptedPointInternal(point, newSegment, point.derivedSpeedKmh)
        }
        publishStats(currentSpeedKmh = lastAccepted?.derivedSpeedKmh)
    }

    fun start(scope: CoroutineScope, startedAtMillis: Long = System.currentTimeMillis()) {
        resetInternal()
        this.startedAtMillis = startedAtMillis
        startTick(scope)
        publishStats(currentSpeedKmh = null)
    }

    fun beginWaitingForGps() {
        waitingGpsStartedAtMillis = System.currentTimeMillis()
    }

    fun endWaitingForGps(firstPointTimestampMillis: Long) {
        waitingGpsStartedAtMillis?.let { waitingStart ->
            waitingGpsDurationMillis += System.currentTimeMillis() - waitingStart
        }
        waitingGpsStartedAtMillis = null
        if (startedAtMillis == null) {
            startedAtMillis = firstPointTimestampMillis
        }
    }

    fun onAcceptedPoint(point: AcceptedGpsPoint, newSegment: Boolean, currentSpeedKmh: Float?) {
        onAcceptedPointInternal(point, newSegment, currentSpeedKmh)
        publishStats(currentSpeedKmh = currentSpeedKmh)
    }

    fun pause() {
        pauseStartedAtMillis?.let { return }
        pauseStartedAtMillis = System.currentTimeMillis()
        tickJob?.cancel()
        tickJob = null
    }

    fun resume(scope: CoroutineScope) {
        pauseStartedAtMillis?.let { pausedAt ->
            pausedDurationMillis += System.currentTimeMillis() - pausedAt
        }
        pauseStartedAtMillis = null
        if (startedAtMillis == null) return
        if (tickJob?.isActive == true) return
        startTick(scope)
    }

    fun stop() {
        tickJob?.cancel()
        tickJob = null
        pauseStartedAtMillis = null
        waitingGpsStartedAtMillis = null
    }

    fun reset() {
        stop()
        resetInternal()
        _stats.value = TripStats()
    }

    private fun resetInternal() {
        startedAtMillis = null
        lastAccepted = null
        totalDistanceMeters = 0.0
        movingTimeMillis = 0L
        maxSpeedKmh = 0f
        pausedDurationMillis = 0L
        waitingGpsDurationMillis = 0L
    }

    private fun onAcceptedPointInternal(
        point: AcceptedGpsPoint,
        newSegment: Boolean,
        currentSpeedKmh: Float?,
    ) {
        val previous = lastAccepted
        if (previous != null && !newSegment && previous.segmentId == point.segmentId) {
            totalDistanceMeters += GeoDistance.distanceMeters(previous, point)
            val deltaMs = point.timestampMillis - previous.timestampMillis
            if (deltaMs > 0L) {
                val speedForMoving = currentSpeedKmh ?: point.derivedSpeedKmh ?: 0f
                if (speedForMoving >= MOVING_MIN_KMH) {
                    movingTimeMillis += deltaMs
                }
            }
        }
        lastAccepted = point

        val speed = currentSpeedKmh ?: 0f
        if (speed > maxSpeedKmh) {
            maxSpeedKmh = speed
        }
    }

    private fun elapsedMillis(now: Long = System.currentTimeMillis()): Long {
        val start = startedAtMillis ?: return 0L
        val activePause = pauseStartedAtMillis?.let { now - it } ?: 0L
        val activeWaiting = waitingGpsStartedAtMillis?.let { now - it } ?: 0L
        return (now - start - pausedDurationMillis - activePause - waitingGpsDurationMillis - activeWaiting)
            .coerceAtLeast(0L)
    }

    private fun publishStats(currentSpeedKmh: Float?) {
        val start = startedAtMillis
        val duration = elapsedMillis()
        val averageSpeed = if (movingTimeMillis > 0L) {
            (totalDistanceMeters / (movingTimeMillis / 1000.0) * MPS_TO_KMH).toFloat()
        } else {
            null
        }

        _stats.value = TripStats(
            startedAtMillis = start,
            durationMillis = duration,
            movingTimeMillis = movingTimeMillis,
            currentSpeedKmh = currentSpeedKmh,
            averageSpeedKmh = averageSpeed?.takeIf { it > 0f },
            maxSpeedKmh = maxSpeedKmh,
            distanceMeters = totalDistanceMeters,
        )
    }

    private fun refreshElapsed() {
        publishStats(currentSpeedKmh = _stats.value.currentSpeedKmh)
    }

    private fun startTick(scope: CoroutineScope) {
        tickJob = scope.launch {
            while (isActive) {
                delay(1_000)
                refreshElapsed()
            }
        }
    }

    companion object {
        private const val MPS_TO_KMH = 3.6
        private const val MOVING_MIN_KMH = 1.5f
    }
}
