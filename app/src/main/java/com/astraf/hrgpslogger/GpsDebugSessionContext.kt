package com.astraf.hrgpslogger

data class GpsDebugSessionContext(
    val recordingPhase: RecordingPhase,
    val isAutoPaused: Boolean = false,
    val manualPauseWhilePaused: Boolean = false,
)
