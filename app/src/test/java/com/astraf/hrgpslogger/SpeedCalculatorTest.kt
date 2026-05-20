package com.astraf.hrgpslogger

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SpeedCalculatorTest {

    @Test
    fun smoothsSpeedOverWindow() {
        val calculator = SpeedCalculator(windowMs = 5_000L)
        val p1 = accepted(lat = 55.751, lon = 37.618, ts = 1_000L, speed = 20f)
        val p2 = accepted(lat = 55.7512, lon = 37.6182, ts = 3_000L, speed = 22f)
        val p3 = accepted(lat = 55.7514, lon = 37.6184, ts = 5_000L, speed = 21f)

        calculator.update(p1)
        calculator.update(p2)
        calculator.update(p3)

        val speed = calculator.currentSpeedKmh
        assertTrue(speed != null && speed >= 15f)
    }

    @Test
    fun stationarySpeedBecomesZero() {
        val calculator = SpeedCalculator()
        val p1 = accepted(lat = 55.751, lon = 37.618, ts = 1_000L, speed = 0.5f)
        val p2 = accepted(lat = 55.7510001, lon = 37.6180001, ts = 3_000L, speed = 0.4f)

        calculator.update(p1)
        calculator.update(p2)

        assertEquals(0f, calculator.currentSpeedKmh)
    }

    private fun accepted(
        lat: Double,
        lon: Double,
        ts: Long,
        speed: Float,
    ): AcceptedGpsPoint = AcceptedGpsPoint(
        latitude = lat,
        longitude = lon,
        timestampMillis = ts,
        accuracyMeters = 10f,
        derivedSpeedKmh = speed,
        segmentId = 0,
    )
}
