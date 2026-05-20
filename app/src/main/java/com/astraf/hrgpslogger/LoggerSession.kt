package com.astraf.hrgpslogger

import android.content.Context
import android.os.SystemClock
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flow
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
    private var speedPolicyJob: Job? = null

    private var stationarySinceElapsed: Long? = null
    private var movingSinceElapsed: Long? = null

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
        reconcileSpeedBasedRidePolicy()
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
            reconcileSpeedBasedRidePolicy()
        }

        return file?.name
    }

    fun pauseCsvCollection(manualPause: Boolean = true) {
        csvCollectJob?.cancel()
        csvCollectJob = null
        csvLogger.pauseLogging()
        tripStatsTracker.pause()
        persistLoggingState(paused = true, manualPauseWhilePaused = manualPause)
        reconcileSpeedBasedRidePolicy()
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
        reconcileSpeedBasedRidePolicy()
    }

    fun stopCsvCollection() {
        csvCollectJob?.cancel()
        csvCollectJob = null
        speedPolicyJob?.cancel()
        speedPolicyJob = null
        stationarySinceElapsed = null
        movingSinceElapsed = null
        tripStatsTracker.stop()
        tripStatsTracker.reset()
        activeTrackStore.clear()
        csvLogger.finishLogging()
    }

    fun persistLoggingState(
        paused: Boolean = csvLogger.phase.value == RecordingPhase.Paused,
        manualPauseWhilePaused: Boolean = LoggingStateStore.isManualPauseWhilePaused(appContext),
    ) {
        if (!csvLogger.hasActiveSession() && !LoggingStateStore.isActive(appContext)) return
        LoggingStateStore.save(
            appContext,
            csvFileName = csvLogger.currentFileName(),
            bleAddress = bleClient.connectedDeviceAddress.value
                ?: LoggingStateStore.getBleAddress(appContext),
            paused = paused,
            manualPauseWhilePaused = if (paused) manualPauseWhilePaused else false,
        )
    }

    /** Перезапускает наблюдение за скоростью (автопауза / автостарт) согласно фазе и типу паузы. */
    internal fun reconcileSpeedBasedRidePolicy() {
        speedPolicyJob?.cancel()
        speedPolicyJob = null
        stationarySinceElapsed = null
        movingSinceElapsed = null

        if (!csvLogger.hasActiveSession()) return
        when (csvLogger.phase.value) {
            RecordingPhase.Idle -> return
            RecordingPhase.Paused ->
                if (LoggingStateStore.isManualPauseWhilePaused(appContext)) return
            RecordingPhase.Recording -> Unit
        }

        speedPolicyJob = combine(
            csvLogger.phase,
            locationTracker.speedKmh,
            speedPolicyTickFlow(),
        ) { phase, speed, _ -> phase to speed }
            .onEach { (phase, speed) ->
                val now = SystemClock.elapsedRealtime()
                when (phase) {
                    RecordingPhase.Recording -> {
                        movingSinceElapsed = null
                        if (speed == null) {
                            stationarySinceElapsed = null
                            return@onEach
                        }
                        if (speed < AUTO_PAUSE_BELOW_KMH) {
                            if (stationarySinceElapsed == null) {
                                stationarySinceElapsed = now
                            } else if (now - stationarySinceElapsed!! >= AUTO_PAUSE_HOLD_MS) {
                                stationarySinceElapsed = null
                                applyAutoPauseFromSpeedPolicy()
                            }
                        } else {
                            stationarySinceElapsed = null
                        }
                    }
                    RecordingPhase.Paused -> {
                        stationarySinceElapsed = null
                        if (LoggingStateStore.isManualPauseWhilePaused(appContext)) {
                            movingSinceElapsed = null
                            return@onEach
                        }
                        if (speed == null) {
                            movingSinceElapsed = null
                            return@onEach
                        }
                        if (speed >= AUTO_RESUME_MIN_KMH) {
                            if (movingSinceElapsed == null) {
                                movingSinceElapsed = now
                            } else if (now - movingSinceElapsed!! >= AUTO_RESUME_HOLD_MS) {
                                movingSinceElapsed = null
                                applyAutoResumeFromSpeedPolicy()
                            }
                        } else {
                            movingSinceElapsed = null
                        }
                    }
                    RecordingPhase.Idle -> Unit
                }
            }
            .launchIn(scope)
    }

    private fun applyAutoPauseFromSpeedPolicy() {
        if (csvLogger.phase.value != RecordingPhase.Recording) return
        pauseCsvCollection(manualPause = false)
    }

    private fun applyAutoResumeFromSpeedPolicy() {
        if (csvLogger.phase.value != RecordingPhase.Paused) return
        if (LoggingStateStore.isManualPauseWhilePaused(appContext)) return
        LoggingForegroundService.resumeSession(appContext)
    }

    private fun speedPolicyTickFlow() = flow {
        while (true) {
            emit(Unit)
            delay(SPEED_POLICY_TICK_MS)
        }
    }

    fun release() {
        if (LoggingStateStore.isActive(appContext)) return
        csvCollectJob?.cancel()
        speedPolicyJob?.cancel()
        speedPolicyJob = null
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

    companion object {
        private const val SPEED_POLICY_TICK_MS = 1_000L
        private const val AUTO_PAUSE_BELOW_KMH = 2f
        private const val AUTO_PAUSE_HOLD_MS = 10_000L
        private const val AUTO_RESUME_MIN_KMH = 2f
        private const val AUTO_RESUME_HOLD_MS = 2_000L
    }
}
