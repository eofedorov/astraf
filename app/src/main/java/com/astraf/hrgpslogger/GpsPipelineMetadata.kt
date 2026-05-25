package com.astraf.hrgpslogger

data class GpsPipelineMetadata(
    val quality: GpsPointQuality,
    val trust: Float,
    val measurementNoise: Float,
    val streamState: GpsStreamState,
    val segmentAction: GpsSegmentAction,
    val reason: GpsDecisionReason,
    val deltaMs: Long? = null,
    val distanceFromLastAcceptedM: Double? = null,
    val impliedSpeedKmh: Float? = null,
    val bridged: Boolean = false,
)
