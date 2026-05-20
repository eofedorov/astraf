package com.astraf.hrgpslogger

data class RawGpsPoint(
    val latitude: Double,
    val longitude: Double,
    val timestampMillis: Long,
    val accuracyMeters: Float?,
    val altitudeMeters: Double? = null,
    val speedMps: Float? = null,
    val bearingDegrees: Float? = null,
) {
    companion object {
        fun from(sample: LocationSample): RawGpsPoint = RawGpsPoint(
            latitude = sample.latitude,
            longitude = sample.longitude,
            timestampMillis = sample.timestampMillis,
            accuracyMeters = sample.accuracyMeters,
            altitudeMeters = sample.altitudeMeters,
            speedMps = sample.speedMps,
            bearingDegrees = sample.bearingDegrees,
        )
    }
}
