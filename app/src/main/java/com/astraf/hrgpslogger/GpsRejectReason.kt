package com.astraf.hrgpslogger

enum class GpsRejectReason {
    POOR_ACCURACY,
    MISSING_ACCURACY,
    INVALID_TIMESTAMP,
    TOO_FREQUENT,
    IMPOSSIBLE_SPEED,
    GPS_GAP,
    WAITING_FOR_FIRST_FIX,
}
