package com.astraf.hrgpslogger

import kotlin.math.cos
import kotlin.math.sqrt

object GeoDistance {
    private const val EARTH_RADIUS_M = 6_371_000.0

    fun distanceMeters(
        latA: Double,
        lonA: Double,
        latB: Double,
        lonB: Double,
    ): Double {
        val latMidRad = Math.toRadians((latA + latB) * 0.5)
        val dLon = Math.toRadians(lonB - lonA)
        val dLat = Math.toRadians(latB - latA)
        val dx = dLon * cos(latMidRad) * EARTH_RADIUS_M
        val dy = dLat * EARTH_RADIUS_M
        return sqrt(dx * dx + dy * dy)
    }

    fun distanceMeters(a: RawGpsPoint, b: RawGpsPoint): Double =
        distanceMeters(a.latitude, a.longitude, b.latitude, b.longitude)

    fun distanceMeters(a: AcceptedGpsPoint, b: AcceptedGpsPoint): Double =
        distanceMeters(a.latitude, a.longitude, b.latitude, b.longitude)

    fun distanceMeters(a: GeoPoint, b: GeoPoint): Double =
        distanceMeters(a.latitude, a.longitude, b.latitude, b.longitude)
}
