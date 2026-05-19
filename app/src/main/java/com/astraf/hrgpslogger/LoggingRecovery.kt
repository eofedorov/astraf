package com.astraf.hrgpslogger

import android.content.Context

object LoggingRecovery {

    fun startIfNeeded(context: Context) {
        if (!LoggingStateStore.isActive(context)) return
        if (LoggingStateStore.isPaused(context)) return
        if (LoggingForegroundService.isRunning) return
        LoggingForegroundService.start(context, resume = true)
    }
}
