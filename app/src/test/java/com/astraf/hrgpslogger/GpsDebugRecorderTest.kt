package com.astraf.hrgpslogger

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File

class GpsDebugRecorderTest {

    private lateinit var filesDir: File
    private lateinit var recorder: GpsDebugRecorder
    private val csvFileName = "hr_gps_1700000000000.csv"

    @Before
    fun setUp() {
        filesDir = File.createTempFile("gps_debug_test_dir", "").apply {
            delete()
            mkdirs()
        }
        recorder = GpsDebugRecorder(filesDir)
        recorder.configure(GpsProcessingConfig())
        GpsDebugStore.debugFile(filesDir, csvFileName).delete()
    }

    @After
    fun tearDown() {
        filesDir.deleteRecursively()
    }

    @Test
    fun append_finalize_writesHeaderEventsFooter() {
        recorder.openBlocking(csvFileName)
        appendSample(ts = 1_000L)
        appendSample(ts = 2_000L)
        recorder.finalizeFooterBlocking(
            csvFileName = csvFileName,
            summary = GpsDebugJsonCodec.summaryFromStats(
                GpsDebugStats(rawPointsCount = 2, acceptedPointsCount = 2, segmentsCount = 1),
            ),
        )

        val file = GpsDebugStore.debugFile(filesDir, csvFileName)
        assertTrue(file.exists())
        assertEquals(2, GpsDebugJsonCodec.countEventLines(file))
        val parsed = GpsDebugExporter.parseJsonl(file)
        assertNotNull(parsed?.summary)
    }

    @Test
    fun resume_continuesEventIndex() {
        recorder.openBlocking(csvFileName)
        appendSample(ts = 1_000L)
        recorder.close()
        Thread.sleep(100)

        recorder.openBlocking(csvFileName)
        appendSample(ts = 2_000L)
        recorder.finalizeFooterBlocking(
            csvFileName = csvFileName,
            summary = GpsDebugJsonCodec.summaryFromStats(GpsDebugStats(rawPointsCount = 2)),
        )

        assertEquals(2, GpsDebugJsonCodec.countEventLines(GpsDebugStore.debugFile(filesDir, csvFileName)))
    }

    @Test
    fun delete_removesSidecar() {
        recorder.openBlocking(csvFileName)
        appendSample(ts = 1_000L)
        recorder.flushBlocking()
        assertTrue(recorder.hasDebugFile(csvFileName))

        recorder.deleteBlocking(csvFileName)
        assertFalse(GpsDebugStore.debugFile(filesDir, csvFileName).exists())
    }

    private fun appendSample(ts: Long) {
        val sample = LocationSample(55.75, 37.62, ts, accuracyMeters = 10f)
        val raw = RawGpsPoint.from(sample)
        val result = GpsFilterResult.Accepted(
            point = AcceptedGpsPoint(
                latitude = 55.75,
                longitude = 37.62,
                timestampMillis = ts,
                accuracyMeters = 10f,
                derivedSpeedKmh = 20f,
                segmentId = 0,
            ),
            newSegment = false,
            bridged = false,
            metadata = GpsPipelineMetadata(
                quality = GpsPointQuality.GOOD,
                trust = 0.9f,
                measurementNoise = 10f,
                streamState = GpsStreamState.GOOD,
                segmentAction = GpsSegmentAction.APPEND_CURRENT,
                reason = GpsDecisionReason.ACCEPTED_GOOD_ACCURACY,
            ),
            raw = raw,
        )
        recorder.appendEvent(csvFileName, sample, result, heartRateBpm = null)
        Thread.sleep(50)
    }
}
