package com.astraf.hrgpslogger

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class GpsTrackQualityProcessorTest {

    private lateinit var processor: GpsTrackQualityProcessor
    private lateinit var config: GpsFilterConfig
    private val acceptedPoints = mutableListOf<AcceptedGpsPoint>()

    @Before
    fun setUp() {
        config = BICYCLE_CONFIG
        processor = GpsTrackQualityProcessor(config)
        acceptedPoints.clear()
    }

    // --- existing smoke ---

    @Test
    fun accept_passesAltitudeToAcceptedPoint() {
        val result = processor.process(raw(lat = 55.751, lon = 37.618, ts = 1_000L, accuracy = 10f, altitude = 123.5))
        assertTrue(result is GpsFilterResult.Accepted)
        assertEquals(123.5, (result as GpsFilterResult.Accepted).point.altitudeMeters!!, 0.01)
    }

    @Test
    fun normalRide_acceptsPointsAndAccumulatesDistance() {
        rideSteps(speedKmh = 25f, steps = 3, startTs = 1_000L)
        assertEquals(3, processor.debugStats.acceptedPointsCount)
        assertEquals(1, processor.debugStats.segmentsCount)
    }

    @Test
    fun poorStartup_rejectsUntilGoodFix() {
        val bad = raw(lat = 55.75, lon = 37.61, ts = 1_000L, accuracy = 100f)
        val good = raw(lat = 55.751, lon = 37.618, ts = 2_000L, accuracy = 12f)

        assertRejected(processor.process(bad), GpsRejectReason.WAITING_FOR_FIRST_FIX)
        accept(good)
        assertEquals(1, processor.debugStats.acceptedPointsCount)
        assertTrue(processor.debugStats.rejectedWaitingForFirstFix >= 1)
    }

    @Test
    fun gpsJump_rejectsOutlier() {
        rideSteps(speedKmh = 25f, steps = 2)
        reject(
            raw(lat = 55.754, lon = 37.621, ts = 4_500L, accuracy = 10f),
            GpsRejectReason.IMPOSSIBLE_SPEED,
        )
    }

    @Test
    fun gpsGap_startsNewSegment() {
        accept(raw(lat = 55.751, lon = 37.618, ts = 1_000L, accuracy = 10f))
        val afterGap = acceptNewSegment(
            raw(lat = 55.75105, lon = 37.61805, ts = 50_000L, accuracy = 10f),
        )
        assertEquals(1, afterGap.segmentId)
        assertEquals(2, processor.debugStats.segmentsCount)
        assertEquals(1, processor.debugStats.gpsGapsCount)
    }

    @Test
    fun impossibleSpeed_rejectsPoint() {
        accept(raw(lat = 55.751, lon = 37.618, ts = 1_000L, accuracy = 10f))
        reject(
            raw(lat = 55.761, lon = 37.628, ts = 2_000L, accuracy = 10f),
            GpsRejectReason.IMPOSSIBLE_SPEED,
        )
    }

    @Test
    fun invalidTimestamp_rejectsNonMonotonicPoint() {
        accept(raw(lat = 55.751, lon = 37.618, ts = 5_000L, accuracy = 10f))
        reject(
            raw(lat = 55.75101, lon = 37.61801, ts = 4_000L, accuracy = 10f),
            GpsRejectReason.INVALID_TIMESTAMP,
        )
    }

    @Test
    fun tooFrequent_rejectsPoint() {
        accept(raw(lat = 55.751, lon = 37.618, ts = 1_000L, accuracy = 10f))
        reject(
            raw(lat = 55.751001, lon = 37.618001, ts = 1_200L, accuracy = 10f),
            GpsRejectReason.TOO_FREQUENT,
        )
    }

    @Test
    fun gpsJamming_continuousGhostTrack_rejectsAllDistantPoints() {
        val intervalMs = 1_500L
        val state = rideSteps(speedKmh = 30f, steps = 20, intervalMs = intervalMs)

        var ghostLat = state.lat + metersToLatDelta(10_000.0)
        var ghostTs = state.ts
        repeat(15) {
            reject(
                raw(lat = ghostLat, lon = state.lon, ts = ghostTs, accuracy = 10f),
                GpsRejectReason.IMPOSSIBLE_SPEED,
            )
            ghostLat += metersToLatDelta(rideStepMeters(30f, intervalMs))
            ghostTs += intervalMs
        }

        assertEquals(20, processor.debugStats.acceptedPointsCount)
        assertEquals(1, processor.debugStats.segmentsCount)
        assertEquals(15, processor.debugStats.rejectedByImpossibleSpeed)
    }

    @Test
    fun gpsJamming_ghostTrackAfterTimeGap_rejectsAllDistantPoints() {
        val intervalMs = 1_500L
        val state = rideSteps(speedKmh = 30f, steps = 20, intervalMs = intervalMs)

        var ghostLat = state.lat + metersToLatDelta(10_000.0)
        var ghostTs = state.ts + config.maxGapWithoutNewSegmentMs + 1_000L
        repeat(15) {
            reject(
                raw(lat = ghostLat, lon = state.lon, ts = ghostTs, accuracy = 10f),
                GpsRejectReason.IMPOSSIBLE_SPEED,
            )
            ghostLat += metersToLatDelta(rideStepMeters(30f, intervalMs))
            ghostTs += intervalMs
        }

        assertEquals(20, processor.debugStats.acceptedPointsCount)
        assertEquals(1, processor.debugStats.segmentsCount)
        assertEquals(15, processor.debugStats.rejectedByImpossibleSpeed)
    }

    @Test
    fun distanceCountsOnlyInsideSegment() {
        val points = listOf(
            raw(lat = 55.751, lon = 37.618, ts = 1_000L, accuracy = 10f),
            raw(lat = 55.75101, lon = 37.61801, ts = 2_500L, accuracy = 10f),
            raw(lat = 55.75150, lon = 37.61850, ts = 20_000L, accuracy = 10f),
            raw(lat = 55.75151, lon = 37.61851, ts = 21_500L, accuracy = 10f),
        )
        val acceptedPoints = points.mapNotNull { point ->
            (processor.process(point) as? GpsFilterResult.Accepted)?.point
        }
        val distance = TrackCsvParser.distanceMeters(acceptedPoints)
        val seg1 = GeoDistance.distanceMeters(acceptedPoints[0], acceptedPoints[1])
        val seg2 = GeoDistance.distanceMeters(acceptedPoints[2], acceptedPoints[3])
        assertEquals(seg1 + seg2, distance, 0.5)
    }

    // --- bicycle speed policy (max 60 km/h in tests) ---

    @Test
    fun bicycleSpeed_acceptsTypicalRideSpeeds() {
        listOf(0f, 3f, 25f, 35f, 52f).forEach { speed ->
            val p = GpsTrackQualityProcessor(BICYCLE_CONFIG)
            val intervalMs = 1_500L
            val stepM = rideStepMeters(speed, intervalMs)
            var lat = 55.751
            val lon = 37.618
            var ts = 1_000L
            p.process(raw(lat, lon, ts, 10f))
            lat += metersToLatDelta(stepM)
            ts += intervalMs
            val result = p.process(raw(lat, lon, ts, 10f))
            assertTrue("speed=$speed km/h should be accepted", result is GpsFilterResult.Accepted)
        }
    }

    @Test
    fun bicycleSpeed_acceptsExactlyAtLimit() {
        val p = GpsTrackQualityProcessor(BICYCLE_CONFIG)
        acceptWithProcessor(p, raw(55.751, 37.618, 1_000L, 10f))
        // Чуть ниже 60 км/ч: плоская аппроксимация шага даёт чуть большую haversine-скорость.
        val stepM = rideStepMeters(59f, 1_500L)
        val result = p.process(
            raw(55.751 + metersToLatDelta(stepM), 37.618, 2_500L, 10f),
        )
        assertTrue(result is GpsFilterResult.Accepted)
        val derived = (result as GpsFilterResult.Accepted).point.derivedSpeedKmh
        assertNotNull(derived)
        assertTrue("derived=$derived", derived!! <= 60f)
    }

    @Test
    fun bicycleSpeed_rejectsAboveLimit() {
        val p = GpsTrackQualityProcessor(BICYCLE_CONFIG)
        acceptWithProcessor(p, raw(55.751, 37.618, 1_000L, 10f))
        val stepM = rideStepMeters(70f, 1_500L)
        val result = p.process(
            raw(55.751 + metersToLatDelta(stepM), 37.618, 2_500L, 10f),
        )
        assertRejected(result, GpsRejectReason.IMPOSSIBLE_SPEED)
    }

    @Test
    fun bicycleSpeed_teleportWithNormalInterval_rejectedBySpeedNotFrequency() {
        accept(raw(55.751, 37.618, 1_000L, 10f))
        val result = processor.process(
            raw(55.751 + metersToLatDelta(500.0), 37.618, 2_500L, 10f),
        )
        assertRejected(result, GpsRejectReason.IMPOSSIBLE_SPEED)
        assertEquals(0, processor.debugStats.rejectedByTooFrequent)
    }

    // --- teleports ---

    @Test
    fun fastRide_teleportThenResumeFromLastRealPoint() {
        val intervalMs = 1_500L
        val state = rideSteps(speedKmh = 52f, steps = 10, intervalMs = intervalMs)
        val lastReal = acceptedPoints.last()

        reject(
            raw(
                lat = state.lat + metersToLatDelta(800.0),
                lon = state.lon,
                ts = state.ts,
                accuracy = 10f,
            ),
            GpsRejectReason.IMPOSSIBLE_SPEED,
        )

        val resumeStepM = rideStepMeters(52f, intervalMs)
        val resumed = accept(
            raw(
                lat = lastReal.latitude + metersToLatDelta(resumeStepM),
                lon = lastReal.longitude,
                ts = lastReal.timestampMillis + intervalMs,
                accuracy = 10f,
            ),
        )
        assertEquals(lastReal.segmentId, resumed.segmentId)
        val expectedSpeed = GeoDistance.distanceMeters(lastReal, resumed) / (intervalMs / 1000.0) * 3.6
        assertEquals(expectedSpeed.toFloat(), resumed.derivedSpeedKmh!!, 2f)
    }

    @Test
    fun slowRide_smallTeleport_rejects() {
        val intervalMs = 1_500L
        val state = rideSteps(speedKmh = 8f, steps = 5, intervalMs = intervalMs)
        reject(
            raw(
                lat = state.lat + metersToLatDelta(55.0),
                lon = state.lon,
                ts = state.ts,
                accuracy = 10f,
            ),
            GpsRejectReason.IMPOSSIBLE_SPEED,
        )
        assertEquals(5, processor.debugStats.acceptedPointsCount)
    }

    @Test
    fun trafficLight_ghostDoesNotMoveAnchor_nearbyResumeAccepted() {
        val lon = 37.618
        var lat = 55.751
        var ts = 1_000L
        accept(raw(lat, lon, ts, 10f))
        repeat(3) {
            ts += 2_000L
            accept(raw(lat + metersToLatDelta(0.5), lon, ts, 10f))
        }
        val anchor = acceptedPoints.last()

        reject(
            raw(lat + metersToLatDelta(2_000.0), lon, ts + 2_000L, 10f),
            GpsRejectReason.IMPOSSIBLE_SPEED,
        )

        val nearby = accept(
            raw(lat + metersToLatDelta(1.0), lon, ts + 4_000L, 10f),
        )
        assertEquals(anchor.latitude, nearby.latitude, 0.00005)
        assertEquals(anchor.segmentId, nearby.segmentId)
    }

    @Test
    fun teleportZigzag_allRejected_anchorUnchanged() {
        val state = rideSteps(speedKmh = 30f, steps = 5)
        val beforeCount = processor.debugStats.acceptedPointsCount
        val anchor = acceptedPoints.last()

        repeat(5) { i ->
            reject(
                raw(
                    lat = state.lat + metersToLatDelta(500.0 * (i + 1)),
                    lon = state.lon + metersToLonDelta(300.0 * (i + 1), state.lat),
                    ts = state.ts + (i + 1) * 1_500L,
                    accuracy = 10f,
                ),
                GpsRejectReason.IMPOSSIBLE_SPEED,
            )
        }

        assertEquals(beforeCount, processor.debugStats.acceptedPointsCount)
        assertEquals(anchor, acceptedPoints.last())
    }

    @Test
    fun teleport_sameTimestamp_rejectsInvalidTimestamp() {
        val p = acceptedPoints.lastOrNull() ?: accept(raw(55.751, 37.618, 5_000L, 10f))
        reject(
            raw(p.latitude + metersToLatDelta(100.0), p.longitude, p.timestampMillis, 10f),
            GpsRejectReason.INVALID_TIMESTAMP,
        )
    }

    // --- jamming / signal loss ---

    @Test
    fun startup_missingAccuracy_waitsForFirstFix() {
        val result = processor.process(raw(55.751, 37.618, 1_000L, null))
        assertRejected(result, GpsRejectReason.WAITING_FOR_FIRST_FIX)
        assertTrue(processor.debugStats.rejectedByMissingAccuracy >= 1)
        accept(raw(55.751, 37.618, 2_000L, 12f))
    }

    @Test
    fun ride_poorAccuracySeries_rejectedThenValidNewSegment() {
        val state = rideSteps(speedKmh = 25f, steps = 5)
        repeat(3) {
            reject(
                raw(state.lat, state.lon, state.ts + 1_000L, 80f),
                GpsRejectReason.POOR_ACCURACY,
            )
        }
        val afterGap = acceptNewSegment(
            raw(
                lat = state.lat + metersToLatDelta(rideStepMeters(25f, 1_500L)),
                lon = state.lon,
                ts = state.ts + config.maxGapWithoutNewSegmentMs + 2_000L,
                accuracy = 10f,
            ),
        )
        assertEquals(1, afterGap.segmentId)
        assertEquals(2, processor.debugStats.segmentsCount)
    }

    @Test
    fun longSilence_ghostTrajectory_rejectedEvenWithLargeGap() {
        val state = rideSteps(speedKmh = 30f, steps = 8)
        reject(
            raw(
                lat = state.lat + metersToLatDelta(5_000.0),
                lon = state.lon,
                ts = state.ts + config.maxGapWithoutNewSegmentMs + 5_000L,
                accuracy = 10f,
            ),
            GpsRejectReason.IMPOSSIBLE_SPEED,
        )
        assertEquals(8, processor.debugStats.acceptedPointsCount)
        assertEquals(1, processor.debugStats.segmentsCount)
    }

    @Test
    fun longSilence_nearbyPoint_acceptedAsNewSegment_noCrossSegmentDistance() {
        val state = rideSteps(speedKmh = 20f, steps = 6)
        val nearby = acceptNewSegment(
            raw(
                lat = state.lat + metersToLatDelta(3.0),
                lon = state.lon,
                ts = state.ts + config.maxGapWithoutNewSegmentMs + 3_000L,
                accuracy = 10f,
            ),
        )
        assertEquals(1, nearby.segmentId)
        val distance = TrackCsvParser.distanceMeters(acceptedPoints)
        assertTrue(distance < 200.0)
    }

    @Test
    fun longSilence_atTrafficLight_nearbyAccepted_distantRejected() {
        accept(raw(55.751, 37.618, 1_000L, 10f))
        val gapTs = 1_000L + config.maxGapWithoutNewSegmentMs + 5_000L
        val nearby = acceptNewSegment(raw(55.751, 37.618, gapTs, 10f))
        assertEquals(1, nearby.segmentId)

        reject(
            raw(55.751 + metersToLatDelta(3_000.0), 37.618, gapTs + 2_000L, 10f),
            GpsRejectReason.IMPOSSIBLE_SPEED,
        )
    }

    // --- pause / restore (processor-level) ---

    @Test
    fun manualPause_forceNewSegment_preventsDistanceAcrossPause() {
        val state = rideSteps(speedKmh = 30f, steps = 5)
        processor.forceNewSegment()
        val afterPause = accept(
            raw(
                lat = state.lat + metersToLatDelta(rideStepMeters(30f, 1_500L)),
                lon = state.lon,
                ts = state.ts + 10_000L,
                accuracy = 10f,
            ),
        )
        assertEquals(1, afterPause.segmentId)
        val distanceInNewSegment = TrackCsvParser.distanceMeters(
            acceptedPoints.filter { it.segmentId == afterPause.segmentId },
        )
        assertEquals(0.0, distanceInNewSegment, 0.5)
        val lastBeforePause = acceptedPoints.last { it.segmentId != afterPause.segmentId }
        assertEquals(0.0, TrackCsvParser.distanceMeters(listOf(lastBeforePause, afterPause)), 0.5)
    }

    @Test
    fun restoreFromAcceptedPoints_ghostRejected_anchorIsLastSaved() {
        val state = rideSteps(speedKmh = 28f, steps = 12)
        val saved = acceptedPoints.toList()
        val restored = GpsTrackQualityProcessor(BICYCLE_CONFIG)
        restored.restoreFromAcceptedPoints(saved, TrackCsvParser.segmentsCount(saved))

        rejectWithProcessor(
            restored,
            raw(state.lat + metersToLatDelta(4_000.0), state.lon, state.ts + 2_000L, 10f),
            GpsRejectReason.IMPOSSIBLE_SPEED,
        )
        assertEquals(saved.size, restored.debugStats.acceptedPointsCount)
    }

    @Test
    fun restoreFromAcceptedPoints_longGapNearbyAcceptedAsNewSegment() {
        val state = rideSteps(speedKmh = 25f, steps = 8)
        val saved = acceptedPoints.toList()
        val restored = GpsTrackQualityProcessor(BICYCLE_CONFIG)
        restored.restoreFromAcceptedPoints(saved, 1)

        val result = restored.process(
            raw(
                lat = state.lat + metersToLatDelta(2.0),
                lon = state.lon,
                ts = state.ts + config.maxGapWithoutNewSegmentMs + 1_000L,
                accuracy = 10f,
            ),
        )
        assertTrue(result is GpsFilterResult.Accepted)
        assertTrue((result as GpsFilterResult.Accepted).newSegment)
    }

    @Test
    fun restoreFromMultipleSegments_continuesSegmentId() {
        val p1 = accept(raw(55.751, 37.618, 1_000L, 10f))
        acceptNewSegment(raw(55.752, 37.619, 20_000L, 10f))
        val saved = acceptedPoints.toList()
        val restored = GpsTrackQualityProcessor(BICYCLE_CONFIG)
        restored.restoreFromAcceptedPoints(saved, 2)

        val next = restored.process(raw(55.75201, 37.61901, 21_500L, 10f))
        assertTrue(next is GpsFilterResult.Accepted)
        assertEquals(1, (next as GpsFilterResult.Accepted).point.segmentId)
    }

    @Test
    fun restoreEmpty_acceptsFirstGoodFix() {
        val restored = GpsTrackQualityProcessor(BICYCLE_CONFIG)
        restored.restoreFromAcceptedPoints(emptyList(), 0)
        val first = restored.process(raw(55.751, 37.618, 1_000L, 12f))
        assertTrue(first is GpsFilterResult.Accepted)
        assertTrue((first as GpsFilterResult.Accepted).newSegment)
    }

    @Test
    fun rejectedGlitch_doesNotInflateDerivedSpeedOnNextValidPoint() {
        val state = rideSteps(speedKmh = 35f, steps = 6)
        reject(
            raw(state.lat + metersToLatDelta(900.0), state.lon, state.ts, 10f),
            GpsRejectReason.IMPOSSIBLE_SPEED,
        )
        val lastReal = acceptedPoints.last()
        val intervalMs = 1_500L
        val next = accept(
            raw(
                lat = lastReal.latitude + metersToLatDelta(rideStepMeters(35f, intervalMs)),
                lon = lastReal.longitude,
                ts = lastReal.timestampMillis + intervalMs,
                accuracy = 10f,
            ),
        )
        assertTrue(next.derivedSpeedKmh!! in 20f..45f)
        assertTrue(processor.debugStats.maxCalculatedSpeedKmh <= 45f)
    }

    // --- helpers ---

    private data class RideState(val lat: Double, val lon: Double, val ts: Long)

    private fun rideSteps(
        speedKmh: Float,
        steps: Int,
        intervalMs: Long = 1_500L,
        startLat: Double = 55.751,
        startLon: Double = 37.618,
        startTs: Long = 1_000L,
    ): RideState {
        var lat = startLat
        var lon = startLon
        var ts = startTs
        val stepM = rideStepMeters(speedKmh, intervalMs)
        repeat(steps) {
            accept(raw(lat, lon, ts, 10f))
            lat += metersToLatDelta(stepM)
            ts += intervalMs
        }
        return RideState(lat, lon, ts)
    }

    private fun accept(point: RawGpsPoint): AcceptedGpsPoint {
        val result = processor.process(point)
        assertTrue("expected accept: $point", result is GpsFilterResult.Accepted)
        val accepted = (result as GpsFilterResult.Accepted).point
        acceptedPoints.add(accepted)
        return accepted
    }

    private fun acceptNewSegment(point: RawGpsPoint): AcceptedGpsPoint {
        val result = processor.process(point)
        assertTrue(result is GpsFilterResult.Accepted)
        val accepted = result as GpsFilterResult.Accepted
        assertTrue(accepted.newSegment)
        acceptedPoints.add(accepted.point)
        return accepted.point
    }

    private fun reject(point: RawGpsPoint, reason: GpsRejectReason) {
        assertRejected(processor.process(point), reason)
    }

    private fun acceptWithProcessor(p: GpsTrackQualityProcessor, point: RawGpsPoint) {
        assertTrue(p.process(point) is GpsFilterResult.Accepted)
    }

    private fun rejectWithProcessor(p: GpsTrackQualityProcessor, point: RawGpsPoint, reason: GpsRejectReason) {
        assertRejected(p.process(point), reason)
    }

    private fun assertRejected(result: GpsFilterResult, reason: GpsRejectReason) {
        assertTrue("expected reject $reason but got $result", result is GpsFilterResult.Rejected)
        assertEquals(reason, (result as GpsFilterResult.Rejected).reason)
    }

    private fun rideStepMeters(speedKmh: Float, intervalMs: Long): Double =
        speedKmh / 3.6f * (intervalMs / 1000.0)

    private fun metersToLatDelta(meters: Double): Double = meters / 111_000.0

    private fun metersToLonDelta(meters: Double, lat: Double): Double =
        meters / (111_000.0 * kotlin.math.cos(Math.toRadians(lat)))

    private fun raw(
        lat: Double,
        lon: Double,
        ts: Long,
        accuracy: Float?,
        altitude: Double? = null,
    ): RawGpsPoint = RawGpsPoint(
        latitude = lat,
        longitude = lon,
        timestampMillis = ts,
        accuracyMeters = accuracy,
        altitudeMeters = altitude,
    )

    companion object {
        /** Продуктовая норма велосипедиста: до ~55 км/ч, в тестах допуск 60 км/ч. */
        val BICYCLE_CONFIG: GpsFilterConfig = GpsFilterConfig(maxReasonableSpeedKmh = 60f)
    }
}
