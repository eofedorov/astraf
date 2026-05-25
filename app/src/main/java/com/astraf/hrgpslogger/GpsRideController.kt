package com.astraf.hrgpslogger

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Единый слой качества GPS: фильтрация raw-точек, accepted-поток, скорость и debug-статистика.
 */
class GpsRideController(
    private val config: GpsProcessingConfig = GpsProcessingConfig(),
    debugLogging: Boolean = false,
) {
    private val processor = GpsTrackQualityProcessor(config, debugLogging = debugLogging)
    private val speedCalculator = SpeedCalculator()

    private val _acceptedPoint = MutableStateFlow<AcceptedGpsPoint?>(null)
    val acceptedPoint: StateFlow<AcceptedGpsPoint?> = _acceptedPoint.asStateFlow()

    private val _speedKmh = MutableStateFlow<Float?>(null)
    val speedKmh: StateFlow<Float?> = _speedKmh.asStateFlow()

    private val _debugStats = MutableStateFlow(GpsDebugStats())
    val debugStats: StateFlow<GpsDebugStats> = _debugStats.asStateFlow()

    private val _waitingForFirstFix = MutableStateFlow(false)
    val waitingForFirstFix: StateFlow<Boolean> = _waitingForFirstFix.asStateFlow()

    fun beginWaitingForFirstFix() {
        processor.reset(waitingForFirstFix = true)
        speedCalculator.reset()
        _acceptedPoint.value = null
        _speedKmh.value = null
        _waitingForFirstFix.value = true
        publishDebugStats()
    }

    fun reset() {
        processor.reset(waitingForFirstFix = false)
        speedCalculator.reset()
        _acceptedPoint.value = null
        _speedKmh.value = null
        _waitingForFirstFix.value = false
        publishDebugStats()
    }

    fun restoreFromAcceptedPoints(points: List<AcceptedGpsPoint>, segmentsCount: Int) {
        processor.restoreFromAcceptedPoints(points, segmentsCount)
        speedCalculator.reset()
        points.lastOrNull()?.let { last ->
            speedCalculator.update(last)
            _acceptedPoint.value = last
            _speedKmh.value = speedCalculator.currentSpeedKmh
        }
        _waitingForFirstFix.value = false
        publishDebugStats()
    }

    /** Явный разрыв сегмента при паузе/возобновлении записи. */
    fun markExternalSegmentBreak() {
        processor.markExternalSegmentBreak()
        publishDebugStats()
    }

    @Deprecated("Используйте markExternalSegmentBreak()", ReplaceWith("markExternalSegmentBreak()"))
    fun forceNewSegment() = markExternalSegmentBreak()

    fun processRaw(sample: LocationSample): GpsFilterResult {
        val result = processor.process(RawGpsPoint.from(sample))
        publishDebugStats()

        if (result is GpsFilterResult.Accepted) {
            if (_waitingForFirstFix.value) {
                _waitingForFirstFix.value = false
            }
            speedCalculator.update(result.point)
            _acceptedPoint.value = result.point
            _speedKmh.value = speedCalculator.currentSpeedKmh
        }

        return result
    }

    private fun publishDebugStats() {
        _debugStats.value = processor.debugStats
    }
}
