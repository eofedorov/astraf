package com.astraf.hrgpslogger

/** Сглаживание скорости по окну accepted GPS-точек ([windowMs]). */
class SpeedCalculator(
    private val windowMs: Long = 4_000L,
    private val stationaryKmh: Float = 1.5f,
) {
    private val window = ArrayDeque<AcceptedGpsPoint>()
    private var _currentSpeedKmh: Float? = null

    val currentSpeedKmh: Float? get() = _currentSpeedKmh

    fun update(point: AcceptedGpsPoint) {
        window.addLast(point)
        trimWindow(point.timestampMillis)
        _currentSpeedKmh = computeWindowSpeedKmh()
    }

    fun reset() {
        window.clear()
        _currentSpeedKmh = null
    }

    private fun trimWindow(nowMillis: Long) {
        while (window.size > 1 && nowMillis - window.first().timestampMillis > windowMs) {
            window.removeFirst()
        }
    }

    private fun computeWindowSpeedKmh(): Float? {
        if (window.size < 2) {
            val derived = window.lastOrNull()?.derivedSpeedKmh
            return derived?.takeIf { it >= stationaryKmh } ?: 0f.takeIf { derived != null }
        }

        val first = window.first()
        val last = window.last()
        val deltaMs = last.timestampMillis - first.timestampMillis
        if (deltaMs <= 0L) return _currentSpeedKmh

        val distanceM = GeoDistance.distanceMeters(first, last)
        val kmh = (distanceM / (deltaMs / 1000.0) * MPS_TO_KMH).toFloat()
        return if (kmh < stationaryKmh) 0f else kmh
    }

    companion object {
        private const val MPS_TO_KMH = 3.6
    }
}
