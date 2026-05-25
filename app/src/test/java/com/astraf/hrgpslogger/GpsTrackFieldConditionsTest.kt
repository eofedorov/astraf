package com.astraf.hrgpslogger

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import kotlin.math.abs
import kotlin.math.sin

/**
 * «Полевые» сценарии: GPS среднего качества (degraded), постоянный поперечный зигзаг
 * вокруг правдоподобного движения велосипеда — типично для городской застройки / отражений.
 */
class GpsTrackFieldConditionsTest {

    private lateinit var processor: GpsTrackQualityProcessor
    private val config = GpsTrackQualityProcessorTest.BICYCLE_CONFIG
    private val acceptedPoints = mutableListOf<AcceptedGpsPoint>()

    @Before
    fun setUp() {
        processor = GpsTrackQualityProcessor(config)
        acceptedPoints.clear()
    }

    @Test
    fun urbanZigzag_mediumAccuracy_acceptsRideWithoutSegmentBreak() {
        val summary = simulateNoisyZigzagRide(
            speedKmh = 22f,
            steps = 50,
            intervalMs = 1_500L,
            jitterMeters = 8.0,
            accuracyMeters = FIELD_MEDIUM_ACCURACY_M,
        )

        assertEquals(50, summary.acceptedCount)
        assertEquals(1, processor.debugStats.segmentsCount)
        assertTrue(
            "rejected=${processor.debugStats.rejectedPointsCount} ignored=${processor.debugStats.ignoredPointsCount}",
            processor.debugStats.rejectedPointsCount + processor.debugStats.ignoredPointsCount <= 3,
        )
        assertTrue(summary.degradedShare >= 0.85f)
        assertTrue(
            "stream=${processor.debugStats.streamState}",
            processor.debugStats.streamState == GpsStreamState.GOOD ||
                processor.debugStats.streamState == GpsStreamState.DEGRADED,
        )
    }

    @Test
    fun urbanZigzag_mediumAccuracy_distanceNotExplodedByJitter() {
        val summary = simulateNoisyZigzagRide(
            speedKmh = 25f,
            steps = 60,
            intervalMs = 1_500L,
            jitterMeters = 10.0,
            accuracyMeters = 24f,
        )

        val tracked = TrackCsvParser.distanceMeters(acceptedPoints)
        val ratio = tracked / summary.idealPathMeters

        assertTrue(
            "tracked=$tracked ideal=${summary.idealPathMeters} ratio=$ratio",
            ratio in 0.85..1.40,
        )
        assertTrue(
            "raw zigzag path=${summary.rawZigzagPathMeters} should exceed ideal",
            summary.rawZigzagPathMeters > summary.idealPathMeters * 1.15,
        )
        assertTrue(
            "pipeline should smooth below raw zigzag cumulative",
            tracked < summary.rawZigzagPathMeters * 0.92,
        )
    }

    @Test
    fun urbanZigzag_varyingAccuracy_staysDegradedNotBad() {
        val speedKmh = 18f
        val intervalMs = 1_500L
        val stepM = rideStepMeters(speedKmh, intervalMs)
        var lat = 55.752
        var lon = 37.620
        var ts = 10_000L
        val accuracies = floatArrayOf(18f, 22f, 26f, 20f, 24f, 28f, 19f, 23f)

        accept(raw(lat, lon, ts, 18f))
        repeat(30) { i ->
            lat += metersToLatDelta(stepM)
            val jitterSign = if (i % 2 == 0) 1.0 else -1.0
            lon += metersToLonDelta(7.0 * jitterSign, lat)
            ts += intervalMs
            val acc = accuracies[i % accuracies.size]
            val result = processor.process(raw(lat, lon, ts, acc))
            assertTrue("step $i: $result", result is GpsFilterResult.Accepted)
            val accepted = result as GpsFilterResult.Accepted
            assertEquals(GpsPointQuality.DEGRADED, accepted.metadata.quality)
            assertTrue(accepted.metadata.trust < 0.85f)
            acceptedPoints.add(accepted.point)
        }

        assertEquals(1, processor.debugStats.segmentsCount)
        assertTrue(processor.debugStats.degradedAcceptedCount >= 28)
    }

    @Test
    fun urbanZigzag_alternatingLateralNoise_doesNotTriggerOutlierStreakBreak() {
        simulateNoisyZigzagRide(
            speedKmh = 28f,
            steps = 40,
            intervalMs = 1_500L,
            jitterMeters = 12.0,
            accuracyMeters = 21f,
        )

        assertEquals(0, processor.debugStats.gpsGapsCount)
        assertEquals(1, processor.debugStats.segmentsCount)
        assertTrue(processor.debugStats.ignoredPointsCount < config.outlierStreakThreshold * 2)
    }

    @Test
    fun urbanZigzag_comparedToCleanRide_hasLowerTrustAndHigherNoise() {
        val noisy = runRideOnFreshProcessor { p, points ->
            simulateOnProcessor(
                proc = p,
                sink = points,
                speedKmh = 24f,
                steps = 35,
                intervalMs = 1_500L,
                jitterMeters = 6.0,
                accuracyMeters = 23f,
                wobbleAccuracy = true,
            )
        }
        val clean = runRideOnFreshProcessor { p, points ->
            simulateOnProcessor(
                proc = p,
                sink = points,
                speedKmh = 24f,
                steps = 35,
                intervalMs = 1_500L,
                jitterMeters = 0.0,
                accuracyMeters = 10f,
                wobbleAccuracy = false,
            )
        }

        assertTrue("noisy accepted=${noisy.acceptedCount}", noisy.acceptedCount >= 28)
        assertTrue("clean accepted=${clean.acceptedCount}", clean.acceptedCount >= 28)
        assertTrue(
            "noisyTrust=${noisy.avgTrust} cleanTrust=${clean.avgTrust} " +
                "noisyNoise=${noisy.avgMeasurementNoise} cleanNoise=${clean.avgMeasurementNoise} " +
                "noisyDeg=${noisy.degradedShare} cleanDeg=${clean.degradedShare}",
            noisy.avgTrust < clean.avgTrust - 0.05f &&
                noisy.avgMeasurementNoise > clean.avgMeasurementNoise * 1.15f &&
                noisy.degradedShare >= 0.75f &&
                clean.degradedShare <= 0.05f,
        )
    }

    @Test
    fun urbanZigzag_tripStats_distanceCloserToIdealThanRawZigzag() {
        val points = mutableListOf<AcceptedGpsPoint>()
        val summary = simulateOnProcessor(
            proc = processor,
            sink = points,
            speedKmh = 20f,
            steps = 45,
            intervalMs = 1_500L,
            jitterMeters = 8.0,
            accuracyMeters = FIELD_MEDIUM_ACCURACY_M,
            wobbleAccuracy = true,
        )
        acceptedPoints.addAll(points)

        val stats = TripStatsTracker()
        stats.start(
            scope = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.Unconfined),
            startedAtMillis = 1_000L,
        )
        points.forEachIndexed { index, point ->
            val newSegment = index == 0 || point.segmentId != points[index - 1].segmentId
            stats.onAcceptedPoint(point, newSegment, currentSpeedKmh = point.derivedSpeedKmh)
        }

        val tripDistance = stats.stats.value.distanceMeters
        val ratio = tripDistance / summary.idealPathMeters
        assertTrue(
            "trip=$tripDistance ideal=${summary.idealPathMeters}",
            ratio in 0.85..1.45,
        )
        assertTrue(tripDistance < summary.rawZigzagPathMeters * 0.95)
    }

    // --- simulation ---

    private data class FieldRideSummary(
        val acceptedCount: Int,
        val idealPathMeters: Double,
        val rawZigzagPathMeters: Double,
        val degradedShare: Float,
        val avgTrust: Float,
        val avgMeasurementNoise: Float,
    )

    private fun simulateNoisyZigzagRide(
        speedKmh: Float,
        steps: Int,
        intervalMs: Long,
        jitterMeters: Double,
        accuracyMeters: Float,
    ): FieldRideSummary = simulateOnProcessor(
        proc = processor,
        sink = acceptedPoints,
        speedKmh = speedKmh,
        steps = steps,
        intervalMs = intervalMs,
        jitterMeters = jitterMeters,
        accuracyMeters = accuracyMeters,
        wobbleAccuracy = true,
    )

    private fun simulateOnProcessor(
        proc: GpsTrackQualityProcessor,
        sink: MutableList<AcceptedGpsPoint>,
        speedKmh: Float,
        steps: Int,
        intervalMs: Long,
        jitterMeters: Double,
        accuracyMeters: Float,
        wobbleAccuracy: Boolean = false,
    ): FieldRideSummary {
        val stepM = rideStepMeters(speedKmh, intervalMs)
        var lat = 55.751
        var lon = 37.618
        var ts = 1_000L

        var idealMeters = 0.0
        var rawPathMeters = 0.0
        var prevRawLat = lat
        var prevRawLon = lon
        var degradedCount = 0
        var trustSum = 0f
        var noiseSum = 0f
        var acceptedCount = 0

        repeat(steps) { i ->
            lat += metersToLatDelta(stepM)
            idealMeters += stepM

            val phase = i * 0.9
            val lateral = jitterMeters * sin(phase) + jitterMeters * 0.35 * sin(phase * 2.3)
            lon += metersToLonDelta(lateral, lat)

            val acc = if (wobbleAccuracy) {
                (accuracyMeters + (i % 5 - 2) * 1.5f).coerceIn(17f, 29f)
            } else {
                accuracyMeters
            }
            val point = raw(lat, lon, ts, acc)

            rawPathMeters += GeoDistance.distanceMeters(prevRawLat, prevRawLon, lat, lon)
            prevRawLat = lat
            prevRawLon = lon

            when (val result = proc.process(point)) {
                is GpsFilterResult.Accepted -> {
                    sink.add(result.point)
                    acceptedCount++
                    if (result.metadata.quality == GpsPointQuality.DEGRADED) {
                        degradedCount++
                    }
                    trustSum += result.metadata.trust
                    noiseSum += result.metadata.measurementNoise
                }
                else -> Unit
            }
            ts += intervalMs
        }

        return FieldRideSummary(
            acceptedCount = acceptedCount,
            idealPathMeters = idealMeters,
            rawZigzagPathMeters = rawPathMeters,
            degradedShare = if (acceptedCount == 0) 0f else degradedCount.toFloat() / acceptedCount,
            avgTrust = if (acceptedCount == 0) 0f else trustSum / acceptedCount,
            avgMeasurementNoise = if (acceptedCount == 0) 0f else noiseSum / acceptedCount,
        )
    }

    private fun runRideOnFreshProcessor(
        block: (GpsTrackQualityProcessor, MutableList<AcceptedGpsPoint>) -> FieldRideSummary,
    ): FieldRideSummary {
        val p = GpsTrackQualityProcessor(config)
        val points = mutableListOf<AcceptedGpsPoint>()
        return block(p, points)
    }

    private fun accept(point: RawGpsPoint): AcceptedGpsPoint {
        val result = processor.process(point)
        assertTrue("expected accept: $result", result is GpsFilterResult.Accepted)
        val accepted = (result as GpsFilterResult.Accepted).point
        acceptedPoints.add(accepted)
        return accepted
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
        accuracy: Float,
    ): RawGpsPoint = RawGpsPoint(
        latitude = lat,
        longitude = lon,
        timestampMillis = ts,
        accuracyMeters = accuracy,
    )

    companion object {
        /** Среднее полевое качество: хуже good (15 м), но не bad (50 м). */
        const val FIELD_MEDIUM_ACCURACY_M = 23f
    }
}
