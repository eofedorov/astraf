package com.astraf.hrgpslogger

data class TrackDetail(
    val fileName: String,
    val filePath: String,
    val displayName: String?,
    val createdAtMillis: Long,
    val startedAtMillis: Long?,
    val endedAtMillis: Long?,
    val durationMillis: Long?,
    val pointCount: Int,
    val distanceMeters: Double?,
    val averageSpeedKmh: Float?,
    val maxSpeedKmh: Float?,
    val averageHeartRateBpm: Int?,
    val maxHeartRateBpm: Int?,
    val totalClimbMeters: Float?,
    val hasGpsData: Boolean,
    val hasHeartRateData: Boolean,
    val hasAltitudeData: Boolean,
    val status: TrackDetailStatus,
    val samples: List<TrackCsvSample>,
    val segments: List<TrackSegment>,
    val speedSeries: List<TrackChartPoint>,
    val heartRateSeries: List<TrackChartPoint>,
    val elevationSeries: List<TrackChartPoint>,
    val isActive: Boolean,
)

enum class TrackDetailStatus {
    Completed,
    InsufficientData,
    FileNotFound,
    Unreadable,
    RecordingActive,
}

data class TrackChartPoint(
    val elapsedMillis: Long,
    val value: Float,
)
