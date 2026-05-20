package com.astraf.hrgpslogger

data class AcceptedGpsPoint(
    val latitude: Double,
    val longitude: Double,
    val timestampMillis: Long,
    val accuracyMeters: Float,
    val derivedSpeedKmh: Float?,
    val segmentId: Int,
    val altitudeMeters: Double? = null,
) {
    fun toGeoPoint(): GeoPoint = GeoPoint(latitude, longitude)
}
