package com.astraf.hrgpslogger

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class TrackRepositoryTest {

    @Test
    fun parseTrackSummary_emptyFile_returnsSummaryWithoutCrash() {
        val file = File.createTempFile("hr_gps_1000", ".csv")
        file.writeText("")
        val summary = TrackRepository.parseTrackSummary(file, metadata = null)
        assertEquals(0, summary.pointCount)
        assertNull(summary.distanceMeters)
    }

    @Test
    fun parseTrackSummary_singleGpsPoint_hasRoutePreview() {
        val file = tempCsv(
            """
            gps_timestamp,segment_id,latitude,longitude,accuracy_m,derived_speed_kmh,bpm
            2024-06-01T08:00:00Z,0,55.751,37.618,10.0,12.0,
            """.trimIndent(),
        )
        val summary = TrackRepository.parseTrackSummary(file, metadata = null)
        assertEquals(1, summary.pointCount)
        assertEquals(1, summary.routePoints.size)
        assertNull(summary.averageHeartRateBpm)
    }

    @Test
    fun parseTrackSummary_withHeartRateAndMetadata() {
        val file = tempCsv(
            """
            gps_timestamp,segment_id,latitude,longitude,accuracy_m,derived_speed_kmh,bpm
            2024-06-01T08:00:00Z,0,55.751,37.618,10.0,10.0,140
            2024-06-01T08:01:00Z,0,55.752,37.619,10.0,20.0,150
            """.trimIndent(),
        )
        val metadata = TrackMetadata(
            totalClimbMeters = 42f,
            pointsWithAltitude = 2,
            pointsWithoutAltitude = 0,
            displayName = "Тест",
            stravaActivityId = 999L,
        )
        val summary = TrackRepository.parseTrackSummary(file, metadata)
        assertEquals("Тест", summary.displayName)
        assertEquals(999L, summary.stravaActivityId)
        assertEquals(145, summary.averageHeartRateBpm)
        assertEquals(42f, summary.totalClimbMeters)
        assertTrue(summary.routePoints.size >= 2)
    }

    @Test
    fun parseTrackSummary_twoFiles_sortedByStartedAtDescending() {
        val older = tempNamedCsv(
            "hr_gps_1000.csv",
            """
            gps_timestamp,segment_id,latitude,longitude,accuracy_m,derived_speed_kmh,bpm
            2024-01-01T08:00:00Z,0,55.751,37.618,10.0,10.0,
            """.trimIndent(),
        )
        val newer = tempNamedCsv(
            "hr_gps_5000.csv",
            """
            gps_timestamp,segment_id,latitude,longitude,accuracy_m,derived_speed_kmh,bpm
            2024-02-01T08:00:00Z,0,55.751,37.618,10.0,10.0,
            """.trimIndent(),
        )
        val summaries = listOf(
            TrackRepository.parseTrackSummary(older, null),
            TrackRepository.parseTrackSummary(newer, null),
        ).sortedByDescending { it.startedAtMillis }
        assertEquals("hr_gps_5000.csv", summaries.first().fileName)
    }

    private fun tempCsv(body: String): File {
        val file = File.createTempFile("hr_gps_test", ".csv")
        file.writeText(body)
        return file
    }

    private fun tempNamedCsv(name: String, body: String): File {
        val file = File.createTempFile("track_repo", ".tmp")
        val target = File(file.parentFile, name)
        file.delete()
        target.writeText(body)
        return target
    }
}
