package com.astraf.hrgpslogger

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

class MotionSpeedEstimatorTest {

    private var clock = 0L
    private lateinit var estimator: MotionSpeedEstimator

    @Before
    fun setUp() {
        clock = 0L
        estimator = MotionSpeedEstimator(
            staleAfterMs = 5_000L,
            elapsedRealtime = { clock },
        )
    }

    @Test
    fun speedMps_convertedToKmh() {
        clock = 1_000L
        estimator.onLocation(location(speedMps = 5f, ts = 1_000L))
        clock = 2_000L
        assertEquals(18f, estimator.currentSpeedKmh()!!, 0.5f)
    }

    @Test
    fun staleLocation_returnsZeroSpeed() {
        clock = 1_000L
        estimator.onLocation(location(speedMps = 10f, ts = 1_000L))
        clock = 7_000L
        assertEquals(0f, estimator.currentSpeedKmh()!!, 0.01f)
    }

    @Test
    fun derivedSpeed_betweenTwoSamples() {
        clock = 1_000L
        estimator.onLocation(location(lat = 55.751, lon = 37.618, ts = 1_000L))
        clock = 3_000L
        estimator.onLocation(
            location(
                lat = 55.751 + metersToLatDelta(rideStepMeters(18f, 2_000L)),
                lon = 37.618,
                ts = 3_000L,
            ),
        )
        assertEquals(18f, estimator.currentSpeedKmh()!!, 2f)
    }

    @Test
    fun noSamples_returnsNull() {
        assertNull(estimator.currentSpeedKmh())
    }

    private fun location(
        lat: Double = 55.751,
        lon: Double = 37.618,
        ts: Long = 1_000L,
        speedMps: Float? = null,
    ): LocationSample = LocationSample(
        latitude = lat,
        longitude = lon,
        timestampMillis = ts,
        accuracyMeters = 10f,
        speedMps = speedMps,
    )

    private fun rideStepMeters(speedKmh: Float, intervalMs: Long): Double =
        speedKmh / 3.6f * (intervalMs / 1000.0)

    private fun metersToLatDelta(meters: Double): Double = meters / 111_000.0
}
