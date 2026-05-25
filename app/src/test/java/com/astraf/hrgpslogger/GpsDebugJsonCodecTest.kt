package com.astraf.hrgpslogger

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
class GpsDebugJsonCodecTest {

    @Test
    fun encodeEventLine_accepted_containsDecisionFields() {
        val sample = LocationSample(
            latitude = 55.75,
            longitude = 37.62,
            timestampMillis = 1_000L,
            accuracyMeters = 10f,
            altitudeMeters = 120.0,
            speedMps = 5f,
            bearingDegrees = 90f,
        )
        val metadata = GpsPipelineMetadata(
            quality = GpsPointQuality.GOOD,
            trust = 0.9f,
            measurementNoise = 12f,
            streamState = GpsStreamState.GOOD,
            segmentAction = GpsSegmentAction.APPEND_CURRENT,
            reason = GpsDecisionReason.ACCEPTED_GOOD_ACCURACY,
        )
        val result = GpsFilterResult.Accepted(
            point = AcceptedGpsPoint(
                latitude = 55.7501,
                longitude = 37.6201,
                timestampMillis = 1_000L,
                accuracyMeters = 10f,
                derivedSpeedKmh = 18f,
                segmentId = 0,
            ),
            newSegment = false,
            bridged = false,
            metadata = metadata,
            raw = RawGpsPoint.from(sample),
        )

        val line = GpsDebugJsonCodec.encodeEventLine(0, sample, result, heartRateBpm = 142)
        val obj = JSONObject(line)
        assertEquals("event", obj.getString("type"))
        assertEquals(0, obj.getInt("index"))
        assertEquals(142, obj.getInt("heartRateBpm"))
        assertEquals("accepted", obj.getJSONObject("decision").getString("type"))
        assertEquals("GOOD", obj.getJSONObject("decision").getJSONObject("metadata").getString("quality"))
    }

    @Test
    fun encodeEventLine_rejected_containsRejectReason() {
        val sample = LocationSample(55.75, 37.62, 2_000L, accuracyMeters = 80f)
        val metadata = GpsPipelineMetadata(
            quality = GpsPointQuality.BAD,
            trust = 0.1f,
            measurementNoise = 50f,
            streamState = GpsStreamState.GOOD,
            segmentAction = GpsSegmentAction.REJECT,
            reason = GpsDecisionReason.REJECTED_BAD_ACCURACY,
        )
        val result = GpsFilterResult.Rejected(
            reason = GpsRejectReason.POOR_ACCURACY,
            metadata = metadata,
            raw = RawGpsPoint.from(sample),
        )
        val obj = JSONObject(GpsDebugJsonCodec.encodeEventLine(1, sample, result, heartRateBpm = null))
        assertEquals("rejected", obj.getJSONObject("decision").getString("type"))
        assertEquals("POOR_ACCURACY", obj.getJSONObject("decision").getString("rejectReason"))
        assertTrue(!obj.has("heartRateBpm"))
    }

    @Test
    fun assembleDocument_producesEventsArray() {
        val header = JSONObject(
            GpsDebugJsonCodec.encodeHeaderLine(
                csvFileName = "hr_gps_1000.csv",
                startedAtMillis = 1000L,
                processingConfig = GpsProcessingConfig(),
            ),
        )
        val sample = LocationSample(55.75, 37.62, 1_000L, accuracyMeters = 10f)
        val event = JSONObject(
            GpsDebugJsonCodec.encodeEventLine(
                index = 0,
                sample = sample,
                result = GpsFilterResult.Ignored(
                    metadata = GpsPipelineMetadata(
                        quality = GpsPointQuality.DEGRADED,
                        trust = 0.4f,
                        measurementNoise = 20f,
                        streamState = GpsStreamState.DEGRADED,
                        segmentAction = GpsSegmentAction.IGNORE,
                        reason = GpsDecisionReason.IGNORED_TEMPORARY_NOISE,
                    ),
                    raw = RawGpsPoint.from(sample),
                ),
                heartRateBpm = null,
            ),
        )
        val summary = GpsDebugJsonCodec.summaryFromStats(
            GpsDebugStats(rawPointsCount = 1, ignoredPointsCount = 1),
        )
        val doc = JSONObject(GpsDebugJsonCodec.assembleDocument(header, listOf(event), summary))
        assertEquals(1, doc.getJSONArray("events").length())
        assertEquals("json", doc.getString("format"))
        assertEquals(0, doc.getJSONObject("summary").getInt("acceptedPointsCount"))
        assertEquals(1, doc.getJSONObject("summary").getJSONObject("gpsDebugStats").getInt("ignoredPointsCount"))
    }
}
