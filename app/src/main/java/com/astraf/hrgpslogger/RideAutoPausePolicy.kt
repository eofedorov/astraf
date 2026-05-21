package com.astraf.hrgpslogger

enum class AutoPauseAction {
    None,
    Pause,
    Resume,
}

/**
 * Автопауза / автовозобновление по скорости движения (отдельно от CSV и GpsRideController).
 */
class RideAutoPausePolicy(
    speedEstimator: MotionSpeedEstimator = MotionSpeedEstimator(),
    private val pauseBelowKmh: Float = PAUSE_BELOW_KMH,
    private val pauseHoldMs: Long = PAUSE_HOLD_MS,
    private val resumeMinKmh: Float = RESUME_MIN_KMH,
    private val resumeHoldMs: Long = RESUME_HOLD_MS,
) {
    private val speedEstimator = speedEstimator

    private var stationarySinceElapsed: Long? = null
    private var movingSinceElapsed: Long? = null

    fun onLocation(sample: LocationSample) {
        speedEstimator.onLocation(sample)
    }

    fun reset() {
        speedEstimator.reset()
        resetTimers()
    }

    fun resetTimers() {
        stationarySinceElapsed = null
        movingSinceElapsed = null
    }

    fun evaluate(
        phase: RecordingPhase,
        manualPauseWhilePaused: Boolean,
        nowElapsed: Long,
    ): AutoPauseAction {
        val speed = speedEstimator.currentSpeedKmh(nowElapsed)

        return when (phase) {
            RecordingPhase.Recording -> evaluateWhileRecording(speed, nowElapsed)
            RecordingPhase.Paused -> evaluateWhilePaused(speed, manualPauseWhilePaused, nowElapsed)
            RecordingPhase.Idle, RecordingPhase.WaitingForGps -> {
                resetTimers()
                AutoPauseAction.None
            }
        }
    }

    private fun evaluateWhileRecording(speed: Float?, nowElapsed: Long): AutoPauseAction {
        movingSinceElapsed = null
        when {
            speed == null -> {
                stationarySinceElapsed = null
                return AutoPauseAction.None
            }
            speed < pauseBelowKmh -> {
                if (stationarySinceElapsed == null) {
                    stationarySinceElapsed = nowElapsed
                    return AutoPauseAction.None
                }
                if (nowElapsed - stationarySinceElapsed!! >= pauseHoldMs) {
                    stationarySinceElapsed = null
                    return AutoPauseAction.Pause
                }
                return AutoPauseAction.None
            }
            else -> {
                stationarySinceElapsed = null
                return AutoPauseAction.None
            }
        }
    }

    private fun evaluateWhilePaused(
        speed: Float?,
        manualPauseWhilePaused: Boolean,
        nowElapsed: Long,
    ): AutoPauseAction {
        stationarySinceElapsed = null
        if (manualPauseWhilePaused) {
            movingSinceElapsed = null
            return AutoPauseAction.None
        }
        when {
            speed == null -> {
                movingSinceElapsed = null
                return AutoPauseAction.None
            }
            speed >= resumeMinKmh -> {
                if (movingSinceElapsed == null) {
                    movingSinceElapsed = nowElapsed
                    return AutoPauseAction.None
                }
                if (nowElapsed - movingSinceElapsed!! >= resumeHoldMs) {
                    movingSinceElapsed = null
                    return AutoPauseAction.Resume
                }
                return AutoPauseAction.None
            }
            else -> {
                movingSinceElapsed = null
                return AutoPauseAction.None
            }
        }
    }

    companion object {
        const val PAUSE_BELOW_KMH = 2f
        const val PAUSE_HOLD_MS = 10_000L
        const val RESUME_MIN_KMH = 2f
        const val RESUME_HOLD_MS = 2_000L
        const val TICK_MS = 1_000L
    }
}
