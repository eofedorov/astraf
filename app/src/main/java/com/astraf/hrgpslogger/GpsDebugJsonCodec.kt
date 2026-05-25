package com.astraf.hrgpslogger

import org.json.JSONArray
import org.json.JSONObject

object GpsDebugJsonCodec {

    fun encodeHeaderLine(
        csvFileName: String,
        startedAtMillis: Long?,
        processingConfig: GpsProcessingConfig,
        sessionContext: GpsDebugSessionContext? = null,
    ): String = JSONObject().apply {
        put("type", "header")
        put("schemaVersion", GpsDebugStore.SCHEMA_VERSION)
        put("format", GpsDebugStore.FORMAT_JSONL)
        put("ride", encodeRide(csvFileName, startedAtMillis, processingConfig))
        sessionContext?.let { put("session", encodeSession(it)) }
    }.toString()

    fun encodeEventLine(
        index: Int,
        sample: LocationSample,
        result: GpsFilterResult,
        heartRateBpm: Int?,
        sessionContext: GpsDebugSessionContext? = null,
    ): String = JSONObject().apply {
        put("type", "event")
        put("index", index)
        put("timestampMillis", sample.timestampMillis)
        put("raw", encodeRaw(sample))
        put("decision", encodeDecision(result))
        heartRateBpm?.let { put("heartRateBpm", it) }
        sessionContext?.let { put("session", encodeSession(it)) }
    }.toString()

    fun encodeFooterLine(
        summary: GpsDebugSummary,
        sessionContext: GpsDebugSessionContext? = null,
    ): String = JSONObject().apply {
        put("type", "footer")
        put("exportedAtMillis", System.currentTimeMillis())
        put("summary", encodeSummary(summary))
        sessionContext?.let { put("session", encodeSession(it)) }
    }.toString()

    fun assembleDocument(
        headerLine: JSONObject,
        eventLines: List<JSONObject>,
        summary: GpsDebugSummary,
    ): String = JSONObject().apply {
        put("schemaVersion", headerLine.optInt("schemaVersion", GpsDebugStore.SCHEMA_VERSION))
        put("format", GpsDebugStore.FORMAT_JSON)
        put("ride", headerLine.getJSONObject("ride"))
        put("summary", encodeSummary(summary))
        put("events", JSONArray().apply {
            eventLines.forEach { eventObj ->
                put(JSONObject().apply {
                    put("index", eventObj.getInt("index"))
                    put("timestampMillis", eventObj.getLong("timestampMillis"))
                    put("raw", eventObj.getJSONObject("raw"))
                    put("decision", eventObj.getJSONObject("decision"))
                    if (eventObj.has("heartRateBpm") && !eventObj.isNull("heartRateBpm")) {
                        put("heartRateBpm", eventObj.getInt("heartRateBpm"))
                    }
                    if (eventObj.has("session")) {
                        put("session", eventObj.getJSONObject("session"))
                    }
                })
            }
        })
    }.toString()

    fun summaryFromStats(stats: GpsDebugStats): GpsDebugSummary =
        GpsDebugSummary(
            gpsDebugStats = stats,
            segmentsCount = stats.segmentsCount,
            acceptedPointsCount = stats.acceptedPointsCount,
        )

    private fun encodeRide(
        csvFileName: String,
        startedAtMillis: Long?,
        processingConfig: GpsProcessingConfig,
    ): JSONObject = JSONObject().apply {
        put("csvFileName", csvFileName)
        startedAtMillis?.let { put("startedAtMillis", it) }
        put("processingConfig", encodeProcessingConfig(processingConfig))
    }

    private fun encodeSession(ctx: GpsDebugSessionContext): JSONObject = JSONObject().apply {
        put("recordingPhase", ctx.recordingPhase.name)
        put("isAutoPaused", ctx.isAutoPaused)
        put("manualPauseWhilePaused", ctx.manualPauseWhilePaused)
    }

    fun encodeProcessingConfig(config: GpsProcessingConfig): JSONObject = JSONObject().apply {
        put("firstFixRequiredAccuracyMeters", config.firstFixRequiredAccuracyMeters.toDouble())
        put("accuracyGoodMeters", config.accuracyGoodMeters.toDouble())
        put("accuracyDegradedMeters", config.accuracyDegradedMeters.toDouble())
        put("accuracyBadMeters", config.accuracyBadMeters.toDouble())
        put("maxAcceptedAccuracyMeters", config.maxAcceptedAccuracyMeters.toDouble())
        put("maxReasonableSpeedKmh", config.maxReasonableSpeedKmh.toDouble())
        put("maxBridgeImpliedSpeedKmh", config.maxBridgeImpliedSpeedKmh.toDouble())
        put("minPointIntervalMs", config.minPointIntervalMs)
        put("maxBridgeGapDurationMs", config.maxBridgeGapDurationMs)
        put("temporaryLostTimeoutMs", config.temporaryLostTimeoutMs)
        put("lostTimeoutMs", config.lostTimeoutMs)
        put("maxBridgeDistanceMeters", config.maxBridgeDistanceMeters)
        put("measurementNoiseMin", config.measurementNoiseMin.toDouble())
        put("measurementNoiseMax", config.measurementNoiseMax.toDouble())
        put("noiseAccuracyMultiplier", config.noiseAccuracyMultiplier.toDouble())
        put("noiseDegradedMultiplier", config.noiseDegradedMultiplier.toDouble())
        put("noiseOutlierStreakMultiplier", config.noiseOutlierStreakMultiplier.toDouble())
        put("outlierStreakThreshold", config.outlierStreakThreshold)
        put("smoothingMinAlpha", config.smoothingMinAlpha.toDouble())
        put("smoothingMaxAlpha", config.smoothingMaxAlpha.toDouble())
        put("smoothingDegradedAlphaScale", config.smoothingDegradedAlphaScale.toDouble())
    }

    fun encodeGpsDebugStats(stats: GpsDebugStats): JSONObject = JSONObject().apply {
        put("rawPointsCount", stats.rawPointsCount)
        put("acceptedPointsCount", stats.acceptedPointsCount)
        put("rejectedPointsCount", stats.rejectedPointsCount)
        put("ignoredPointsCount", stats.ignoredPointsCount)
        put("bridgedPointsCount", stats.bridgedPointsCount)
        put("degradedAcceptedCount", stats.degradedAcceptedCount)
        put("rejectedByPoorAccuracy", stats.rejectedByPoorAccuracy)
        put("rejectedByMissingAccuracy", stats.rejectedByMissingAccuracy)
        put("rejectedByInvalidTimestamp", stats.rejectedByInvalidTimestamp)
        put("rejectedByTooFrequent", stats.rejectedByTooFrequent)
        put("rejectedByImpossibleSpeed", stats.rejectedByImpossibleSpeed)
        put("rejectedWaitingForFirstFix", stats.rejectedWaitingForFirstFix)
        put("gpsGapsCount", stats.gpsGapsCount)
        put("segmentsCount", stats.segmentsCount)
        put("maxObservedAccuracyM", stats.maxObservedAccuracyM.toDouble())
        put("maxCalculatedSpeedKmh", stats.maxCalculatedSpeedKmh.toDouble())
        put("streamState", stats.streamState.name)
        put("lastMeasurementNoise", stats.lastMeasurementNoise.toDouble())
        put("lastTrust", stats.lastTrust.toDouble())
        stats.lastDecisionReason?.let { put("lastDecisionReason", it.name) }
    }

    private fun encodeSummary(summary: GpsDebugSummary): JSONObject = JSONObject().apply {
        put("gpsDebugStats", encodeGpsDebugStats(summary.gpsDebugStats))
        put("segmentsCount", summary.segmentsCount)
        put("acceptedPointsCount", summary.acceptedPointsCount)
    }

    private fun encodeRaw(sample: LocationSample): JSONObject = encodeRawPoint(
        latitude = sample.latitude,
        longitude = sample.longitude,
        timestampMillis = sample.timestampMillis,
        accuracyMeters = sample.accuracyMeters,
        altitudeMeters = sample.altitudeMeters,
        speedMps = sample.speedMps,
        bearingDegrees = sample.bearingDegrees,
    )

    private fun encodeRawPoint(raw: RawGpsPoint): JSONObject = encodeRawPoint(
        latitude = raw.latitude,
        longitude = raw.longitude,
        timestampMillis = raw.timestampMillis,
        accuracyMeters = raw.accuracyMeters,
        altitudeMeters = raw.altitudeMeters,
        speedMps = raw.speedMps,
        bearingDegrees = raw.bearingDegrees,
    )

    private fun encodeRawPoint(
        latitude: Double,
        longitude: Double,
        timestampMillis: Long,
        accuracyMeters: Float?,
        altitudeMeters: Double?,
        speedMps: Float?,
        bearingDegrees: Float?,
    ): JSONObject = JSONObject().apply {
        put("latitude", latitude)
        put("longitude", longitude)
        put("timestampMillis", timestampMillis)
        accuracyMeters?.let { put("accuracyMeters", it.toDouble()) }
        altitudeMeters?.let { put("altitudeMeters", it) }
        speedMps?.let { put("speedMps", it.toDouble()) }
        bearingDegrees?.let { put("bearingDegrees", it.toDouble()) }
    }

    fun encodeDecision(result: GpsFilterResult): JSONObject = when (result) {
        is GpsFilterResult.Accepted -> JSONObject().apply {
            put("type", "accepted")
            put("metadata", encodeMetadata(result.metadata))
            put("accepted", JSONObject().apply {
                put("latitude", result.point.latitude)
                put("longitude", result.point.longitude)
                put("segmentId", result.point.segmentId)
                result.point.derivedSpeedKmh?.let { put("derivedSpeedKmh", it.toDouble()) }
                put("newSegment", result.newSegment)
                put("bridged", result.bridged)
            })
        }
        is GpsFilterResult.Ignored -> JSONObject().apply {
            put("type", "ignored")
            put("metadata", encodeMetadata(result.metadata))
        }
        is GpsFilterResult.Rejected -> JSONObject().apply {
            put("type", "rejected")
            put("rejectReason", result.reason.name)
            put("metadata", encodeMetadata(result.metadata))
        }
    }

    fun encodeMetadata(metadata: GpsPipelineMetadata): JSONObject = JSONObject().apply {
        put("quality", metadata.quality.name)
        put("trust", metadata.trust.toDouble())
        put("measurementNoise", metadata.measurementNoise.toDouble())
        put("streamState", metadata.streamState.name)
        put("segmentAction", metadata.segmentAction.name)
        put("reason", metadata.reason.name)
        metadata.deltaMs?.let { put("deltaMs", it) }
        metadata.distanceFromLastAcceptedM?.let { put("distanceFromLastAcceptedM", it) }
        metadata.impliedSpeedKmh?.let { put("impliedSpeedKmh", it.toDouble()) }
        put("bridged", metadata.bridged)
    }

    fun parseJsonlLine(line: String): JSONObject? =
        runCatching { JSONObject(line.trim()) }.getOrNull()

    fun countEventLines(file: java.io.File): Int {
        if (!file.exists()) return 0
        return file.bufferedReader().useLines { lines ->
            lines.count { line ->
                val obj = parseJsonlLine(line) ?: return@count false
                obj.optString("type") == "event"
            }
        }
    }
}

data class GpsDebugSummary(
    val gpsDebugStats: GpsDebugStats,
    val segmentsCount: Int,
    val acceptedPointsCount: Int,
)
