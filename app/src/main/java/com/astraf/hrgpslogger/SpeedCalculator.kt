package com.astraf.hrgpslogger

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.math.cos
import kotlin.math.sqrt

/**
 * Лёгкий расчёт скорости по GPS: предпочитает [gpsSpeedMps] от чипа,
 * иначе — дистанция/время между точками; сглаживание EMA как в навигаторах.
 * Пересчёт только при новой координате (без таймеров и лишних потоков).
 */
class SpeedCalculator {

    private var lastSample: LocationSample? = null
    private var smoothedMps: Float? = null

    private val _speedKmh = MutableStateFlow<Float?>(null)
    val speedKmh: StateFlow<Float?> = _speedKmh.asStateFlow()

    fun update(sample: LocationSample, gpsSpeedMps: Float?) {
        if (sample.accuracyMeters > MAX_ACCURACY_M || sample.accuracyMeters <= 0f) {
            return
        }

        val instantMps = computeInstantSpeedMps(sample, gpsSpeedMps) ?: return

        smoothedMps = if (smoothedMps == null) {
            instantMps
        } else {
            EMA_ALPHA * instantMps + (1f - EMA_ALPHA) * smoothedMps!!
        }

        val kmh = smoothedMps!! * MPS_TO_KMH
        _speedKmh.value = if (kmh < STATIONARY_KMH) 0f else kmh
        lastSample = sample
    }

    fun reset() {
        lastSample = null
        smoothedMps = null
        _speedKmh.value = null
    }

    private fun computeInstantSpeedMps(sample: LocationSample, gpsSpeedMps: Float?): Float? {
        var speed = gpsSpeedMps?.takeIf { it in 0f..MAX_SPEED_MPS }

        val previous = lastSample ?: return speed
        val dtSec = (sample.timestampMillis - previous.timestampMillis) / 1000f
        if (dtSec < MIN_DT_SEC) return speed

        val distanceM = distanceMeters(previous, sample)
        val noiseM = (previous.accuracyMeters + sample.accuracyMeters) * NOISE_FACTOR
        if (distanceM < noiseM) {
            return speed ?: 0f
        }

        val derivedMps = (distanceM / dtSec).toFloat()
        if (derivedMps > MAX_SPEED_MPS) return speed

        speed = when (speed) {
            null -> derivedMps
            else -> GPS_WEIGHT * speed + (1f - GPS_WEIGHT) * derivedMps
        }
        return speed
    }

  /** Быстрая equirectangular-аппроксимация — достаточна для коротких интервалов GPS. */
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
        private const val MPS_TO_KMH = 3.6f
        private const val EMA_ALPHA = 0.35f
        private const val GPS_WEIGHT = 0.65f
        private const val MIN_DT_SEC = 0.8f
        private const val MAX_ACCURACY_M = 25f
        private const val MAX_SPEED_MPS = 55f
        private const val STATIONARY_KMH = 1.5f
        private const val NOISE_FACTOR = 0.35f
    }
}
