package com.astraf.hrgpslogger

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

class StatisticsCalculatorTest {

    private val zone = ZoneId.of("Europe/Moscow")
    private val today = LocalDate.of(2026, 5, 21)

    @Test
    fun isEligibleForStats_excludesActiveAndEmpty() {
        assertFalse(StatisticsCalculator.isEligibleForStats(activeTrack()))
        assertFalse(StatisticsCalculator.isEligibleForStats(track(distance = 0.0, points = 5)))
        assertFalse(StatisticsCalculator.isEligibleForStats(track(distance = 1000.0, points = 1)))
        assertTrue(StatisticsCalculator.isEligibleForStats(track(distance = 5000.0, points = 10)))
    }

    @Test
    fun tracksInPeriod_weekFiltersCurrentWeek() {
        val monday = today.with(java.time.DayOfWeek.MONDAY)
        val inWeek = track(
            startedAt = monday.atStartOfDay(zone).toInstant().toEpochMilli(),
            distance = 10_000.0,
        )
        val lastWeek = track(
            startedAt = monday.minusDays(7).atStartOfDay(zone).toInstant().toEpochMilli(),
            distance = 10_000.0,
        )
        val result = StatisticsCalculator.tracksInPeriod(
            listOf(inWeek, lastWeek),
            StatsPeriod.Week,
            zone,
            today,
        )
        assertEquals(1, result.size)
        assertEquals(inWeek.fileName, result.first().fileName)
    }

    @Test
    fun buildSummary_weightedAverageSpeed() {
        val a = track(distance = 20_000.0, movingTime = 3_600_000L, avgSpeed = 20f)
        val b = track(distance = 40_000.0, movingTime = 3_600_000L, avgSpeed = 40f)
        val summary = StatisticsCalculator.buildSummary(listOf(a, b))
        assertEquals(60_000.0, summary.totalDistanceMeters, 0.01)
        assertEquals(7_200_000L, summary.totalMovingTimeMillis)
        assertEquals(30f, summary.averageMovingSpeedKmh!!, 0.5f)
    }

    @Test
    fun buildActivityBuckets_weekHasSevenColumns() {
        val buckets = StatisticsCalculator.buildActivityBuckets(
            emptyList(),
            StatsPeriod.Week,
            zone,
            today,
        )
        assertEquals(7, buckets.size)
        assertEquals("Пн", buckets.first().label)
        assertEquals("Вс", buckets.last().label)
    }

    @Test
    fun buildDistribution_percentagesSumTo100() {
        val tracks = listOf(
            track(distance = 10_000.0),
            track(distance = 30_000.0),
            track(distance = 60_000.0),
            track(distance = 120_000.0),
        )
        val buckets = StatisticsCalculator.buildDistribution(tracks)
        assertEquals(4, buckets.size)
        assertEquals(1, buckets[0].rideCount)
        assertEquals(1, buckets[3].rideCount)
    }

    @Test
    fun buildYearHeatmapLayout_has31ColumnsPerMonth() {
        val mayRide = track(
            startedAt = LocalDate.of(2026, 5, 21)
                .atStartOfDay(zone)
                .toInstant()
                .toEpochMilli(),
        )
        val snapshot = StatisticsCalculator.buildSnapshot(
            listOf(mayRide),
            StatsPeriod.Year,
            zone,
            today.atStartOfDay(zone).plusHours(12).let {
                java.time.ZonedDateTime.of(it.toLocalDateTime(), zone)
            },
        )
        assertEquals(12, snapshot.heatmapLayout.weeks.size)
        snapshot.heatmapLayout.weeks.forEach { row ->
            assertEquals(31, row.size)
        }
        val mayRow = snapshot.heatmapLayout.weeks[4]
        assertEquals(1, mayRow.count { (it?.rideCount ?: 0) > 0 })
    }

    @Test
    fun buildBestRides_fastestRequiresMinDistance() {
        val shortFast = track(distance = 15_000.0, avgSpeed = 45f)
        val longFast = track(distance = 25_000.0, avgSpeed = 35f, fileName = "long.csv")
        val best = StatisticsCalculator.buildBestRides(listOf(shortFast, longFast))
        assertEquals("long.csv", best.fastest?.fileName)
    }

    private fun activeTrack() = track(distance = 5000.0, isActive = true)

    private fun track(
        fileName: String = "hr_gps_${System.nanoTime()}.csv",
        startedAt: Long = today.atStartOfDay(zone).toInstant().toEpochMilli(),
        distance: Double = 25_000.0,
        movingTime: Long = 3_600_000L,
        avgSpeed: Float = 25f,
        maxSpeed: Float = 40f,
        climb: Float = 100f,
        points: Int = 50,
        isActive: Boolean = false,
    ): TrackSummary = TrackSummary(
        fileName = fileName,
        filePath = "/tmp/$fileName",
        startedAtMillis = startedAt,
        pointCount = points,
        durationMillis = movingTime + 600_000L,
        movingTimeMillis = movingTime,
        distanceMeters = distance,
        averageSpeedKmh = avgSpeed,
        maxSpeedKmh = maxSpeed,
        averageHeartRateBpm = null,
        totalClimbMeters = climb,
        displayName = null,
        routePoints = listOf(
            RoutePreviewPoint(55.75, 37.61),
            RoutePreviewPoint(55.76, 37.62),
        ),
        stravaActivityId = null,
        isActive = isActive,
    )
}
