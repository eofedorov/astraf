package com.astraf.hrgpslogger

sealed class GpsFilterResult {
    abstract val metadata: GpsPipelineMetadata?
    abstract val raw: RawGpsPoint

    data class Accepted(
        val point: AcceptedGpsPoint,
        val newSegment: Boolean,
        val bridged: Boolean,
        override val metadata: GpsPipelineMetadata,
        override val raw: RawGpsPoint,
    ) : GpsFilterResult()

    data class Ignored(
        override val metadata: GpsPipelineMetadata,
        override val raw: RawGpsPoint,
    ) : GpsFilterResult()

    data class Rejected(
        val reason: GpsRejectReason,
        override val metadata: GpsPipelineMetadata,
        override val raw: RawGpsPoint,
    ) : GpsFilterResult()
}
