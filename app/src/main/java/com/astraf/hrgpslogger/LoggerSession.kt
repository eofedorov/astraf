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
    val tripStatsTracker = TripStatsTracker()
    val activeTrackStore = ActiveTrackStore()

    private var csvCollectJob: Job? = null

    val recordingPhase get() = csvLogger.phase

    fun ensureLocationTracking() {
        locationTracker.start()
    }

    fun ensureTripStatsTracking() {
        if (csvLogger.phase.value != RecordingPhase.Recording) return
        tripStatsTracker.ensureStarted(
            scope = scope,
            locationFlow = locationTracker.location,
            speedFlow = locationTracker.speedKmh,
        )
    }

    fun restorePausedSession(fileName: String) {
        csvLogger.restorePausedSession(fileName)
        csvLogger.currentFile()?.let { activeTrackStore.restoreFromCsv(it) }
        parseTrackStartMillis(fileName)?.let { tripStatsTracker.restorePausedAnchor(it) }
        LoggingStateStore.getBleAddress(appContext)?.let { address ->
            if (bleClient.connectionState.value != BleConnectionState.READY) {
                bleClient.connect(address)
            }
        }
    }

    /**
     * @param resumeFileName имя CSV в filesDir для продолжения записи после перезапуска
     * @return имя CSV-файла или null
     */
    fun startCsvCollection(resumeFileName: String? = null): String? {
        csvCollectJob?.cancel()

        val file = when {
            resumeFileName != null -> {
                val resumed = csvLogger.resumeLogging(resumeFileName)
                csvLogger.currentFile()?.let { activeTrackStore.restoreFromCsv(it) }
                resumed
            }
            csvLogger.phase.value == RecordingPhase.Paused -> {
                csvLogger.resumeWriting()
                csvLogger.currentFile()
            }
            csvLogger.phase.value == RecordingPhase.Idle -> {
                activeTrackStore.clear()
                csvLogger.startLogging()
            }
            else -> csvLogger.currentFile()
        }

        if (csvLogger.phase.value == RecordingPhase.Recording) {
            if (tripStatsTracker.stats.value.startedAtMillis == null) {
                tripStatsTracker.start(
                    scope = scope,
                    locationFlow = locationTracker.location,
                    speedFlow = locationTracker.speedKmh,
                )
            } else {
                tripStatsTracker.resume(
                    scope = scope,
                    locationFlow = locationTracker.location,
                    speedFlow = locationTracker.speedKmh,
                )
            }
            startCollectJob()
        }

        return file?.name
    }

    fun pauseCsvCollection() {
        csvCollectJob?.cancel()
        csvCollectJob = null
        csvLogger.pauseLogging()
        tripStatsTracker.pause()
        persistLoggingState(paused = true)
    }

    fun resumeCsvCollection() {
        if (csvLogger.phase.value != RecordingPhase.Paused) return
        csvLogger.resumeWriting()
        tripStatsTracker.resume(
            scope = scope,
            locationFlow = locationTracker.location,
            speedFlow = locationTracker.speedKmh,
        )
        startCollectJob()
        persistLoggingState(paused = false)
    }

    fun stopCsvCollection() {
        csvCollectJob?.cancel()
        csvCollectJob = null
        tripStatsTracker.stop()
        tripStatsTracker.reset()
        activeTrackStore.clear()
        csvLogger.finishLogging()
    }

    fun persistLoggingState(paused: Boolean = csvLogger.phase.value == RecordingPhase.Paused) {
        if (!csvLogger.hasActiveSession() && !LoggingStateStore.isActive(appContext)) return
        LoggingStateStore.save(
            appContext,
            csvFileName = csvLogger.currentFileName(),
            bleAddress = bleClient.connectedDeviceAddress.value
                ?: LoggingStateStore.getBleAddress(appContext),
            paused = paused,
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

    private fun parseTrackStartMillis(fileName: String): Long? =
        fileName.removePrefix("hr_gps_").removeSuffix(".csv").toLongOrNull()

    private fun startCollectJob() {
        csvCollectJob?.cancel()
        csvCollectJob = combine(
            bleClient.heartRateBpm,
            locationTracker.location,
        ) { bpm, location -> bpm to location }
            .onEach { (bpm, location) ->
                csvLogger.writeIfChanged(bpm, location)
                location?.let { sample ->
                    activeTrackStore.append(sample, csvLogger.phase.value)
                }
            }
            .launchIn(scope)
    }
}
