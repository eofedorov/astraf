package com.astraf.hrgpslogger

sealed class GpsFilterResult {
    data class Accepted(
        val point: AcceptedGpsPoint,
        val newSegment: Boolean,
    ) : GpsFilterResult()

    data class Rejected(
        val reason: GpsRejectReason,
        val raw: RawGpsPoint,
    ) : GpsFilterResult()
}
