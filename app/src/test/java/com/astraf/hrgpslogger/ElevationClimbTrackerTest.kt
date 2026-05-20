package com.astraf.hrgpslogger

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class ElevationClimbTrackerTest {

    private lateinit var tracker: ElevationClimbTracker

    @Before
    fun setUp() {
        tracker = ElevationClimbTracker()
    }

    @Test
    fun flatRoadWithNoise_totalClimbNearZero() {
        listOf(100.0, 101.0, 99.0, 102.0, 100.0, 101.0).forEach { alt ->
            tracker.onAcceptedPoint(point(alt))
        }
        assertTrue(tracker.state.value.totalClimbMeters < 3f)
    }

    @Test
    fun singleClimb_accumulatesMeaningfulGain() {
        (100..120 step 2).map { it.toDouble() }.forEach { alt ->
            tracker.onAcceptedPoint(point(alt, ts = alt.toLong() * 1_000L))
        }
        assertTrue(tracker.state.value.totalClimbMeters >= 10f)
    }

    @Test
    fun climbDescentNewClimb_countsBothAscents() {
        listOf(
            100.0, 103.0, 106.0, 109.0, 110.0,
            107.0, 105.0,
            108.0, 111.0, 114.0, 115.0,
        ).forEachIndexed { index, alt ->
            tracker.onAcceptedPoint(point(alt, ts = (index + 1) * 1_000L))
        }
        assertTrue(
            "total=${tracker.state.value.totalClimbMeters}",
            tracker.state.value.totalClimbMeters >= 10f,
        )
    }

    @Test
    fun climbDescentNewClimb_specScenario3_approximateGain() {
        listOf(100.0, 110.0, 105.0, 115.0).forEachIndexed { index, alt ->
            tracker.onAcceptedPoint(point(alt, ts = (index + 1) * 2_000L))
        }
        assertTrue(
            "total=${tracker.state.value.totalClimbMeters}",
            tracker.state.value.totalClimbMeters >= 5f,
        )
    }

    @Test
    fun unrealisticAltitudeJump_resetsWithoutHugeSpike() {
        tracker.onAcceptedPoint(point(100.0))
        tracker.onAcceptedPoint(point(102.0))
        tracker.onAcceptedPoint(point(200.0))
        assertTrue(tracker.state.value.totalClimbMeters < 50f)
    }

    @Test
    fun pointsWithoutAltitude_doNotBreakCalculation() {
        tracker.onAcceptedPoint(point(100.0, ts = 1_000L))
        tracker.onAcceptedPoint(point(null, ts = 2_000L))
        tracker.onAcceptedPoint(point(105.0, ts = 3_000L))
        tracker.onAcceptedPoint(point(null, ts = 4_000L))
        tracker.onAcceptedPoint(point(110.0, ts = 5_000L))
        tracker.onAcceptedPoint(point(115.0, ts = 6_000L))
        assertTrue(tracker.state.value.totalClimbMeters >= 3f)
        assertEquals(4, tracker.debugStats.value.pointsWithAltitude)
        assertEquals(2, tracker.debugStats.value.pointsWithoutAltitude)
    }

    @Test
    fun computeTotalClimbMeters_noAltitude_returnsNull() {
        val points = listOf(
            point(null, ts = 1_000L),
            point(null, ts = 2_000L),
        )
        assertNull(ElevationClimbTracker.computeTotalClimbMeters(points))
    }

    @Test
    fun restoreFromAcceptedPoints_matchesIncremental() {
        val alts = listOf(100.0, 110.0, 105.0, 115.0)
        alts.forEach { tracker.onAcceptedPoint(point(it)) }
        val incremental = tracker.state.value.totalClimbMeters

        val replay = ElevationClimbTracker()
        replay.restoreFromAcceptedPoints(alts.map { point(it) })
        assertEquals(incremental, replay.state.value.totalClimbMeters, 0.01f)
    }

    private fun point(
        altitude: Double?,
        ts: Long = System.currentTimeMillis(),
    ): AcceptedGpsPoint = AcceptedGpsPoint(
        latitude = 55.0,
        longitude = 37.0,
        timestampMillis = ts,
        accuracyMeters = 10f,
        derivedSpeedKmh = null,
        segmentId = 0,
        altitudeMeters = altitude,
    )
}
