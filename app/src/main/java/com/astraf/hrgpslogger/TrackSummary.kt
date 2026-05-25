package com.astraf.hrgpslogger

data class TrackSummary(
    val fileName: String,
    val filePath: String,
    val startedAtMillis: Long,
    val pointCount: Int,
    val durationMillis: Long?,
    val distanceMeters: Double?,
    val averageSpeedKmh: Float?,
    val maxSpeedKmh: Float?,
    val averageHeartRateBpm: Int?,
    val totalClimbMeters: Float?,
    val displayName: String?,
    val routePoints: List<RoutePreviewPoint>,
    val stravaActivityId: Long?,
    val isActive: Boolean,
) {
    val hasGpsData: Boolean get() = pointCount > 0
}
