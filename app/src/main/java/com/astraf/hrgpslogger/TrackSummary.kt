package com.astraf.hrgpslogger

data class TrackSummary(
    val fileName: String,
    val filePath: String,
    val startedAtMillis: Long,
    val pointCount: Int,
    val durationMillis: Long?,
    val distanceMeters: Double?,
    val isActive: Boolean,
)
