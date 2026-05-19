package com.astraf.hrgpslogger

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlin.math.cos
import kotlin.math.sqrt

data class TripStats(
    val startedAtMillis: Long? = null,
    val durationMillis: Long = 0L,
    val currentSpeedKmh: Float? = null,
    val averageSpeedKmh: Float? = null,
    val maxSpeedKmh: Float = 0f,
    val distanceMeters: Double = 0.0,
)

class TripStatsTracker {

    private val _stats = MutableStateFlow(TripStats())
    val stats: StateFlow<TripStats> = _stats.asStateFlow()

    private var collectJob: Job? = null
    private var tickJob: Job? = null
    private var startedAtMillis: Long? = null
    private var lastLocation: LocationSample? = null
    private var totalDistanceMeters: Double = 0.0
    private var maxSpeedKmh: Float = 0f
    private var pausedDurationMillis: Long = 0L
    private var pauseStartedAtMillis: Long? = null

    fun restorePausedAnchor(startedAtMillis: Long) {
        this.startedAtMillis = startedAtMillis
        _stats.value = TripStats(
            startedAtMillis = startedAtMillis,
            durationMillis = elapsedMillis(),
        )
    }

    fun ensureStarted(
        scope: CoroutineScope,
        locationFlow: StateFlow<LocationSample?>,
        speedFlow: StateFlow<Float?>,
    ) {
        if (collectJob?.isActive == true) return
        start(scope, locationFlow, speedFlow)
    }

    fun start(
        scope: CoroutineScope,
        locationFlow: StateFlow<LocationSample?>,
        speedFlow: StateFlow<Float?>,
    ) {
        reset()
        startedAtMillis = System.currentTimeMillis()

        collectJob = combine(locationFlow, speedFlow) { location, speed ->
            location to speed
        }.onEach { (location, speed) ->
            update(location, speed)
        }.launchIn(scope)

        tickJob = scope.launch {
            while (isActive) {
                delay(1_000)
                refreshElapsed()
            }
        }
    }

    fun pause() {
        pauseStartedAtMillis?.let { return }
        pauseStartedAtMillis = System.currentTimeMillis()
        collectJob?.cancel()
        collectJob = null
        tickJob?.cancel()
        tickJob = null
    }

    fun resume(
        scope: CoroutineScope,
        locationFlow: StateFlow<LocationSample?>,
        speedFlow: StateFlow<Float?>,
    ) {
        pauseStartedAtMillis?.let { pausedAt ->
            pausedDurationMillis += System.currentTimeMillis() - pausedAt
        }
        pauseStartedAtMillis = null
        if (startedAtMillis == null) return
        if (collectJob?.isActive == true) return

        collectJob = combine(locationFlow, speedFlow) { location, speed ->
            location to speed
        }.onEach { (location, speed) ->
            update(location, speed)
        }.launchIn(scope)

        tickJob = scope.launch {
            while (isActive) {
                delay(1_000)
                refreshElapsed()
            }
        }
    }

    fun stop() {
        collectJob?.cancel()
        collectJob = null
        tickJob?.cancel()
        tickJob = null
        pauseStartedAtMillis = null
    }

    fun reset() {
        collectJob?.cancel()
        collectJob = null
        tickJob?.cancel()
        tickJob = null
        startedAtMillis = null
        lastLocation = null
        totalDistanceMeters = 0.0
        maxSpeedKmh = 0f
        pausedDurationMillis = 0L
        pauseStartedAtMillis = null
        _stats.value = TripStats()
    }

    private fun elapsedMillis(now: Long = System.currentTimeMillis()): Long {
        val start = startedAtMillis ?: return 0L
        val activePause = pauseStartedAtMillis?.let { now - it } ?: 0L
        return (now - start - pausedDurationMillis - activePause).coerceAtLeast(0L)
    }

    private fun update(location: LocationSample?, speedKmh: Float?) {
        val start = startedAtMillis ?: return
        val duration = elapsedMillis()

        if (location != null && location.accuracyMeters <= MAX_ACCURACY_M) {
            lastLocation?.let { previous ->
                totalDistanceMeters += distanceMeters(previous, location)
            }
            lastLocation = location
        }

        val currentSpeed = speedKmh ?: 0f
        if (currentSpeed > maxSpeedKmh) {
            maxSpeedKmh = currentSpeed
        }

        val averageSpeed = if (duration > 0L) {
            (totalDistanceMeters / (duration / 1000.0)) * MPS_TO_KMH
        } else {
            null
        }

        _stats.value = TripStats(
            startedAtMillis = start,
            durationMillis = duration,
            currentSpeedKmh = speedKmh,
            averageSpeedKmh = averageSpeed?.toFloat()?.takeIf { it > 0f },
            maxSpeedKmh = maxSpeedKmh,
            distanceMeters = totalDistanceMeters,
        )
    }

    private fun refreshElapsed() {
        val start = startedAtMillis ?: return
        val duration = elapsedMillis()
        val averageSpeed = if (duration > 0L) {
            (totalDistanceMeters / (duration / 1000.0)) * MPS_TO_KMH
        } else {
            null
        }
        val current = _stats.value
        _stats.value = current.copy(
            startedAtMillis = start,
            durationMillis = duration,
            averageSpeedKmh = averageSpeed?.toFloat()?.takeIf { it > 0f },
        )
    }

    private fun distanceMeters(a: LocationSample, b: LocationSample): Double {
        val latMidRad = Math.toRadians((a.latitude + b.latitude) * 0.5)
        val dLon = Math.toRadians(b.longitude - a.longitude)
        val dLat = Math.toRadians(b.latitude - a.latitude)
        val dx = dLon * cos(latMidRad) * EARTH_RADIUS_M
        val dy = dLat * EARTH_RADIUS_M
        return sqrt(dx * dx + dy * dy)
    }

    companion object {
        private const val EARTH_RADIUS_M = 6_371_000.0
        private const val MPS_TO_KMH = 3.6
        private const val MAX_ACCURACY_M = 25f
    }
}
