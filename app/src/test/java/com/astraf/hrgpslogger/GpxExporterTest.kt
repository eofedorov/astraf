package com.astraf.hrgpslogger

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class GpxExporterTest {

    @Test
    fun exportToFile_containsTrackPointsAndTime() {
        val samples = listOf(
            sample(ts = 1_000L, lat = 55.751, lon = 37.618, segment = 0, bpm = 120, alt = 105.0),
            sample(ts = 2_500L, lat = 55.75101, lon = 37.61801, segment = 0, bpm = 122),
        )
        val output = File.createTempFile("ride", ".gpx")
        GpxExporter.exportToFile(samples, output)

        val xml = output.readText()
        assertTrue(xml.contains("<gpx version=\"1.1\""))
        assertTrue(xml.contains("<trkpt lat=\"55.751\" lon=\"37.618\">"))
        assertTrue(xml.contains("<ele>105.0</ele>"))
        assertTrue(xml.contains("<time>"))
        assertTrue(xml.contains("<hr>120</hr>"))
        assertEquals("hr_gps_123.gpx", GpxExporter.fileNameForCsv("hr_gps_123.csv"))
    }

    @Test
    fun exportToFile_splitsSegments() {
        val samples = listOf(
            sample(ts = 1_000L, lat = 55.751, lon = 37.618, segment = 0),
            sample(ts = 50_000L, lat = 55.752, lon = 37.619, segment = 1),
        )
        val output = File.createTempFile("ride_segments", ".gpx")
        GpxExporter.exportToFile(samples, output)
        val xml = output.readText()
        assertTrue(xml.contains("</trkseg>"))
        assertEquals(2, xml.split("<trkseg>").size - 1)
    }

    private fun sample(
        ts: Long,
        lat: Double,
        lon: Double,
        segment: Int,
        bpm: Int? = null,
        alt: Double? = null,
    ): TrackCsvSample = TrackCsvSample(
        point = AcceptedGpsPoint(
            latitude = lat,
            longitude = lon,
            timestampMillis = ts,
            accuracyMeters = 10f,
            derivedSpeedKmh = null,
            segmentId = segment,
            altitudeMeters = alt,
        ),
        bpm = bpm,
    )
}
