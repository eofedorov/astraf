package com.astraf.hrgpslogger

data class TrackSegment(
    val id: Int,
    val points: List<GeoPoint> = emptyList(),
)
