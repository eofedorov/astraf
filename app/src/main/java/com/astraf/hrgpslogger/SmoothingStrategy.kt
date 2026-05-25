package com.astraf.hrgpslogger

interface SmoothingStrategy {
    fun smooth(
        raw: RawGpsPoint,
        previousProcessed: AcceptedGpsPoint?,
        trust: Float,
        quality: GpsPointQuality,
        config: GpsProcessingConfig,
    ): Pair<Double, Double>
}

/**
 * Адаптивное сглаживание: чем выше trust, тем ближе к raw; degraded — сильнее к предыдущей точке.
 */
class AdaptiveSmoothingStrategy : SmoothingStrategy {
    override fun smooth(
        raw: RawGpsPoint,
        previousProcessed: AcceptedGpsPoint?,
        trust: Float,
        quality: GpsPointQuality,
        config: GpsProcessingConfig,
    ): Pair<Double, Double> {
        if (previousProcessed == null) {
            return raw.latitude to raw.longitude
        }

        val baseAlpha = config.smoothingMinAlpha +
            (config.smoothingMaxAlpha - config.smoothingMinAlpha) * trust.coerceIn(0f, 1f)

        val alpha = when (quality) {
            GpsPointQuality.GOOD -> baseAlpha
            GpsPointQuality.DEGRADED -> baseAlpha * config.smoothingDegradedAlphaScale
            GpsPointQuality.BAD -> config.smoothingMinAlpha
        }.coerceIn(config.smoothingMinAlpha, config.smoothingMaxAlpha)

        val lat = previousProcessed.latitude + alpha * (raw.latitude - previousProcessed.latitude)
        val lon = previousProcessed.longitude + alpha * (raw.longitude - previousProcessed.longitude)
        return lat to lon
    }
}
