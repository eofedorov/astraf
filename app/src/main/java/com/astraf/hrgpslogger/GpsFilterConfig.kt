package com.astraf.hrgpslogger

data class GpsFilterConfig(
    val firstFixRequiredAccuracyMeters: Float = 25f,
    val maxAcceptedAccuracyMeters: Float = 50f,
    val maxStatsAccuracyMeters: Float = 30f,
    val maxReasonableSpeedKmh: Float = 80f,
    val minPointIntervalMs: Long = 500,
    val maxGapWithoutNewSegmentMs: Long = 15_000,
)
