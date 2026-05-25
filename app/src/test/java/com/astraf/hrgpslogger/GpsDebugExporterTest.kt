package com.astraf.hrgpslogger

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import java.io.File

class GpsDebugExporterTest {

    @Test
    fun parseJsonl_andAssemble_producesMonolithicJson() {
        val file = File.createTempFile("gps_debug", ".json")
        val config = GpsProcessingConfig()
        file.writeText(
            buildString {
                appendLine(
                    GpsDebugJsonCodec.encodeHeaderLine(
                        csvFileName = "hr_gps_1000.csv",
                        startedAtMillis = 1000L,
                        processingConfig = config,
                    ),
                )
                val sample = LocationSample(55.75, 37.62, 1_000L, accuracyMeters = 10f)
                val raw = RawGpsPoint.from(sample)
                val accepted = GpsFilterResult.Accepted(
                    point = AcceptedGpsPoint(
                        latitude = 55.75,
                        longitude = 37.62,
                        timestampMillis = 1_000L,
                        accuracyMeters = 10f,
                        derivedSpeedKmh = 20f,
                        segmentId = 0,
                    ),
                    newSegment = false,
                    bridged = false,
                    metadata = GpsPipelineMetadata(
                        quality = GpsPointQuality.GOOD,
                        trust = 0.8f,
                        measurementNoise = 10f,
                        streamState = GpsStreamState.GOOD,
                        segmentAction = GpsSegmentAction.APPEND_CURRENT,
                        reason = GpsDecisionReason.ACCEPTED_GOOD_ACCURACY,
                    ),
                    raw = raw,
                )
                appendLine(GpsDebugJsonCodec.encodeEventLine(0, sample, accepted, heartRateBpm = 130))
                appendLine(
                    GpsDebugJsonCodec.encodeFooterLine(
                        GpsDebugJsonCodec.summaryFromStats(
                            GpsDebugStats(rawPointsCount = 1, acceptedPointsCount = 1, segmentsCount = 1),
                        ),
                    ),
                )
            },
        )

        val parsed = GpsDebugExporter.parseJsonl(file)
        assertNotNull(parsed)
        assertEquals(1, parsed!!.eventLines.size)

        val document = GpsDebugJsonCodec.assembleDocument(
            headerLine = parsed.header,
            eventLines = parsed.eventLines,
            summary = parsed.summary!!,
        )
        val root = JSONObject(document)
        assertEquals(1, root.getJSONArray("events").length())
        assertEquals("hr_gps_1000.csv", root.getJSONObject("ride").getString("csvFileName"))
        assertEquals("accepted", root.getJSONArray("events").getJSONObject(0).getJSONObject("decision").getString("type"))
    }

    @Test
    fun countEventLines_ignoresHeaderAndFooter() {
        val file = File.createTempFile("gps_debug_count", ".json")
        file.writeText(
            """
            ${GpsDebugJsonCodec.encodeHeaderLine("hr_gps_1.csv", 1L, GpsProcessingConfig())}
            ${GpsDebugJsonCodec.encodeFooterLine(GpsDebugJsonCodec.summaryFromStats(GpsDebugStats()))}
            """.trimIndent() + "\n" +
                GpsDebugJsonCodec.encodeEventLine(
                    0,
                    LocationSample(1.0, 2.0, 3L, 4f),
                    GpsFilterResult.Ignored(
                        metadata = GpsPipelineMetadata(
                            quality = GpsPointQuality.GOOD,
                            trust = 1f,
                            measurementNoise = 1f,
                            streamState = GpsStreamState.GOOD,
                            segmentAction = GpsSegmentAction.IGNORE,
                            reason = GpsDecisionReason.IGNORED_TEMPORARY_NOISE,
                        ),
                        raw = RawGpsPoint(1.0, 2.0, 3L, 4f),
                    ),
                    null,
                ),
        )
        assertEquals(1, GpsDebugJsonCodec.countEventLines(file))
    }
}
