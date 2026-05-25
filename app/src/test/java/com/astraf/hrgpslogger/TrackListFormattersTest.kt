package com.astraf.hrgpslogger

import com.astraf.hrgpslogger.ui.DayPart
import com.astraf.hrgpslogger.ui.dayPartFromHour
import com.astraf.hrgpslogger.ui.formatRideCardHeader
import com.astraf.hrgpslogger.ui.formatRideHumanDate
import com.astraf.hrgpslogger.ui.formatTrackMonthSectionTitle
import com.astraf.hrgpslogger.ui.groupTracksByMonth
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime

class TrackListFormattersTest {

    private val zone = ZoneId.of("Europe/Moscow")

    @Test
    fun dayPartFromHour_boundaries() {
        assertEquals(DayPart.Night, dayPartFromHour(0))
        assertEquals(DayPart.Night, dayPartFromHour(5))
        assertEquals(DayPart.Morning, dayPartFromHour(6))
        assertEquals(DayPart.Morning, dayPartFromHour(11))
        assertEquals(DayPart.Day, dayPartFromHour(12))
        assertEquals(DayPart.Day, dayPartFromHour(17))
        assertEquals(DayPart.Evening, dayPartFromHour(18))
        assertEquals(DayPart.Evening, dayPartFromHour(23))
    }

    @Test
    fun formatRideCardHeader_matchesHumanDate() {
        val now = ZonedDateTime.of(2026, 5, 21, 20, 0, 0, 0, zone)
        val ride = now.withHour(14).withMinute(35).toInstant().toEpochMilli()
        assertEquals("Сегодня днём", formatRideCardHeader(ride, zone, now))
    }

    @Test
    fun formatRideHumanDate_todayAndYesterday() {
        val now = ZonedDateTime.of(2026, 5, 21, 20, 0, 0, 0, zone)
        val todayMorning = now.withHour(8).toInstant().toEpochMilli()
        val yesterdayAfternoon = now.minusDays(1).withHour(14).toInstant().toEpochMilli()

        assertEquals("Сегодня утром", formatRideHumanDate(todayMorning, zone, now))
        assertEquals("Вчера днём", formatRideHumanDate(yesterdayAfternoon, zone, now))
    }

    @Test
    fun formatRideHumanDate_otherDay() {
        val now = ZonedDateTime.of(2026, 5, 21, 12, 0, 0, 0, zone)
        val ride = ZonedDateTime.of(2026, 5, 10, 19, 30, 0, 0, zone).toInstant().toEpochMilli()
        assertEquals("10 мая вечером", formatRideHumanDate(ride, zone, now))
    }

    @Test
    fun formatTrackMonthSectionTitle_currentAndPastYear() {
        val now = LocalDate.of(2026, 5, 21)
        val currentYear = ZonedDateTime.of(2026, 4, 5, 10, 0, 0, 0, zone).toInstant().toEpochMilli()
        val pastYear = ZonedDateTime.of(2024, 12, 3, 10, 0, 0, 0, zone).toInstant().toEpochMilli()

        assertEquals("Апрель", formatTrackMonthSectionTitle(currentYear, zone, now))
        assertEquals("Декабрь 2024", formatTrackMonthSectionTitle(pastYear, zone, now))
    }

    @Test
    fun groupTracksByMonth_sortsNewestFirst() {
        val older = sampleTrack("a.csv", startedAtMillis = 1_000L)
        val newer = sampleTrack("b.csv", startedAtMillis = 2_000L)
        val sections = groupTracksByMonth(listOf(older, newer), zone)
        assertEquals(1, sections.size)
        assertEquals("b.csv", sections.first().tracks.first().fileName)
    }

    @Test
    fun toRoutePreview_downsamplesLargeTracks() {
        val points = (0 until 200).map { index ->
            AcceptedGpsPoint(
                latitude = 55.0 + index * 0.0001,
                longitude = 37.0,
                timestampMillis = index.toLong(),
                accuracyMeters = 5f,
                derivedSpeedKmh = null,
                segmentId = 0,
                altitudeMeters = null,
            )
        }
        val preview = points.toRoutePreview(maxPoints = 50)
        assertTrue(preview.size <= 52)
        assertEquals(points.first().latitude, preview.first().latitude, 0.0001)
        assertEquals(points.last().latitude, preview.last().latitude, 0.0001)
    }

    private fun sampleTrack(fileName: String, startedAtMillis: Long) = TrackSummary(
        fileName = fileName,
        filePath = "/tmp/$fileName",
        startedAtMillis = startedAtMillis,
        pointCount = 1,
        durationMillis = 0L,
        distanceMeters = 0.0,
        averageSpeedKmh = null,
        maxSpeedKmh = null,
        averageHeartRateBpm = null,
        totalClimbMeters = null,
        displayName = null,
        routePoints = emptyList(),
        stravaActivityId = null,
        isActive = false,
    )
}
