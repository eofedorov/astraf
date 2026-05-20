package com.astraf.hrgpslogger

import android.app.Application
import com.astraf.hrgpslogger.strava.StravaIntegration
import org.maplibre.android.MapLibre
import org.maplibre.android.storage.FileSource

class HrGpsLoggerApp : Application() {

    val session: LoggerSession by lazy { LoggerSession(this) }
    val stravaIntegration: StravaIntegration by lazy { StravaIntegration(this) }

    override fun onCreate() {
        super.onCreate()
        CrashLogManager.install(this)
        MapLibre.getInstance(this)
        // Тайлы кешируются в приватном каталоге приложения (FileSource default path).
        FileSource.getInstance(this)
        LoggingRecovery.startIfNeeded(this)
    }
}
