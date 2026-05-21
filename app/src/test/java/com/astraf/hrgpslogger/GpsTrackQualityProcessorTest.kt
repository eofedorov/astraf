package com.astraf.hrgpslogger

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class GpsTrackQualityProcessorTest {

    private lateinit var processor: GpsTrackQualityProcessor
    private val config = GpsFilterConfig()

    @Before
    fun setUp() {
        processor = GpsTrackQualityProcessor(config)
    }

    @Test
    fun accept_passesAltitudeToAcceptedPoint() {
        val result = processor.process(
            raw(lat = 55.751, lon = 37.618, ts = 1_000L, accuracy = 10f, altitude = 123.5),
        )
        assertTrue(result is GpsFilterResult.Accepted)
        assertEquals(123.5, (result as GpsFilterResult.Accepted).point.altitudeMeters!!, 0.01)
    }

    @Test
    fun normalRide_acceptsPointsAndAccumulatesDistance() {
        val points = listOf(
            raw(lat = 55.751, lon = 37.618, ts = 1_000L, accuracy = 10f),
            raw(lat = 55.75101, lon = 37.61801, ts = 2_500L, accuracy = 12f),
            raw(lat = 55.75102, lon = 37.61802, ts = 4_000L, accuracy = 8f),
        )

        val accepted = points.map { processor.process(it) }
        assertTrue(accepted.all { it is GpsFilterResult.Accepted })
        assertEquals(3, processor.debugStats.acceptedPointsCount)
        assertEquals(1, processor.debugStats.segmentsCount)
    }

    @Test
    fun poorStartup_rejectsUntilGoodFix() {
        val bad = raw(lat = 55.75, lon = 37.61, ts = 1_000L, accuracy = 100f)
        val good = raw(lat = 55.751, lon = 37.618, ts = 2_000L, accuracy = 12f)

        val first = processor.process(bad)
        val second = processor.process(good)

        assertTrue(first is GpsFilterResult.Rejected)
        assertEquals(GpsRejectReason.WAITING_FOR_FIRST_FIX, (first as GpsFilterResult.Rejected).reason)
        assertTrue(second is GpsFilterResult.Accepted)
        assertEquals(1, processor.debugStats.acceptedPointsCount)
        assertTrue(processor.debugStats.rejectedWaitingForFirstFix >= 1)
    }

    @Test
    fun gpsJump_rejectsOutlier() {
        accept(raw(lat = 55.751, lon = 37.618, ts = 1_000L, accuracy = 10f))
        accept(raw(lat = 55.75101, lon = 37.61801, ts = 2_500L, accuracy = 10f))

        val jump = raw(lat = 55.754, lon = 37.621, ts = 4_500L, accuracy = 10f)
        val result = processor.process(jump)

        assertTrue(result is GpsFilterResult.Rejected)
        assertEquals(GpsRejectReason.IMPOSSIBLE_SPEED, (result as GpsFilterResult.Rejected).reason)
    }

    @Test
    fun gpsGap_startsNewSegment() {
        accept(raw(lat = 55.751, lon = 37.618, ts = 1_000L, accuracy = 10f))
        val afterGap = processor.process(
            raw(lat = 55.75105, lon = 37.61805, ts = 50_000L, accuracy = 10f),
        )

        assertTrue(afterGap is GpsFilterResult.Accepted)
        val accepted = afterGap as GpsFilterResult.Accepted
        assertTrue(accepted.newSegment)
        assertEquals(1, accepted.point.segmentId)
        assertEquals(2, processor.debugStats.segmentsCount)
        assertEquals(1, processor.debugStats.gpsGapsCount)
    }

    @Test
    fun impossibleSpeed_rejectsPoint() {
        accept(raw(lat = 55.751, lon = 37.618, ts = 1_000L, accuracy = 10f))

        val fast = raw(lat = 55.761, lon = 37.628, ts = 2_000L, accuracy = 10f)
        val result = processor.process(fast)

        assertTrue(result is GpsFilterResult.Rejected)
        assertEquals(GpsRejectReason.IMPOSSIBLE_SPEED, (result as GpsFilterResult.Rejected).reason)
    }

    @Test
    fun invalidTimestamp_rejectsNonMonotonicPoint() {
        accept(raw(lat = 55.751, lon = 37.618, ts = 5_000L, accuracy = 10f))

        val result = processor.process(raw(lat = 55.75101, lon = 37.61801, ts = 4_000L, accuracy = 10f))
        assertTrue(result is GpsFilterResult.Rejected)
        assertEquals(GpsRejectReason.INVALID_TIMESTAMP, (result as GpsFilterResult.Rejected).reason)
    }

    @Test
    fun tooFrequent_rejectsPoint() {
        accept(raw(lat = 55.751, lon = 37.618, ts = 1_000L, accuracy = 10f))

        val result = processor.process(raw(lat = 55.751001, lon = 37.618001, ts = 1_200L, accuracy = 10f))
        assertTrue(result is GpsFilterResult.Rejected)
        assertEquals(GpsRejectReason.TOO_FREQUENT, (result as GpsFilterResult.Rejected).reason)
    }

    @Test
    fun gpsJamming_continuousGhostTrack_rejectsAllDistantPoints() {
        val intervalMs = 1_500L
        val stepM = rideStepMeters(speedKmh = 30f, intervalMs = intervalMs)
        val lon = 37.618

        var ts = 1_000L
        var lat = 55.751
        repeat(20) {
            accept(raw(lat = lat, lon = lon, ts = ts, accuracy = 10f))
            lat += metersToLatDelta(stepM)
            ts += intervalMs
        }

        var ghostLat = lat + metersToLatDelta(10_000.0)
        var ghostTs = ts
        repeat(15) {
            val result = processor.process(
                raw(lat = ghostLat, lon = lon, ts = ghostTs, accuracy = 10f),
            )
            assertTrue("ts=$ghostTs: ожидали отклонение далёкой точки", result is GpsFilterResult.Rejected)
            assertEquals(
                GpsRejectReason.IMPOSSIBLE_SPEED,
                (result as GpsFilterResult.Rejected).reason,
            )
            ghostLat += metersToLatDelta(stepM)
            ghostTs += intervalMs
        }

        assertEquals(20, processor.debugStats.acceptedPointsCount)
        assertEquals(1, processor.debugStats.segmentsCount)
        assertEquals(15, processor.debugStats.rejectedByImpossibleSpeed)
    }

    @Test
    fun gpsJamming_ghostTrackAfterTimeGap_rejectsAllDistantPoints() {
        val intervalMs = 1_500L
        val stepM = rideStepMeters(speedKmh = 30f, intervalMs = intervalMs)
        val lon = 37.618

        var ts = 1_000L
        var lat = 55.751
        repeat(20) {
            accept(raw(lat = lat, lon = lon, ts = ts, accuracy = 10f))
            lat += metersToLatDelta(stepM)
            ts += intervalMs
        }

        var ghostLat = lat + metersToLatDelta(10_000.0)
        var ghostTs = ts + config.maxGapWithoutNewSegmentMs + 1_000L
        repeat(15) {
            val result = processor.process(
                raw(lat = ghostLat, lon = lon, ts = ghostTs, accuracy = 10f),
            )
            assertTrue("ts=$ghostTs: ожидали отклонение далёкой точки", result is GpsFilterResult.Rejected)
            assertEquals(
                GpsRejectReason.IMPOSSIBLE_SPEED,
                (result as GpsFilterResult.Rejected).reason,
            )
            ghostLat += metersToLatDelta(stepM)
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

    private fun accept(point: RawGpsPoint): AcceptedGpsPoint {
        val result = processor.process(point)
        assertTrue(result is GpsFilterResult.Accepted)
        return (result as GpsFilterResult.Accepted).point
    }

    private fun rideStepMeters(speedKmh: Float, intervalMs: Long): Double =
        speedKmh / 3.6f * (intervalMs / 1000.0)

    private fun metersToLatDelta(meters: Double): Double = meters / 111_000.0

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
}
