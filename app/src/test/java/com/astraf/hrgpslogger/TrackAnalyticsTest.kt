package com.astraf.hrgpslogger

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TrackAnalyticsTest {

    @Test
    fun computeDuration_twoPoints() {
        val points = listOf(
            point(ts = 0L, lat = 55.0, lon = 37.0, segment = 0),
            point(ts = 215_000L, lat = 55.01, lon = 37.01, segment = 0),
        )
        assertEquals(215_000L, TrackAnalytics.computeDurationMillis(points))
    }

    @Test
    fun computeDistance_singlePoint_returnsZero() {
        val points = listOf(point(ts = 0L, lat = 55.0, lon = 37.0, segment = 0))
        assertEquals(0.0, TrackAnalytics.computeDistanceMeters(points)!!, 0.01)
    }

    @Test
    fun computeHeartRateStats_withReadings() {
        val samples = listOf(
            sample(ts = 0L, bpm = 120),
            sample(ts = 1_000L, bpm = 140),
        )
        val (avg, max) = TrackAnalytics.computeHeartRateStats(samples)
        assertEquals(130, avg)
        assertEquals(140, max)
    }

    @Test
    fun buildSegments_splitsBySegmentId() {
        val points = listOf(
            point(ts = 0L, lat = 55.0, lon = 37.0, segment = 0),
            point(ts = 1_000L, lat = 55.01, lon = 37.01, segment = 0),
            point(ts = 50_000L, lat = 55.1, lon = 37.1, segment = 1),
        )
        val segments = TrackAnalytics.buildSegments(points)
        assertEquals(2, segments.size)
        assertEquals(2, segments[0].points.size)
        assertEquals(1, segments[1].points.size)
    }

    @Test
    fun buildSpeedSeries_requiresAtLeastTwoPoints() {
        val samples = listOf(sample(ts = 0L, speed = 12f))
        assertTrue(TrackAnalytics.buildSpeedSeries(samples).isEmpty())
        val two = listOf(
            sample(ts = 0L, speed = 10f),
            sample(ts = 2_000L, speed = 15f),
        )
        assertTrue(TrackAnalytics.buildSpeedSeries(two).size >= 2)
    }

    @Test
    fun buildSpeedSeries_startsAtZeroWithZeroSpeed() {
        val two = listOf(
            sample(ts = 0L, speed = 10f),
            sample(ts = 2_000L, speed = 15f),
        )
        val series = TrackAnalytics.buildSpeedSeries(two)
        assertEquals(0L, series.first().elapsedMillis)
        assertEquals(0f, series.first().value, 0.01f)
        assertTrue(series.all { it.value >= 0f })
        assertTrue(series.all { it.elapsedMillis >= 0L })
    }

    @Test
    fun buildSpeedSeries_clampsNegativeDerivedSpeed() {
        val two = listOf(
            sample(ts = 0L, speed = 0f),
            sample(ts = 1_000L, speed = -5f),
        )
        val series = TrackAnalytics.buildSpeedSeries(two)
        assertTrue(series.all { it.value >= 0f })
    }

    @Test
    fun resolveStatus_insufficientWhenNoPoints() {
        assertEquals(
            TrackDetailStatus.InsufficientData,
            TrackAnalytics.resolveStatus(
                fileExists = true,
                readable = true,
                pointCount = 0,
                isActive = false,
            ),
        )
    }

    @Test
    fun resolveStatus_recordingActive() {
        assertEquals(
            TrackDetailStatus.RecordingActive,
            TrackAnalytics.resolveStatus(
                fileExists = true,
                readable = true,
                pointCount = 5,
                isActive = true,
            ),
        )
    }

    @Test
    fun computeMaxSpeed_usesDerivedSpeed() {
        val points = listOf(
            point(ts = 0L, lat = 55.0, lon = 37.0, segment = 0, speed = 10f),
            point(ts = 2_000L, lat = 55.001, lon = 37.001, segment = 0, speed = 32.1f),
        )
        assertEquals(32.1f, TrackAnalytics.computeMaxSpeedKmh(points)!!, 0.1f)
    }

    @Test
    fun computeAverageSpeed_nullForSinglePoint() {
        assertNull(TrackAnalytics.computeAverageSpeedKmh(listOf(point(ts = 0L, lat = 55.0, lon = 37.0, segment = 0))))
    }

    @Test
    fun computeMovingTime_countsOnlyMovingIntervals() {
        val points = listOf(
            point(ts = 0L, lat = 55.0, lon = 37.0, segment = 0, speed = 20f),
            point(ts = 60_000L, lat = 55.001, lon = 37.001, segment = 0, speed = 20f),
            point(ts = 120_000L, lat = 55.002, lon = 37.002, segment = 0, speed = 0.5f),
        )
        assertEquals(60_000L, TrackAnalytics.computeMovingTimeMillis(points))
    }

    private fun point(
        ts: Long,
        lat: Double,
        lon: Double,
        segment: Int,
        speed: Float? = null,
    ): AcceptedGpsPoint = AcceptedGpsPoint(
        latitude = lat,
        longitude = lon,
        timestampMillis = ts,
        accuracyMeters = 10f,
        derivedSpeedKmh = speed,
        segmentId = segment,
    )

    private fun sample(ts: Long, bpm: Int? = null, speed: Float? = 12f): TrackCsvSample =
        TrackCsvSample(
            point = point(ts = ts, lat = 55.751, lon = 37.618, segment = 0, speed = speed),
            bpm = bpm,
        )
}
