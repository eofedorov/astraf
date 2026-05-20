package com.astraf.hrgpslogger

data class GpsDebugStats(
    val rawPointsCount: Int = 0,
    val acceptedPointsCount: Int = 0,
    val rejectedPointsCount: Int = 0,
    val rejectedByPoorAccuracy: Int = 0,
    val rejectedByMissingAccuracy: Int = 0,
    val rejectedByInvalidTimestamp: Int = 0,
    val rejectedByTooFrequent: Int = 0,
    val rejectedByImpossibleSpeed: Int = 0,
    val rejectedWaitingForFirstFix: Int = 0,
    val gpsGapsCount: Int = 0,
    val segmentsCount: Int = 0,
    val maxObservedAccuracyM: Float = 0f,
    val maxCalculatedSpeedKmh: Float = 0f,
)
