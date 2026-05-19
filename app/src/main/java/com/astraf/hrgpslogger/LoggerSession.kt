package com.astraf.hrgpslogger

import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach

class LoggerSession(context: Context) {

    private val appContext = context.applicationContext
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    val bleClient = BleHeartRateClient(appContext)
    val locationTracker = LocationTracker(appContext)
    val csvLogger = CsvLogger(appContext)

    private var csvCollectJob: Job? = null

    fun ensureLocationTracking() {
        locationTracker.start()
    }

    /**
     * @param resumeFileName имя CSV в filesDir для продолжения записи после перезапуска
     * @return имя CSV-файла или null
     */
    fun startCsvCollection(resumeFileName: String? = null): String? {
        csvCollectJob?.cancel()

        val file = when {
            resumeFileName != null -> csvLogger.resumeLogging(resumeFileName)
            !csvLogger.isLogging.value -> csvLogger.startLogging()
            else -> csvLogger.currentFile()
        }

        csvCollectJob = combine(
            bleClient.heartRateBpm,
            locationTracker.location,
        ) { bpm, location -> bpm to location }
            .onEach { (bpm, location) ->
                csvLogger.writeIfChanged(bpm, location)
            }
            .launchIn(scope)

        return file?.name
    }

    fun stopCsvCollection() {
        csvCollectJob?.cancel()
        csvCollectJob = null
        csvLogger.stopLogging()
    }

    fun persistLoggingState() {
        LoggingStateStore.save(
            appContext,
            csvFileName = csvLogger.currentFileName(),
            bleAddress = bleClient.connectedDeviceAddress.value
                ?: LoggingStateStore.getBleAddress(appContext),
        )
    }

    fun release() {
        if (LoggingStateStore.isActive(appContext)) return
        csvCollectJob?.cancel()
        bleClient.release()
        locationTracker.release()
        csvLogger.release()
        scope.cancel()
    }
}
