package com.astraf.hrgpslogger

class GpsTrackQualityProcessor(
    private val config: GpsFilterConfig = GpsFilterConfig(),
) {
    private var waitingForFirstFix = true
    private var lastAccepted: AcceptedGpsPoint? = null
    private var currentSegmentId = 0
    private var stats = GpsDebugStats()

    val debugStats: GpsDebugStats get() = stats

    fun reset(waitingForFirstFix: Boolean = true) {
        this.waitingForFirstFix = waitingForFirstFix
        lastAccepted = null
        currentSegmentId = 0
        stats = GpsDebugStats()
    }

    fun restoreFromAcceptedPoints(
        points: List<AcceptedGpsPoint>,
        segmentsCount: Int,
    ) {
        waitingForFirstFix = false
        lastAccepted = points.lastOrNull()
        currentSegmentId = points.lastOrNull()?.segmentId ?: 0
        stats = stats.copy(
            acceptedPointsCount = points.size,
            segmentsCount = segmentsCount.coerceAtLeast(if (points.isEmpty()) 0 else 1),
        )
    }

    fun forceNewSegment() {
        if (lastAccepted != null) {
            currentSegmentId++
            stats = stats.copy(segmentsCount = currentSegmentId + 1)
        }
        lastAccepted = null
    }

    fun process(raw: RawGpsPoint): GpsFilterResult {
        stats = stats.copy(rawPointsCount = stats.rawPointsCount + 1)

        raw.accuracyMeters?.takeIf { it > 0f }?.let { accuracy ->
            if (accuracy > stats.maxObservedAccuracyM) {
                stats = stats.copy(maxObservedAccuracyM = accuracy)
            }
        }

        if (waitingForFirstFix) {
            return processWaitingForFirstFix(raw)
        }

        val timestampResult = validateTimestamp(raw)
        if (timestampResult != null) {
            return reject(timestampResult, raw)
        }

        val accuracyResult = validateAccuracy(raw, forFirstFix = false)
        if (accuracyResult != null) {
            return reject(accuracyResult, raw)
        }

        val previous = lastAccepted!!
        val deltaMs = raw.timestampMillis - previous.timestampMillis
        val derivedSpeedKmh = calculateSpeedKmh(previous, raw, deltaMs)
        if (derivedSpeedKmh != null) {
            if (derivedSpeedKmh > stats.maxCalculatedSpeedKmh) {
                stats = stats.copy(maxCalculatedSpeedKmh = derivedSpeedKmh)
            }
            if (derivedSpeedKmh > config.maxReasonableSpeedKmh) {
                return reject(GpsRejectReason.IMPOSSIBLE_SPEED, raw)
            }
        }

        val newSegment = deltaMs > config.maxGapWithoutNewSegmentMs
        if (newSegment) {
            stats = stats.copy(
                gpsGapsCount = stats.gpsGapsCount + 1,
                segmentsCount = stats.segmentsCount + 1,
            )
            currentSegmentId++
        }

        return accept(raw, newSegment)
    }

    private fun processWaitingForFirstFix(raw: RawGpsPoint): GpsFilterResult {
        val accuracyResult = validateAccuracy(raw, forFirstFix = true)
        if (accuracyResult != null) {
            return reject(GpsRejectReason.WAITING_FOR_FIRST_FIX, raw, accuracyResult)
        }

        if (raw.timestampMillis <= 0L) {
            return reject(GpsRejectReason.WAITING_FOR_FIRST_FIX, raw, GpsRejectReason.INVALID_TIMESTAMP)
        }

        waitingForFirstFix = false
        stats = stats.copy(segmentsCount = 1)
        return accept(raw, newSegment = true)
    }

    private fun validateTimestamp(raw: RawGpsPoint): GpsRejectReason? {
        val previous = lastAccepted ?: return null
        if (raw.timestampMillis <= 0L) {
            return GpsRejectReason.INVALID_TIMESTAMP
        }
        if (raw.timestampMillis <= previous.timestampMillis) {
            return GpsRejectReason.INVALID_TIMESTAMP
        }
        val deltaMs = raw.timestampMillis - previous.timestampMillis
        if (deltaMs < config.minPointIntervalMs) {
            return GpsRejectReason.TOO_FREQUENT
        }
        return null
    }

    private fun validateAccuracy(raw: RawGpsPoint, forFirstFix: Boolean): GpsRejectReason? {
        val accuracy = raw.accuracyMeters
        if (accuracy == null || accuracy <= 0f) {
            return GpsRejectReason.MISSING_ACCURACY
        }
        val threshold = if (forFirstFix) {
            config.firstFixRequiredAccuracyMeters
        } else {
            config.maxStatsAccuracyMeters
        }
        if (accuracy > config.maxAcceptedAccuracyMeters) {
            return GpsRejectReason.POOR_ACCURACY
        }
        if (accuracy > threshold) {
            return GpsRejectReason.POOR_ACCURACY
        }
        return null
    }

    private fun accept(raw: RawGpsPoint, newSegment: Boolean): GpsFilterResult.Accepted {
        val previous = lastAccepted
        val derivedSpeedKmh = if (previous != null && !newSegment) {
            val deltaMs = raw.timestampMillis - previous.timestampMillis
            calculateSpeedKmh(previous, raw, deltaMs)
        } else {
            null
        }

        val accepted = AcceptedGpsPoint(
            latitude = raw.latitude,
            longitude = raw.longitude,
            timestampMillis = raw.timestampMillis,
            accuracyMeters = raw.accuracyMeters!!,
            derivedSpeedKmh = derivedSpeedKmh,
            segmentId = currentSegmentId,
            altitudeMeters = raw.altitudeMeters,
        )
        lastAccepted = accepted
        stats = stats.copy(acceptedPointsCount = stats.acceptedPointsCount + 1)
        return GpsFilterResult.Accepted(point = accepted, newSegment = newSegment)
    }

    private fun reject(
        reason: GpsRejectReason,
        raw: RawGpsPoint,
        statsReason: GpsRejectReason = reason,
    ): GpsFilterResult.Rejected {
        stats = stats.copy(
            rejectedPointsCount = stats.rejectedPointsCount + 1,
            rejectedByPoorAccuracy = stats.rejectedByPoorAccuracy +
                if (statsReason == GpsRejectReason.POOR_ACCURACY) 1 else 0,
            rejectedByMissingAccuracy = stats.rejectedByMissingAccuracy +
                if (statsReason == GpsRejectReason.MISSING_ACCURACY) 1 else 0,
            rejectedByInvalidTimestamp = stats.rejectedByInvalidTimestamp +
                if (statsReason == GpsRejectReason.INVALID_TIMESTAMP) 1 else 0,
            rejectedByTooFrequent = stats.rejectedByTooFrequent +
                if (statsReason == GpsRejectReason.TOO_FREQUENT) 1 else 0,
            rejectedByImpossibleSpeed = stats.rejectedByImpossibleSpeed +
                if (statsReason == GpsRejectReason.IMPOSSIBLE_SPEED) 1 else 0,
            rejectedWaitingForFirstFix = stats.rejectedWaitingForFirstFix +
                if (reason == GpsRejectReason.WAITING_FOR_FIRST_FIX) 1 else 0,
        )
        return GpsFilterResult.Rejected(reason = reason, raw = raw)
    }

    private fun calculateSpeedKmh(
        previous: AcceptedGpsPoint,
        raw: RawGpsPoint,
        deltaMs: Long,
    ): Float? {
        if (deltaMs <= 0L) return null
        val distanceM = GeoDistance.distanceMeters(
            previous.latitude,
            previous.longitude,
            raw.latitude,
            raw.longitude,
        )
        return (distanceM / (deltaMs / 1000.0) * MPS_TO_KMH).toFloat()
    }

    companion object {
        private const val MPS_TO_KMH = 3.6
    }
}
