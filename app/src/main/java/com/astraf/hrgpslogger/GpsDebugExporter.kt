package com.astraf.hrgpslogger

import android.content.Context
import org.json.JSONObject
import java.io.File

object GpsDebugExporter {

    fun exportCacheFile(context: Context, csvFileName: String): File? {
        val sidecar = GpsDebugStore.debugFile(context, csvFileName)
        if (!sidecar.exists() || sidecar.length() == 0L) return null
        val parsed = parseJsonl(sidecar) ?: return null
        if (parsed.eventLines.isEmpty()) return null

        val dir = File(context.cacheDir, "gps_debug_exports").apply { mkdirs() }
        val outFile = File(dir, exportFileNameForCsv(csvFileName))
        val document = GpsDebugJsonCodec.assembleDocument(
            headerLine = parsed.header,
            eventLines = parsed.eventLines,
            summary = parsed.summary ?: GpsDebugSummary(
                gpsDebugStats = GpsDebugStats(),
                segmentsCount = 0,
                acceptedPointsCount = 0,
            ),
        )
        outFile.writeText(document)
        return outFile
    }

    fun exportCacheFile(
        context: Context,
        csvFileName: String,
        liveSummary: GpsDebugSummary,
    ): File? {
        val sidecar = GpsDebugStore.debugFile(context, csvFileName)
        if (!sidecar.exists() || sidecar.length() == 0L) return null
        val parsed = parseJsonl(sidecar) ?: return null
        if (parsed.eventLines.isEmpty()) return null

        val dir = File(context.cacheDir, "gps_debug_exports").apply { mkdirs() }
        val outFile = File(dir, exportFileNameForCsv(csvFileName))
        val document = GpsDebugJsonCodec.assembleDocument(
            headerLine = parsed.header,
            eventLines = parsed.eventLines,
            summary = liveSummary,
        )
        outFile.writeText(document)
        return outFile
    }

    fun exportFileNameForCsv(csvFileName: String): String =
        GpsDebugStore.debugFileName(csvFileName)

    internal data class ParsedJsonl(
        val header: JSONObject,
        val eventLines: List<JSONObject>,
        val summary: GpsDebugSummary?,
    )

    internal fun parseJsonl(file: File): ParsedJsonl? {
        var header: JSONObject? = null
        val events = mutableListOf<JSONObject>()
        var summary: GpsDebugSummary? = null

        file.bufferedReader().forEachLine { line ->
            val trimmed = line.trim()
            if (trimmed.isEmpty()) return@forEachLine
            val obj = GpsDebugJsonCodec.parseJsonlLine(trimmed) ?: return@forEachLine
            when (obj.optString("type")) {
                "header" -> header = obj
                "event" -> events.add(obj)
                "footer" -> {
                    val summaryObj = obj.optJSONObject("summary") ?: return@forEachLine
                    summary = parseSummary(summaryObj)
                }
            }
        }

        val headerLine = header ?: return null
        return ParsedJsonl(
            header = headerLine,
            eventLines = events,
            summary = summary,
        )
    }

    private fun parseSummary(obj: JSONObject): GpsDebugSummary {
        val statsObj = obj.getJSONObject("gpsDebugStats")
        return GpsDebugSummary(
            gpsDebugStats = parseStats(statsObj),
            segmentsCount = obj.getInt("segmentsCount"),
            acceptedPointsCount = obj.getInt("acceptedPointsCount"),
        )
    }

    private fun parseStats(obj: JSONObject): GpsDebugStats = GpsDebugStats(
        rawPointsCount = obj.getInt("rawPointsCount"),
        acceptedPointsCount = obj.getInt("acceptedPointsCount"),
        rejectedPointsCount = obj.getInt("rejectedPointsCount"),
        ignoredPointsCount = obj.getInt("ignoredPointsCount"),
        bridgedPointsCount = obj.getInt("bridgedPointsCount"),
        degradedAcceptedCount = obj.getInt("degradedAcceptedCount"),
        rejectedByPoorAccuracy = obj.getInt("rejectedByPoorAccuracy"),
        rejectedByMissingAccuracy = obj.getInt("rejectedByMissingAccuracy"),
        rejectedByInvalidTimestamp = obj.getInt("rejectedByInvalidTimestamp"),
        rejectedByTooFrequent = obj.getInt("rejectedByTooFrequent"),
        rejectedByImpossibleSpeed = obj.getInt("rejectedByImpossibleSpeed"),
        rejectedWaitingForFirstFix = obj.getInt("rejectedWaitingForFirstFix"),
        gpsGapsCount = obj.getInt("gpsGapsCount"),
        segmentsCount = obj.getInt("segmentsCount"),
        maxObservedAccuracyM = obj.getDouble("maxObservedAccuracyM").toFloat(),
        maxCalculatedSpeedKmh = obj.getDouble("maxCalculatedSpeedKmh").toFloat(),
        streamState = GpsStreamState.valueOf(obj.getString("streamState")),
        lastMeasurementNoise = obj.getDouble("lastMeasurementNoise").toFloat(),
        lastTrust = obj.getDouble("lastTrust").toFloat(),
        lastDecisionReason = obj.optString("lastDecisionReason", "").takeIf { it.isNotEmpty() }
            ?.let { GpsDecisionReason.valueOf(it) },
    )
}
