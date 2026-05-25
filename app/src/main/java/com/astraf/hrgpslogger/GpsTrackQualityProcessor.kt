package com.astraf.hrgpslogger

import android.util.Log

class GpsTrackQualityProcessor(
    private val config: GpsProcessingConfig = GpsProcessingConfig(),
    private val smoothingStrategy: SmoothingStrategy = AdaptiveSmoothingStrategy(),
    private val debugLogging: Boolean = false,
) {
    private var waitingForFirstFix = true
    private var lastAccepted: AcceptedGpsPoint? = null
    private var lastRawForMotion: RawGpsPoint? = null
    private var lastRawTimestampMillis: Long? = null
    private var currentSegmentId = 0
    private var streamState = GpsStreamState.GOOD
    private var lastValidPointAtMillis: Long? = null
    private var consecutiveOutliers = 0
    private var consecutiveDegraded = 0
    private var stats = GpsDebugStats()

    val debugStats: GpsDebugStats get() = stats

    fun reset(waitingForFirstFix: Boolean = true) {
        this.waitingForFirstFix = waitingForFirstFix
        lastAccepted = null
        lastRawForMotion = null
        lastRawTimestampMillis = null
        currentSegmentId = 0
        streamState = GpsStreamState.GOOD
        lastValidPointAtMillis = null
        consecutiveOutliers = 0
        consecutiveDegraded = 0
        stats = GpsDebugStats()
    }

    fun restoreFromAcceptedPoints(
        points: List<AcceptedGpsPoint>,
        segmentsCount: Int,
    ) {
        waitingForFirstFix = false
        lastAccepted = points.lastOrNull()
        lastRawForMotion = points.lastOrNull()?.let { accepted ->
            RawGpsPoint(
                latitude = accepted.latitude,
                longitude = accepted.longitude,
                timestampMillis = accepted.timestampMillis,
                accuracyMeters = accepted.accuracyMeters,
                altitudeMeters = accepted.altitudeMeters,
            )
        }
        lastRawTimestampMillis = points.lastOrNull()?.timestampMillis
        currentSegmentId = points.lastOrNull()?.segmentId ?: 0
        lastValidPointAtMillis = points.lastOrNull()?.timestampMillis
        streamState = GpsStreamState.GOOD
        consecutiveOutliers = 0
        consecutiveDegraded = 0
        stats = stats.copy(
            acceptedPointsCount = points.size,
            segmentsCount = segmentsCount.coerceAtLeast(if (points.isEmpty()) 0 else 1),
            streamState = GpsStreamState.GOOD,
        )
    }

    /** Явный разрыв сегмента (пауза/возобновление записи). */
    fun markExternalSegmentBreak() {
        if (lastAccepted != null || stats.acceptedPointsCount > 0) {
            currentSegmentId++
            stats = stats.copy(segmentsCount = (currentSegmentId + 1).coerceAtLeast(stats.segmentsCount))
        }
        lastAccepted = null
        lastRawForMotion = null
        streamState = GpsStreamState.GOOD
        consecutiveOutliers = 0
        consecutiveDegraded = 0
    }

    @Deprecated("Используйте markExternalSegmentBreak()", ReplaceWith("markExternalSegmentBreak()"))
    fun forceNewSegment() = markExternalSegmentBreak()

    fun process(raw: RawGpsPoint): GpsFilterResult {
        stats = stats.copy(rawPointsCount = stats.rawPointsCount + 1)
        updateMaxAccuracy(raw)

        if (waitingForFirstFix) {
            return processWaitingForFirstFix(raw)
        }

        val timestampReject = validateTimestamp(raw)
        if (timestampReject != null) {
            return rejectRaw(raw, timestampReject, GpsDecisionReason.REJECTED_INVALID_TIMESTAMP)
        }

        val quality = assessQuality(raw)
        val context = buildMovementContext(raw)
        updateStreamState(raw.timestampMillis, quality)

        if (quality == GpsPointQuality.BAD) {
            consecutiveOutliers++
            if (shouldIgnoreTemporaryNoise()) {
                return ignore(
                    raw = raw,
                    quality = quality,
                    trust = 0f,
                    noise = config.measurementNoiseMax,
                    context = context,
                    reason = GpsDecisionReason.IGNORED_TEMPORARY_NOISE,
                )
            }
            return rejectRaw(raw, mapBadQualityToReject(quality, context), GpsDecisionReason.REJECTED_BAD_ACCURACY)
        }

        val trust = calculateTrust(raw, quality, context)
        val measurementNoise = calculateMeasurementNoise(raw, quality, trust, context)

        if (isOutlier(raw, context, quality)) {
            consecutiveOutliers++
            if (shouldIgnoreTemporaryNoise()) {
                return ignore(
                    raw = raw,
                    quality = quality,
                    trust = trust,
                    noise = measurementNoise,
                    context = context,
                    reason = GpsDecisionReason.IGNORED_TEMPORARY_NOISE,
                )
            }
            return rejectRaw(
                raw,
                GpsRejectReason.IMPOSSIBLE_SPEED,
                GpsDecisionReason.REJECTED_IMPLAUSIBLE_SPEED,
                quality,
                trust,
                measurementNoise,
                context,
            )
        }

        consecutiveOutliers = 0
        if (quality == GpsPointQuality.DEGRADED) {
            consecutiveDegraded++
        } else {
            consecutiveDegraded = 0
        }

        val gapDecision = evaluateGap(raw, context, quality, trust, measurementNoise)
        return gapDecision
    }

    private fun processWaitingForFirstFix(raw: RawGpsPoint): GpsFilterResult {
        val accuracyReject = validateAccuracyForFirstFix(raw)
        if (accuracyReject != null) {
            return rejectRaw(
                raw,
                GpsRejectReason.WAITING_FOR_FIRST_FIX,
                GpsDecisionReason.WAITING_FOR_FIRST_FIX,
                statsReason = accuracyReject,
            )
        }
        if (raw.timestampMillis <= 0L) {
            return rejectRaw(
                raw,
                GpsRejectReason.WAITING_FOR_FIRST_FIX,
                GpsDecisionReason.WAITING_FOR_FIRST_FIX,
                statsReason = GpsRejectReason.INVALID_TIMESTAMP,
            )
        }

        waitingForFirstFix = false
        stats = stats.copy(segmentsCount = 1)
        return acceptPoint(
            raw = raw,
            quality = assessQuality(raw),
            trust = 1f,
            measurementNoise = config.measurementNoiseMin,
            context = MovementContext(null, null, null),
            newSegment = true,
            bridged = false,
            reason = GpsDecisionReason.ACCEPTED_GOOD_ACCURACY,
            segmentAction = GpsSegmentAction.START_NEW_SEGMENT,
        )
    }

    private fun evaluateGap(
        raw: RawGpsPoint,
        context: MovementContext,
        quality: GpsPointQuality,
        trust: Float,
        measurementNoise: Float,
    ): GpsFilterResult {
        val previous = lastAccepted
        if (previous == null) {
            if (stats.segmentsCount == 0) {
                stats = stats.copy(segmentsCount = 1)
            }
            return acceptPoint(
                raw, quality, trust, measurementNoise, context,
                newSegment = true,
                bridged = false,
                reason = decisionReasonForAccept(quality),
                segmentAction = GpsSegmentAction.START_NEW_SEGMENT,
            )
        }

        val deltaMs = context.deltaMs ?: 0L
        val impliedSpeed = context.impliedSpeedKmh
        val distance = context.distanceM ?: 0.0

        val canBridge = streamState != GpsStreamState.LOST &&
            deltaMs <= config.maxBridgeGapDurationMs &&
            distance <= config.maxBridgeDistanceMeters &&
            (impliedSpeed == null || impliedSpeed <= config.maxBridgeImpliedSpeedKmh) &&
            quality != GpsPointQuality.BAD

        val wasRecoveringFromLoss = streamState == GpsStreamState.TEMPORARILY_LOST ||
            streamState == GpsStreamState.LOST
        val isBridgedGap = canBridge &&
            (wasRecoveringFromLoss || deltaMs >= BRIDGE_GAP_MIN_MS)

        if (isBridgedGap) {
            val bridged = deltaMs >= BRIDGE_GAP_MIN_MS || wasRecoveringFromLoss
            streamState = GpsStreamState.GOOD
            return acceptPoint(
                raw, quality, trust, measurementNoise, context,
                newSegment = false,
                bridged = bridged,
                reason = if (bridged) {
                    GpsDecisionReason.BRIDGED_SHORT_GAP
                } else {
                    decisionReasonForAccept(quality)
                },
                segmentAction = if (bridged) {
                    GpsSegmentAction.APPEND_BRIDGED
                } else {
                    GpsSegmentAction.APPEND_CURRENT
                },
            )
        }

        val longGap = deltaMs > config.lostTimeoutMs
        val implausibleGap = impliedSpeed != null && impliedSpeed > config.maxReasonableSpeedKmh

        if (longGap || implausibleGap || streamState == GpsStreamState.LOST) {
            currentSegmentId++
            stats = stats.copy(
                gpsGapsCount = stats.gpsGapsCount + 1,
                segmentsCount = stats.segmentsCount + 1,
            )
            val reason = when {
                implausibleGap -> GpsDecisionReason.NEW_SEGMENT_IMPLAUSIBLE_GAP
                longGap -> GpsDecisionReason.NEW_SEGMENT_LONG_GAP
                else -> GpsDecisionReason.NEW_SEGMENT_LONG_GAP
            }
            streamState = GpsStreamState.GOOD
            return acceptPoint(
                raw, quality, trust, measurementNoise, context,
                newSegment = true,
                bridged = false,
                reason = reason,
                segmentAction = GpsSegmentAction.START_NEW_SEGMENT,
            )
        }

        return acceptPoint(
            raw, quality, trust, measurementNoise, context,
            newSegment = false,
            bridged = false,
            reason = decisionReasonForAccept(quality),
            segmentAction = GpsSegmentAction.APPEND_CURRENT,
        )
    }

    private fun acceptPoint(
        raw: RawGpsPoint,
        quality: GpsPointQuality,
        trust: Float,
        measurementNoise: Float,
        context: MovementContext,
        newSegment: Boolean,
        bridged: Boolean,
        reason: GpsDecisionReason,
        segmentAction: GpsSegmentAction,
    ): GpsFilterResult.Accepted {
        val (lat, lon) = smoothingStrategy.smooth(raw, lastAccepted, trust, quality, config)
        val previous = lastAccepted
        val derivedSpeedKmh = if (previous != null && !newSegment) {
            context.impliedSpeedKmh ?: calculateSpeedKmh(previous, raw, context.deltaMs ?: 0L)
        } else {
            null
        }

        val accepted = AcceptedGpsPoint(
            latitude = lat,
            longitude = lon,
            timestampMillis = raw.timestampMillis,
            accuracyMeters = raw.accuracyMeters!!,
            derivedSpeedKmh = derivedSpeedKmh,
            segmentId = currentSegmentId,
            altitudeMeters = raw.altitudeMeters,
        )
        lastAccepted = accepted
        lastRawForMotion = raw
        lastRawTimestampMillis = raw.timestampMillis
        lastValidPointAtMillis = raw.timestampMillis
        streamState = GpsStreamState.GOOD

        stats = stats.copy(
            acceptedPointsCount = stats.acceptedPointsCount + 1,
            bridgedPointsCount = stats.bridgedPointsCount + if (bridged) 1 else 0,
            degradedAcceptedCount = stats.degradedAcceptedCount +
                if (quality == GpsPointQuality.DEGRADED) 1 else 0,
            streamState = streamState,
            lastMeasurementNoise = measurementNoise,
            lastTrust = trust,
            lastDecisionReason = reason,
        )
        derivedSpeedKmh?.takeIf { it > stats.maxCalculatedSpeedKmh }?.let { speed ->
            stats = stats.copy(maxCalculatedSpeedKmh = speed)
        }

        val metadata = buildMetadata(
            raw, quality, trust, measurementNoise, context, segmentAction, reason, bridged,
        )
        logDecision(metadata, raw)
        return GpsFilterResult.Accepted(
            point = accepted,
            newSegment = newSegment,
            bridged = bridged,
            metadata = metadata,
            raw = raw,
        )
    }

    private fun ignore(
        raw: RawGpsPoint,
        quality: GpsPointQuality,
        trust: Float,
        noise: Float,
        context: MovementContext,
        reason: GpsDecisionReason,
    ): GpsFilterResult.Ignored {
        stats = stats.copy(
            ignoredPointsCount = stats.ignoredPointsCount + 1,
            streamState = streamState,
            lastMeasurementNoise = noise,
            lastTrust = trust,
            lastDecisionReason = reason,
        )
        val metadata = buildMetadata(
            raw, quality, trust, noise, context, GpsSegmentAction.IGNORE, reason, bridged = false,
        )
        logDecision(metadata, raw)
        return GpsFilterResult.Ignored(metadata = metadata, raw = raw)
    }

    private fun rejectRaw(
        raw: RawGpsPoint,
        rejectReason: GpsRejectReason,
        decisionReason: GpsDecisionReason,
        quality: GpsPointQuality = GpsPointQuality.BAD,
        trust: Float = 0f,
        noise: Float = config.measurementNoiseMax,
        context: MovementContext = buildMovementContext(raw),
        statsReason: GpsRejectReason = rejectReason,
    ): GpsFilterResult.Rejected {
        updateRejectStats(rejectReason, statsReason)
        val metadata = buildMetadata(
            raw, quality, trust, noise, context, GpsSegmentAction.REJECT, decisionReason, bridged = false,
        )
        stats = stats.copy(
            streamState = streamState,
            lastMeasurementNoise = noise,
            lastTrust = trust,
            lastDecisionReason = decisionReason,
        )
        logDecision(metadata, raw)
        return GpsFilterResult.Rejected(
            reason = rejectReason,
            metadata = metadata,
            raw = raw,
        )
    }

    private fun assessQuality(raw: RawGpsPoint): GpsPointQuality {
        val accuracy = raw.accuracyMeters ?: return GpsPointQuality.BAD
        return when {
            accuracy <= config.accuracyGoodMeters -> GpsPointQuality.GOOD
            accuracy <= config.accuracyDegradedMeters -> GpsPointQuality.DEGRADED
            accuracy <= config.maxAcceptedAccuracyMeters -> GpsPointQuality.DEGRADED
            else -> GpsPointQuality.BAD
        }
    }

    private fun calculateTrust(
        raw: RawGpsPoint,
        quality: GpsPointQuality,
        context: MovementContext,
    ): Float {
        val accuracy = raw.accuracyMeters ?: return 0f
        var trust = when (quality) {
            GpsPointQuality.GOOD -> 0.85f
            GpsPointQuality.DEGRADED -> 0.45f
            GpsPointQuality.BAD -> 0.1f
        }

        val accuracyFactor = (1f - (accuracy / config.accuracyBadMeters).coerceIn(0f, 1f)) * 0.25f
        trust += accuracyFactor

        context.impliedSpeedKmh?.let { speed ->
            if (speed > config.maxReasonableSpeedKmh) {
                trust *= 0.2f
            } else if (speed > config.maxBridgeImpliedSpeedKmh) {
                trust *= 0.6f
            }
        }

        if (consecutiveDegraded >= 2) {
            trust *= 0.85f
        }
        if (streamState == GpsStreamState.DEGRADED) {
            trust *= 0.9f
        }

        return trust.coerceIn(0.05f, 1f)
    }

    private fun calculateMeasurementNoise(
        raw: RawGpsPoint,
        quality: GpsPointQuality,
        trust: Float,
        context: MovementContext,
    ): Float {
        val accuracy = raw.accuracyMeters ?: config.accuracyBadMeters
        var noise = accuracy * config.noiseAccuracyMultiplier

        when (quality) {
            GpsPointQuality.DEGRADED -> noise *= config.noiseDegradedMultiplier
            GpsPointQuality.BAD -> noise = config.measurementNoiseMax
            GpsPointQuality.GOOD -> Unit
        }

        if (consecutiveOutliers > 0) {
            noise *= config.noiseOutlierStreakMultiplier
        }
        if (consecutiveDegraded >= 2) {
            noise *= 1.3f
        }

        context.impliedSpeedKmh?.takeIf { it > config.maxBridgeImpliedSpeedKmh }?.let {
            noise *= 1.5f
        }

        val trustFactor = 1f - trust * 0.5f
        noise *= (1f + trustFactor)

        return noise.coerceIn(config.measurementNoiseMin, config.measurementNoiseMax)
    }

    private fun isOutlier(
        raw: RawGpsPoint,
        context: MovementContext,
        quality: GpsPointQuality,
    ): Boolean {
        val speed = context.impliedSpeedKmh ?: return false
        return speed > config.maxReasonableSpeedKmh
    }

    private fun shouldIgnoreTemporaryNoise(): Boolean =
        consecutiveOutliers < config.outlierStreakThreshold &&
            streamState != GpsStreamState.LOST

    private fun updateStreamState(timestampMillis: Long, quality: GpsPointQuality) {
        val lastValid = lastValidPointAtMillis
        if (lastValid != null && quality == GpsPointQuality.BAD) {
            val silenceMs = timestampMillis - lastValid
            streamState = when {
                silenceMs >= config.lostTimeoutMs -> GpsStreamState.LOST
                silenceMs >= config.temporaryLostTimeoutMs -> GpsStreamState.TEMPORARILY_LOST
                else -> GpsStreamState.DEGRADED
            }
            stats = stats.copy(streamState = streamState)
            return
        }
        if (quality == GpsPointQuality.DEGRADED && streamState == GpsStreamState.GOOD) {
            streamState = GpsStreamState.DEGRADED
        }
        stats = stats.copy(streamState = streamState)
    }

    private fun buildMovementContext(raw: RawGpsPoint): MovementContext {
        val previousRaw = lastRawForMotion ?: return MovementContext(null, null, null)
        val deltaMs = raw.timestampMillis - previousRaw.timestampMillis
        if (deltaMs <= 0L) return MovementContext(deltaMs, null, null)
        val distanceM = GeoDistance.distanceMeters(
            previousRaw.latitude,
            previousRaw.longitude,
            raw.latitude,
            raw.longitude,
        )
        val impliedSpeed = calculateSpeedKmhRaw(previousRaw, raw, deltaMs)
        return MovementContext(deltaMs, distanceM, impliedSpeed)
    }

    private fun validateTimestamp(raw: RawGpsPoint): GpsRejectReason? {
        val previous = lastAccepted ?: return null
        if (raw.timestampMillis <= 0L) return GpsRejectReason.INVALID_TIMESTAMP
        if (raw.timestampMillis <= previous.timestampMillis) return GpsRejectReason.INVALID_TIMESTAMP
        val deltaMs = raw.timestampMillis - previous.timestampMillis
        if (deltaMs < config.minPointIntervalMs) return GpsRejectReason.TOO_FREQUENT
        return null
    }

    private fun validateAccuracyForFirstFix(raw: RawGpsPoint): GpsRejectReason? {
        val accuracy = raw.accuracyMeters
        if (accuracy == null || accuracy <= 0f) return GpsRejectReason.MISSING_ACCURACY
        if (accuracy > config.firstFixRequiredAccuracyMeters) return GpsRejectReason.POOR_ACCURACY
        if (accuracy > config.maxAcceptedAccuracyMeters) return GpsRejectReason.POOR_ACCURACY
        return null
    }

    private fun mapBadQualityToReject(quality: GpsPointQuality, context: MovementContext): GpsRejectReason {
        context.impliedSpeedKmh?.takeIf { it > config.maxReasonableSpeedKmh }?.let {
            return GpsRejectReason.IMPOSSIBLE_SPEED
        }
        return GpsRejectReason.POOR_ACCURACY
    }

    private fun decisionReasonForAccept(quality: GpsPointQuality): GpsDecisionReason = when (quality) {
        GpsPointQuality.GOOD -> GpsDecisionReason.ACCEPTED_GOOD_ACCURACY
        GpsPointQuality.DEGRADED -> GpsDecisionReason.ACCEPTED_DEGRADED_LOW_TRUST
        GpsPointQuality.BAD -> GpsDecisionReason.ACCEPTED_DEGRADED_LOW_TRUST
    }

    private fun buildMetadata(
        raw: RawGpsPoint,
        quality: GpsPointQuality,
        trust: Float,
        noise: Float,
        context: MovementContext,
        segmentAction: GpsSegmentAction,
        reason: GpsDecisionReason,
        bridged: Boolean,
    ) = GpsPipelineMetadata(
        quality = quality,
        trust = trust,
        measurementNoise = noise,
        streamState = streamState,
        segmentAction = segmentAction,
        reason = reason,
        deltaMs = context.deltaMs,
        distanceFromLastAcceptedM = context.distanceM,
        impliedSpeedKmh = context.impliedSpeedKmh,
        bridged = bridged,
    )

    private fun updateMaxAccuracy(raw: RawGpsPoint) {
        raw.accuracyMeters?.takeIf { it > 0f }?.let { accuracy ->
            if (accuracy > stats.maxObservedAccuracyM) {
                stats = stats.copy(maxObservedAccuracyM = accuracy)
            }
        }
    }

    private fun updateRejectStats(reason: GpsRejectReason, statsReason: GpsRejectReason) {
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
    }

    private fun logDecision(metadata: GpsPipelineMetadata, raw: RawGpsPoint) {
        if (!debugLogging) return
        if (metadata.reason == GpsDecisionReason.ACCEPTED_GOOD_ACCURACY &&
            metadata.segmentAction == GpsSegmentAction.APPEND_CURRENT
        ) {
            return
        }
        Log.d(
            TAG,
            "gps decision=${metadata.reason} action=${metadata.segmentAction} " +
                "stream=${metadata.streamState} quality=${metadata.quality} " +
                "trust=${"%.2f".format(metadata.trust)} noise=${"%.1f".format(metadata.measurementNoise)} " +
                "dt=${metadata.deltaMs} dist=${metadata.distanceFromLastAcceptedM?.let { "%.1f".format(it) }} " +
                "speed=${metadata.impliedSpeedKmh} acc=${raw.accuracyMeters}",
        )
    }

    private fun calculateSpeedKmhRaw(
        previous: RawGpsPoint,
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

    private data class MovementContext(
        val deltaMs: Long?,
        val distanceM: Double?,
        val impliedSpeedKmh: Float?,
    )

    companion object {
        private const val TAG = "GpsPipeline"
        private const val MPS_TO_KMH = 3.6
        /** Минимальный интервал (мс), после которого восстановление считается bridge, а не обычным шагом. */
        private const val BRIDGE_GAP_MIN_MS = 3_000L
    }
}
