package com.astraf.hrgpslogger

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File

/**
 * Интеграционные JVM-тесты: связка фильтра, скорости, статистики и CSV-replay.
 */
class GpsTrackIntegrationTest {

    private lateinit var controller: GpsRideController
    private lateinit var stats: TripStatsTracker
    private val config = GpsTrackQualityProcessorTest.BICYCLE_CONFIG

    @Before
    fun setUp() {
        controller = GpsRideController(config)
        stats = TripStatsTracker()
        stats.start(
            scope = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.Unconfined),
            startedAtMillis = 1_000L,
        )
    }

    @Test
    fun rejectedTeleport_doesNotUpdateSpeedKmh() {
        val intervalMs = 1_500L
        val stepM = rideStepMeters(30f, intervalMs)
        var lat = 55.751
        val lon = 37.618
        var ts = 1_000L
        repeat(5) {
            acceptLocation(lat, lon, ts)
            lat += metersToLatDelta(stepM)
            ts += intervalMs
        }
        val speedBefore = controller.speedKmh.value
        assertTrue(speedBefore != null && speedBefore > 5f)

        val ghost = location(lat + metersToLatDelta(2_000.0), lon, ts)
        val rejectResult = controller.processRaw(ghost)
        assertTrue(
            rejectResult is GpsFilterResult.Rejected || rejectResult is GpsFilterResult.Ignored,
        )
        assertTrue(rejectResult !is GpsFilterResult.Accepted)

        assertEquals(speedBefore, controller.speedKmh.value)
    }

    @Test
    fun tripStats_rejectedTeleport_doesNotIncreaseMaxSpeedOrDistance() {
        val intervalMs = 1_500L
        val stepM = rideStepMeters(28f, intervalMs)
        var lat = 55.751
        val lon = 37.618
        var ts = 1_000L
        repeat(4) {
            onAccepted(lat, lon, ts)
            lat += metersToLatDelta(stepM)
            ts += intervalMs
        }
        val distanceBefore = stats.stats.value.distanceMeters
        val maxBefore = stats.stats.value.maxSpeedKmh

        controller.processRaw(location(lat + metersToLatDelta(1_500.0), lon, ts))

        assertEquals(distanceBefore, stats.stats.value.distanceMeters, 0.01)
        assertEquals(maxBefore, stats.stats.value.maxSpeedKmh, 0.01f)
    }

    @Test
    fun pauseResume_modeledByForceNewSegment_noDistanceAcrossSegments() {
        val intervalMs = 1_500L
        val stepM = rideStepMeters(32f, intervalMs)
        var lat = 55.751
        val lon = 37.618
        var ts = 1_000L
        val accepted = mutableListOf<AcceptedGpsPoint>()
        repeat(4) {
            accepted += onAccepted(lat, lon, ts)
            lat += metersToLatDelta(stepM)
            ts += intervalMs
        }

        controller.markExternalSegmentBreak()
        val afterPause = onAccepted(
            lat = accepted.last().latitude + metersToLatDelta(stepM),
            lon = accepted.last().longitude,
            ts = accepted.last().timestampMillis + 60_000L,
            newSegmentExpected = true,
        )
        accepted += afterPause

        val distance = TrackCsvParser.distanceMeters(accepted)
        val withinSeg0 = GeoDistance.distanceMeters(accepted[0], accepted[3])
        assertEquals(withinSeg0, distance, 1.0)
        assertEquals(1, afterPause.segmentId)
    }

    @Test
    fun csvReplay_restoresOnlyAcceptedPoints_noGhostInFile() {
        val intervalMs = 1_500L
        val stepM = rideStepMeters(25f, intervalMs)
        var lat = 55.751
        val lon = 37.618
        var ts = 1_000L
        val written = mutableListOf<AcceptedGpsPoint>()
        repeat(6) {
            written += onAccepted(lat, lon, ts)
            lat += metersToLatDelta(stepM)
            ts += intervalMs
        }
        controller.processRaw(location(lat + metersToLatDelta(3_000.0), lon, ts))

        val file = File.createTempFile("gps_test_", ".csv")
        file.writeText(buildCsv(written))

        val replayed = TrackCsvParser.parseAcceptedPoints(file)
        assertEquals(written.size, replayed.size)
        assertEquals(0, replayed.count { it.segmentId != 0 })
        file.delete()
    }

    @Test
    fun restoreFromCsv_thenGhost_rejected() {
        val points = listOf(
            acceptedPoint(55.751, 37.618, 1_000L, 0),
            acceptedPoint(55.7515, 37.6185, 2_500L, 0),
            acceptedPoint(55.752, 37.619, 20_000L, 1),
        )
        controller.restoreFromAcceptedPoints(points, segmentsCount = 2)

        val result = controller.processRaw(
            location(55.752 + metersToLatDelta(5_000.0), 37.619, 21_000L),
        )
        assertTrue(
            result is GpsFilterResult.Rejected || result is GpsFilterResult.Ignored,
        )
    }

    private fun acceptLocation(lat: Double, lon: Double, ts: Long) {
        val result = controller.processRaw(location(lat, lon, ts))
        assertTrue(result is GpsFilterResult.Accepted)
    }

    private fun onAccepted(
        lat: Double,
        lon: Double,
        ts: Long,
        newSegmentExpected: Boolean = false,
    ): AcceptedGpsPoint {
        val result = controller.processRaw(location(lat, lon, ts))
        assertTrue(result is GpsFilterResult.Accepted)
        val accepted = result as GpsFilterResult.Accepted
        if (newSegmentExpected) {
            assertTrue(accepted.newSegment)
        }
        stats.onAcceptedPoint(
            point = accepted.point,
            newSegment = accepted.newSegment,
            currentSpeedKmh = controller.speedKmh.value,
        )
        return accepted.point
    }

    private fun location(lat: Double, lon: Double, ts: Long): LocationSample =
        LocationSample(latitude = lat, longitude = lon, timestampMillis = ts, accuracyMeters = 10f)

    private fun acceptedPoint(lat: Double, lon: Double, ts: Long, segmentId: Int): AcceptedGpsPoint =
        AcceptedGpsPoint(
            latitude = lat,
            longitude = lon,
            timestampMillis = ts,
            accuracyMeters = 10f,
            derivedSpeedKmh = 25f,
            segmentId = segmentId,
        )

    private fun buildCsv(points: List<AcceptedGpsPoint>): String {
        val header = "gps_timestamp,segment_id,latitude,longitude,accuracy_m,derived_speed_kmh,bpm"
        val lines = points.map { p ->
            "1970-01-01T00:00:00Z,${p.segmentId},${p.latitude},${p.longitude},${p.accuracyMeters},${p.derivedSpeedKmh ?: ""},"
        }
        return (listOf(header) + lines).joinToString("\n")
    }

    private fun rideStepMeters(speedKmh: Float, intervalMs: Long): Double =
        speedKmh / 3.6f * (intervalMs / 1000.0)

    private fun metersToLatDelta(meters: Double): Double = meters / 111_000.0
}
