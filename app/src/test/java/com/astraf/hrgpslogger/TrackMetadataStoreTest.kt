package com.astraf.hrgpslogger

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import java.io.File

class TrackMetadataStoreTest {

    @Test
    fun encodeDecode_roundTrip() {
        val metadata = TrackMetadata(
            totalClimbMeters = 142.5f,
            pointsWithAltitude = 120,
            pointsWithoutAltitude = 3,
        )
        val decoded = TrackMetadataStore.decode(TrackMetadataStore.encode(metadata))
        assertNotNull(decoded)
        assertEquals(142.5f, decoded!!.totalClimbMeters, 0.01f)
        assertEquals(120, decoded.pointsWithAltitude)
        assertEquals(3, decoded.pointsWithoutAltitude)
    }

    @Test
    fun saveToFile_loadFromFile_roundTrip() {
        val file = File.createTempFile("track_meta", ".json")
        val metadata = TrackMetadata(
            totalClimbMeters = 25f,
            pointsWithAltitude = 40,
            pointsWithoutAltitude = 1,
        )
        TrackMetadataStore.saveToFile(file, metadata)
        val loaded = TrackMetadataStore.loadFromFile(file)
        assertNotNull(loaded)
        assertEquals(25f, loaded!!.totalClimbMeters, 0.01f)
    }

    @Test
    fun loadFromFile_missing_returnsNull() {
        val file = File.createTempFile("missing_meta", ".json")
        file.delete()
        assertNull(TrackMetadataStore.loadFromFile(file))
    }

    @Test
    fun metadataFileName_appendsSuffix() {
        assertEquals(
            "hr_gps_123.metadata.json",
            TrackMetadataStore.metadataFileName("hr_gps_123.csv"),
        )
    }

    @Test
    fun encodeDecode_withDisplayName() {
        val metadata = TrackMetadata(
            totalClimbMeters = 10f,
            pointsWithAltitude = 5,
            pointsWithoutAltitude = 1,
            displayName = "Утренняя поездка",
        )
        val decoded = TrackMetadataStore.decode(TrackMetadataStore.encode(metadata))
        assertNotNull(decoded)
        assertEquals("Утренняя поездка", decoded!!.displayName)
    }

    @Test
    fun encodeDecode_withStravaActivityId() {
        val metadata = TrackMetadata(
            totalClimbMeters = 10f,
            pointsWithAltitude = 5,
            pointsWithoutAltitude = 1,
            stravaActivityId = 123456789L,
        )
        val decoded = TrackMetadataStore.decode(TrackMetadataStore.encode(metadata))
        assertNotNull(decoded)
        assertEquals(123456789L, decoded!!.stravaActivityId)
    }

    @Test
    fun decode_legacyWithoutDisplayName() {
        val text = """
            totalClimbMeters=10.0
            pointsWithAltitude=5
            pointsWithoutAltitude=1
        """.trimIndent()
        val decoded = TrackMetadataStore.decode(text)
        assertNotNull(decoded)
        assertNull(decoded!!.displayName)
    }
}
