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
import java.io.File

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
        restoreTrackAndStatsFromFile(fileName)
        LoggingStateStore.getBleAddress(appContext)?.let { address ->
            if (bleClient.connectionState.value != BleConnectionState.READY) {
                bleClient.connect(address)
            }
        }
    }

    fun ensureTrackLoadedFromFile(fileName: String) {
        if (activeTrackStore.points.value.isNotEmpty()) return
        restoreTrackAndStatsFromFile(fileName)
    }

    /**
     * @param resumeFileName имя CSV в filesDir для продолжения записи после перезапуска
     * @return имя CSV-файла или null
     */
    fun startCsvCollection(resumeFileName: String? = null): String? {
        csvCollectJob?.cancel()

        val persistedFileName = if (LoggingStateStore.isActive(appContext)) {
            LoggingStateStore.getCsvFileName(appContext)
        } else {
            null
        }
        val targetResumeFileName = resumeFileName ?: persistedFileName

        val file = when {
            targetResumeFileName != null &&
                (csvLogger.phase.value == RecordingPhase.Idle ||
                    targetResumeFileName == csvLogger.currentFileName()) -> {
                resumeFromPersistedFile(targetResumeFileName)
            }
            csvLogger.phase.value == RecordingPhase.Paused -> {
                csvLogger.resumeWriting()
                csvLogger.currentFile()?.also { file ->
                    restoreTrackAndStatsFromFile(file.name)
                }
            }
            csvLogger.phase.value == RecordingPhase.Idle -> {
                if (LoggingStateStore.isActive(appContext)) {
                    persistedFileName?.let { resumeFromPersistedFile(it) }
                } else {
                    activeTrackStore.clear()
                    csvLogger.startLogging()
                }
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
        csvLogger.currentFileName()?.let { restoreTrackAndStatsFromFile(it) }
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

    private fun resumeFromPersistedFile(fileName: String): File? {
        val file = csvLogger.resumeLogging(fileName) ?: return null
        restoreTrackAndStatsFromFile(fileName)
        return file
    }

    private fun restoreTrackAndStatsFromFile(fileName: String) {
        val file = File(appContext.filesDir, fileName)
        if (!file.exists()) return
        activeTrackStore.restoreFromCsv(file)
        parseTrackStartMillis(fileName)?.let { startedAtMillis ->
            tripStatsTracker.restoreFromTrack(activeTrackStore.points.value, startedAtMillis)
        }
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
