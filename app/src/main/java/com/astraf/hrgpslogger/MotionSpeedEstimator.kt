package com.astraf.hrgpslogger

import android.os.SystemClock

/**
 * Оценка скорости для политики автопаузы по сырым location updates (без accepted GPS pipeline).
 */
class MotionSpeedEstimator(
    private val staleAfterMs: Long = STALE_AFTER_MS,
    private val elapsedRealtime: () -> Long = { SystemClock.elapsedRealtime() },
) {
    private var previousSample: LocationSample? = null
    private var lastSample: LocationSample? = null
    private var lastReceivedElapsed: Long? = null

    fun onLocation(sample: LocationSample) {
        previousSample = lastSample
        lastSample = sample
        lastReceivedElapsed = elapsedRealtime()
    }

    fun reset() {
        previousSample = null
        lastSample = null
        lastReceivedElapsed = null
    }

    fun currentSpeedKmh(nowElapsed: Long = elapsedRealtime()): Float? {
        val sample = lastSample ?: return null
        val receivedAt = lastReceivedElapsed ?: return null

        if (nowElapsed - receivedAt > staleAfterMs) {
            return 0f
        }

        sample.speedMps?.takeIf { it >= 0f }?.let { mps ->
            return (mps * MPS_TO_KMH).coerceAtLeast(0f)
        }

        val previous = previousSample
        if (previous != null && previous !== sample) {
            val deltaMs = sample.timestampMillis - previous.timestampMillis
            if (deltaMs > 0L) {
                val distanceM = GeoDistance.distanceMeters(
                    previous.latitude,
                    previous.longitude,
                    sample.latitude,
                    sample.longitude,
                )
                return (distanceM / (deltaMs / 1000.0) * MPS_TO_KMH).toFloat().coerceAtLeast(0f)
            }
        }

        return null
    }

    companion object {
        private const val MPS_TO_KMH = 3.6f
        const val STALE_AFTER_MS = 8_000L
    }
}
