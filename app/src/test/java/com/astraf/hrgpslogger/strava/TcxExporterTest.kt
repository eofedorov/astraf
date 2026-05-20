package com.astraf.hrgpslogger.strava

import com.astraf.hrgpslogger.AcceptedGpsPoint
import com.astraf.hrgpslogger.TrackCsvSample
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class TcxExporterTest {

    @Test
    fun exportToFile_containsTimestampsHeartRateAndDistance() {
        val samples = listOf(
            sample(ts = 1_000L, lat = 55.751, lon = 37.618, segment = 0, bpm = 120),
            sample(ts = 2_500L, lat = 55.75101, lon = 37.61801, segment = 0, bpm = 122),
            sample(ts = 50_000L, lat = 55.75105, lon = 37.61805, segment = 1, bpm = 125),
            sample(ts = 51_500L, lat = 55.75106, lon = 37.61806, segment = 1, bpm = 126),
        )
        val output = File.createTempFile("ride", ".tcx")
        TcxExporter.exportToFile(samples, output)

        val xml = output.readText()
        assertTrue(xml.contains("<TrainingCenterDatabase"))
        assertTrue(xml.contains("<HeartRateBpm>"))
        assertTrue(xml.contains("<Value>120</Value>"))
        assertTrue(xml.contains("<Time>"))
        assertTrue(xml.contains("<LatitudeDegrees>55.751</LatitudeDegrees>"))
        assertEquals("hr_gps_123.tcx", TcxExporter.externalIdForCsv("hr_gps_123.csv"))
    }

    @Test
    fun cumulativeDistance_skipsSegmentBreak() {
        val samples = listOf(
            sample(ts = 1_000L, lat = 55.751, lon = 37.618, segment = 0, bpm = null),
            sample(ts = 2_500L, lat = 55.75101, lon = 37.61801, segment = 0, bpm = null),
            sample(ts = 50_000L, lat = 55.752, lon = 37.619, segment = 1, bpm = null),
            sample(ts = 51_500L, lat = 55.75201, lon = 37.61901, segment = 1, bpm = null),
        )
        val total = TcxExporter.cumulativeDistanceMeters(samples)
        val seg0 = distance(samples[0], samples[1])
        val seg1 = distance(samples[2], samples[3])
        assertEquals(seg0 + seg1, total, 0.5)
    }

    private fun sample(
        ts: Long,
        lat: Double,
        lon: Double,
        segment: Int,
        bpm: Int?,
    ): TrackCsvSample = TrackCsvSample(
        point = AcceptedGpsPoint(
            latitude = lat,
            longitude = lon,
            timestampMillis = ts,
            accuracyMeters = 10f,
            derivedSpeedKmh = null,
            segmentId = segment,
        ),
        bpm = bpm,
    )

    private fun distance(a: TrackCsvSample, b: TrackCsvSample): Double =
        com.astraf.hrgpslogger.GeoDistance.distanceMeters(a.point, b.point)
}
