package com.astraf.hrgpslogger

/**
 * Все числовые пороги GPS-pipeline в одном месте (велосипед, консервативно).
 */
data class GpsProcessingConfig(
    val firstFixRequiredAccuracyMeters: Float = 25f,
    val accuracyGoodMeters: Float = 15f,
    val accuracyDegradedMeters: Float = 30f,
    val accuracyBadMeters: Float = 50f,
    val maxAcceptedAccuracyMeters: Float = 50f,

    val maxReasonableSpeedKmh: Float = 55f,
    val maxBridgeImpliedSpeedKmh: Float = 45f,

    val minPointIntervalMs: Long = 500,
    val maxBridgeGapDurationMs: Long = 15_000,
    val temporaryLostTimeoutMs: Long = 8_000,
    val lostTimeoutMs: Long = 30_000,

    val maxBridgeDistanceMeters: Double = 400.0,

    val measurementNoiseMin: Float = 4f,
    val measurementNoiseMax: Float = 120f,
    val noiseAccuracyMultiplier: Float = 1.2f,
    val noiseDegradedMultiplier: Float = 1.8f,
    val noiseOutlierStreakMultiplier: Float = 2.5f,

    val outlierStreakThreshold: Int = 3,

    val smoothingMinAlpha: Float = 0.15f,
    val smoothingMaxAlpha: Float = 0.85f,
    val smoothingDegradedAlphaScale: Float = 0.45f,
)

/** @deprecated Используйте [GpsProcessingConfig]; оставлено для совместимости тестов. */
typealias GpsFilterConfig = GpsProcessingConfig
