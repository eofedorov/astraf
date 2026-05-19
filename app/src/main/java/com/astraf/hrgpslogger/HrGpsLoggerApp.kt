package com.astraf.hrgpslogger

import android.app.Application
import org.maplibre.android.MapLibre
import org.maplibre.android.storage.FileSource

class HrGpsLoggerApp : Application() {

    val session: LoggerSession by lazy { LoggerSession(this) }

    override fun onCreate() {
        super.onCreate()
        MapLibre.getInstance(this)
        // Тайлы кешируются в приватном каталоге приложения (FileSource default path).
        FileSource.getInstance(this)
        LoggingRecovery.startIfNeeded(this)
    }
}
