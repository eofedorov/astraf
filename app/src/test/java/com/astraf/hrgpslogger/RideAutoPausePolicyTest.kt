package com.astraf.hrgpslogger

import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

class RideAutoPausePolicyTest {

    private var clock = 0L
    private lateinit var policy: RideAutoPausePolicy

    @Before
    fun setUp() {
        clock = 0L
        policy = RideAutoPausePolicy(
            speedEstimator = MotionSpeedEstimator(
                staleAfterMs = 5_000L,
                elapsedRealtime = { clock },
            ),
        )
    }

    @Test
    fun recording_stationaryForHold_triggersPause() {
        clock = 1_000L
        policy.onLocation(stationaryLocation(ts = 1_000L))

        repeat(10) {
            clock += 1_000L
            assertEquals(AutoPauseAction.None, evaluateRecording())
        }

        clock += 1_000L
        assertEquals(AutoPauseAction.Pause, evaluateRecording())
    }

    @Test
    fun recording_moving_resetsPauseTimer() {
        clock = 1_000L
        policy.onLocation(stationaryLocation(ts = 1_000L))
        clock = 2_000L
        assertEquals(AutoPauseAction.None, evaluateRecording())

        clock = 3_000L
        policy.onLocation(movingLocation(ts = 3_000L))
        assertEquals(AutoPauseAction.None, evaluateRecording())

        clock = 4_000L
        policy.onLocation(stationaryLocation(ts = 4_000L))
        repeat(10) {
            clock += 1_000L
            assertEquals(AutoPauseAction.None, evaluateRecording())
        }
        clock += 1_000L
        assertEquals(AutoPauseAction.Pause, evaluateRecording())
    }

    @Test
    fun paused_manual_doesNotAutoResume() {
        feedMoving()
        clock = 20_000L
        repeat(3) {
            clock += 1_000L
            assertEquals(
                AutoPauseAction.None,
                policy.evaluate(RecordingPhase.Paused, manualPauseWhilePaused = true, clock),
            )
        }
    }

    @Test
    fun paused_auto_movingForHold_triggersResume() {
        clock = 1_000L
        policy.onLocation(movingLocation(ts = 1_000L))
        clock = 2_000L
        assertEquals(AutoPauseAction.None, evaluatePaused())
        clock = 3_000L
        assertEquals(AutoPauseAction.None, evaluatePaused())
        clock = 4_000L
        assertEquals(AutoPauseAction.Resume, evaluatePaused())
    }

    @Test
    fun recording_staleSpeed_triggersPause() {
        clock = 1_000L
        policy.onLocation(movingLocation(ts = 1_000L, speedMps = 8f))
        clock = MotionSpeedEstimator.STALE_AFTER_MS + 2_000L

        repeat(10) {
            assertEquals(AutoPauseAction.None, evaluateRecording())
            clock += 1_000L
        }
        assertEquals(AutoPauseAction.Pause, evaluateRecording())
    }

    @Test
    fun waitingForGps_noAction() {
        assertEquals(
            AutoPauseAction.None,
            policy.evaluate(RecordingPhase.WaitingForGps, manualPauseWhilePaused = false, 0L),
        )
    }

    private fun evaluateRecording(): AutoPauseAction =
        policy.evaluate(RecordingPhase.Recording, manualPauseWhilePaused = false, clock)

    private fun evaluatePaused(): AutoPauseAction =
        policy.evaluate(RecordingPhase.Paused, manualPauseWhilePaused = false, clock)

    private fun feedMoving() {
        clock = 1_000L
        policy.onLocation(movingLocation(ts = 1_000L))
        clock = 3_000L
        policy.onLocation(movingLocation(ts = 3_000L))
    }

    private fun stationaryLocation(ts: Long): LocationSample =
        location(lat = 55.751, lon = 37.618, ts = ts, speedMps = 0f)

    private fun movingLocation(ts: Long, speedMps: Float = 5f): LocationSample =
        location(
            lat = 55.751 + metersToLatDelta(rideStepMeters(18f, 2_000L)),
            lon = 37.618,
            ts = ts,
            speedMps = speedMps,
        )

    private fun location(
        lat: Double,
        lon: Double,
        ts: Long,
        speedMps: Float?,
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
