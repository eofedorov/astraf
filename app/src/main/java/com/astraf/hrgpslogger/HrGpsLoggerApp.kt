package com.astraf.hrgpslogger

import android.app.Application

class HrGpsLoggerApp : Application() {

    val session: LoggerSession by lazy { LoggerSession(this) }

    override fun onCreate() {
        super.onCreate()
        LoggingRecovery.startIfNeeded(this)
    }
}
