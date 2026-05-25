package com.astraf.hrgpslogger

import android.content.Context
import java.io.File

class TrackRepository(private val context: Context) {

    fun listTracks(activeFilePath: String?): List<TrackSummary> {
        val files = context.filesDir.listFiles { file ->
            file.isFile && file.name.startsWith(TRACK_PREFIX) && file.name.endsWith(".csv")
        } ?: return emptyList()

        return files
            .map { file ->
                val parsed = parseTrack(file)
                parsed.copy(
                    isActive = activeFilePath != null && file.absolutePath == activeFilePath,
                )
            }
            .sortedByDescending { it.startedAtMillis }
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
        val totalClimbMeters = resolveTotalClimbMeters(
            fileName = fileName,
            points = points,
            metadata = metadata,
            loadMetadata = { TrackMetadataStore.load(context, it) },
        )
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
        GpsDebugStore.debugFile(context, fileName).delete()
        return deleted
    }

    fun renameTrackDisplayName(fileName: String, displayName: String?) {
        TrackMetadataStore.updateDisplayName(context, fileName, displayName)
    }

    private fun parseTrack(file: File): TrackSummary {
        val metadata = TrackMetadataStore.load(context, file.name)
        return parseTrackSummary(
            file = file,
            metadata = metadata,
            loadMetadata = { TrackMetadataStore.load(context, it) },
        )
    }

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

        internal fun parseTrackSummary(
            file: File,
            metadata: TrackMetadata?,
            loadMetadata: (String) -> TrackMetadata? = { null },
        ): TrackSummary {
            val startedAtMillis = fileNameTimestamp(file.name).takeIf { it > 0L } ?: file.lastModified()

            if (file.length() == 0L) {
                return emptySummary(file, startedAtMillis, metadata)
            }

            val samples = try {
                TrackCsvParser.parseSamples(file)
            } catch (_: Exception) {
                return emptySummary(file, startedAtMillis, metadata)
            }

            val points = samples.map { it.point }
            val (averageHeartRateBpm, _) = TrackAnalytics.computeHeartRateStats(samples)

            return TrackSummary(
                fileName = file.name,
                filePath = file.absolutePath,
                startedAtMillis = points.firstOrNull()?.timestampMillis ?: startedAtMillis,
                pointCount = points.size,
                durationMillis = TrackAnalytics.computeDurationMillis(points),
                distanceMeters = TrackAnalytics.computeDistanceMeters(points),
                averageSpeedKmh = TrackAnalytics.computeAverageSpeedKmh(points),
                maxSpeedKmh = TrackAnalytics.computeMaxSpeedKmh(points),
                averageHeartRateBpm = averageHeartRateBpm,
                totalClimbMeters = resolveTotalClimbMeters(file.name, points, metadata, loadMetadata),
                displayName = metadata?.displayName,
                routePoints = points.toRoutePreview(),
                stravaActivityId = metadata?.stravaActivityId,
                isActive = false,
            )
        }

        private fun emptySummary(
            file: File,
            startedAtMillis: Long,
            metadata: TrackMetadata?,
        ): TrackSummary = TrackSummary(
            fileName = file.name,
            filePath = file.absolutePath,
            startedAtMillis = startedAtMillis,
            pointCount = 0,
            durationMillis = null,
            distanceMeters = null,
            averageSpeedKmh = null,
            maxSpeedKmh = null,
            averageHeartRateBpm = null,
            totalClimbMeters = null,
            displayName = metadata?.displayName,
            routePoints = emptyList(),
            stravaActivityId = metadata?.stravaActivityId,
            isActive = false,
        )

        private fun resolveTotalClimbMeters(
            fileName: String,
            points: List<AcceptedGpsPoint>,
            metadata: TrackMetadata?,
            loadMetadata: (String) -> TrackMetadata?,
        ): Float? {
            metadata?.totalClimbMeters?.takeIf { it > 0f }?.let { return it }
            loadMetadata(fileName)?.totalClimbMeters?.takeIf { it > 0f }?.let { return it }
            return ElevationClimbTracker.computeTotalClimbMeters(points)
        }

        private fun fileNameTimestamp(fileName: String): Long =
            fileName.removePrefix(TRACK_PREFIX).removeSuffix(".csv").toLongOrNull() ?: 0L
    }
}
