package com.astraf.hrgpslogger

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import java.io.File

class TrackCsvParserTest {

    @Test
    fun parseSample_readsBpmFromSeventhColumn() {
        val line = "2024-01-15T10:00:00Z,0,55.751,37.618,10.0,12.5,142"
        val sample = TrackCsvParser.parseSample(line)

        assertNotNull(sample)
        assertEquals(142, sample?.bpm)
        assertEquals(55.751, sample?.point?.latitude ?: 0.0, 0.0001)
    }

    @Test
    fun parseSample_emptyBpmIsNull() {
        val line = "2024-01-15T10:00:00Z,0,55.751,37.618,10.0,12.5,"
        val sample = TrackCsvParser.parseSample(line)
        assertNotNull(sample)
        assertNull(sample?.bpm)
    }

    @Test
    fun parseSamples_fromFile() {
        val file = File.createTempFile("track", ".csv")
        file.writeText(
            """
            gps_timestamp,segment_id,latitude,longitude,accuracy_m,derived_speed_kmh,bpm
            2024-01-15T10:00:00Z,0,55.751,37.618,10.0,12.5,130
            2024-01-15T10:00:02Z,0,55.75101,37.61801,10.0,13.0,131
            """.trimIndent(),
        )

        val samples = TrackCsvParser.parseSamples(file)
        assertEquals(2, samples.size)
        assertEquals(130, samples[0].bpm)
        assertEquals(131, samples[1].bpm)
    }
}
