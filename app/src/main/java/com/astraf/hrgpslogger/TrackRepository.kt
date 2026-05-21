package com.astraf.hrgpslogger

import android.content.Context
import java.io.File

class TrackRepository(private val context: Context) {

    fun listTracks(activeFilePath: String?): List<TrackSummary> {
        val files = context.filesDir.listFiles { file ->
            file.isFile && file.name.startsWith(TRACK_PREFIX) && file.name.endsWith(".csv")
        } ?: return emptyList()

        return files
            .sortedByDescending { it.lastModified() }
            .map { file ->
                val parsed = parseTrack(file)
                parsed.copy(
                    isActive = activeFilePath != null && file.absolutePath == activeFilePath,
                )
            }
    }

    fun loadTrackDetail(fileName: String, activeFilePath: String?): TrackDetail {
        val file = File(context.filesDir, fileName)
        val isActive = activeFilePath != null && file.absolutePath == activeFilePath
        val metadata = TrackMetadataStore.load(context, fileName)
        val displayName = metadata?.displayName

        if (!file.exists()) {
            return emptyDetail(
                fileName = fileName,
                filePath = file.absolutePath,
                displayName = displayName,
                createdAtMillis = fileNameTimestamp(fileName),
                status = TrackDetailStatus.FileNotFound,
                isActive = isActive,
            )
        }

        val samples = try {
            TrackCsvParser.parseSamples(file)
        } catch (_: Exception) {
            return emptyDetail(
                fileName = fileName,
                filePath = file.absolutePath,
                displayName = displayName,
                createdAtMillis = file.lastModified(),
                status = TrackDetailStatus.Unreadable,
                isActive = isActive,
            )
        }

        val points = samples.map { it.point }
        val pointCount = points.size
        val startedAtMillis = points.firstOrNull()?.timestampMillis ?: fileNameTimestamp(fileName)
        val endedAtMillis = points.lastOrNull()?.timestampMillis
        val durationMillis = TrackAnalytics.computeDurationMillis(points)
        val distanceMeters = TrackAnalytics.computeDistanceMeters(points)
        val averageSpeedKmh = TrackAnalytics.computeAverageSpeedKmh(points)
        val maxSpeedKmh = TrackAnalytics.computeMaxSpeedKmh(points)
        val (averageHeartRateBpm, maxHeartRateBpm) = TrackAnalytics.computeHeartRateStats(samples)
        val totalClimbMeters = resolveTotalClimbMeters(fileName, points, metadata)
        val hasGpsData = pointCount > 0
        val hasHeartRateData = samples.any { it.bpm != null }
        val hasAltitudeData = points.any { it.altitudeMeters != null }
        val status = TrackAnalytics.resolveStatus(
            fileExists = true,
            readable = true,
            pointCount = pointCount,
            isActive = isActive,
        )

        return TrackDetail(
            fileName = fileName,
            filePath = file.absolutePath,
            displayName = displayName,
            createdAtMillis = file.lastModified(),
            startedAtMillis = startedAtMillis,
            endedAtMillis = endedAtMillis,
            durationMillis = durationMillis,
            pointCount = pointCount,
            distanceMeters = distanceMeters,
            averageSpeedKmh = averageSpeedKmh,
            maxSpeedKmh = maxSpeedKmh,
            averageHeartRateBpm = averageHeartRateBpm,
            maxHeartRateBpm = maxHeartRateBpm,
            totalClimbMeters = totalClimbMeters,
            hasGpsData = hasGpsData,
            hasHeartRateData = hasHeartRateData,
            hasAltitudeData = hasAltitudeData,
            status = status,
            samples = samples,
            segments = TrackAnalytics.buildSegments(points),
            speedSeries = TrackAnalytics.buildSpeedSeries(samples),
            heartRateSeries = TrackAnalytics.buildHeartRateSeries(samples),
            elevationSeries = TrackAnalytics.buildElevationSeries(samples),
            isActive = isActive,
        )
    }

    fun deleteTrack(fileName: String): Boolean {
        val file = File(context.filesDir, fileName)
        val deleted = if (file.exists()) file.delete() else true
        TrackMetadataStore.delete(context, fileName)
        return deleted
    }

    fun renameTrackDisplayName(fileName: String, displayName: String?) {
        TrackMetadataStore.updateDisplayName(context, fileName, displayName)
    }

    private fun parseTrack(file: File): TrackSummary {
        val startedAtMillis = fileNameTimestamp(file.name).takeIf { it > 0L } ?: file.lastModified()

        if (file.length() == 0L) {
            return TrackSummary(
                fileName = file.name,
                filePath = file.absolutePath,
                startedAtMillis = startedAtMillis,
                pointCount = 0,
                durationMillis = null,
                distanceMeters = null,
                totalClimbMeters = null,
                isActive = false,
            )
        }

        val points = TrackCsvParser.parseAcceptedPoints(file)
        val duration = TrackAnalytics.computeDurationMillis(points)
        val totalClimbMeters = resolveTotalClimbMeters(file.name, points, null)

        return TrackSummary(
            fileName = file.name,
            filePath = file.absolutePath,
            startedAtMillis = points.firstOrNull()?.timestampMillis ?: startedAtMillis,
            pointCount = points.size,
            durationMillis = duration,
            distanceMeters = TrackAnalytics.computeDistanceMeters(points),
            totalClimbMeters = totalClimbMeters,
            isActive = false,
        )
    }

    private fun resolveTotalClimbMeters(
        fileName: String,
        points: List<AcceptedGpsPoint>,
        metadata: TrackMetadata?,
    ): Float? {
        metadata?.totalClimbMeters?.takeIf { it > 0f }?.let { return it }
        TrackMetadataStore.load(context, fileName)?.totalClimbMeters?.takeIf { it > 0f }?.let { return it }
        return ElevationClimbTracker.computeTotalClimbMeters(points)
    }

    private fun fileNameTimestamp(fileName: String): Long =
        fileName.removePrefix(TRACK_PREFIX).removeSuffix(".csv").toLongOrNull() ?: 0L

    private fun emptyDetail(
        fileName: String,
        filePath: String,
        displayName: String?,
        createdAtMillis: Long,
        status: TrackDetailStatus,
        isActive: Boolean,
    ): TrackDetail = TrackDetail(
        fileName = fileName,
        filePath = filePath,
        displayName = displayName,
        createdAtMillis = createdAtMillis,
        startedAtMillis = fileNameTimestamp(fileName).takeIf { it > 0L },
        endedAtMillis = null,
        durationMillis = null,
        pointCount = 0,
        distanceMeters = null,
        averageSpeedKmh = null,
        maxSpeedKmh = null,
        averageHeartRateBpm = null,
        maxHeartRateBpm = null,
        totalClimbMeters = null,
        hasGpsData = false,
        hasHeartRateData = false,
        hasAltitudeData = false,
        status = status,
        samples = emptyList(),
        segments = emptyList(),
        speedSeries = emptyList(),
        heartRateSeries = emptyList(),
        elevationSeries = emptyList(),
        isActive = isActive,
    )

    companion object {
        private const val TRACK_PREFIX = "hr_gps_"
    }
}
