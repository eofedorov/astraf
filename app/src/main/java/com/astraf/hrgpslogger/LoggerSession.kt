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
    val gpsRideController = GpsRideController()

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
        if (tripStatsTracker.stats.value.startedAtMillis == null) {
            tripStatsTracker.start(scope)
        } else {
            tripStatsTracker.resume(scope)
        }
    }

    fun restorePausedSession(fileName: String) {
        csvLogger.restorePausedSession(fileName)
        restoreTrackAndStatsFromFile(fileName)
        gpsRideController.restoreFromAcceptedPoints(
            points = TrackCsvParser.parseAcceptedPoints(File(appContext.filesDir, fileName)),
            segmentsCount = activeTrackStore.segments.value.size,
        )
        LoggingStateStore.getBleAddress(appContext)?.let { address ->
            if (bleClient.connectionState.value != BleConnectionState.READY) {
                bleClient.connect(address)
            }
        }
        reconcileSpeedBasedRidePolicy()
        startCollectJob()
    }

    fun ensureTrackLoadedFromFile(fileName: String) {
        if (activeTrackStore.segments.value.isNotEmpty()) return
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
                csvLogger.currentFile()?.also { restored ->
                    restoreTrackAndStatsFromFile(restored.name)
                    gpsRideController.forceNewSegment()
                }
            }
            csvLogger.phase.value == RecordingPhase.Idle -> {
                if (LoggingStateStore.isActive(appContext)) {
                    persistedFileName?.let { resumeFromPersistedFile(it) }
                } else {
                    beginNewSessionWaitingForGps()
                    null
                }
            }
            else -> csvLogger.currentFile()
        }

        when (csvLogger.phase.value) {
            RecordingPhase.Recording -> {
                if (tripStatsTracker.stats.value.startedAtMillis == null) {
                    tripStatsTracker.start(scope)
                } else {
                    tripStatsTracker.resume(scope)
                }
                startCollectJob()
                reconcileSpeedBasedRidePolicy()
            }
            RecordingPhase.WaitingForGps -> {
                startCollectJob()
            }
            else -> Unit
        }

        return file?.name ?: csvLogger.currentFileName()
    }

    fun pauseCsvCollection(manualPause: Boolean = true) {
        csvCollectJob?.cancel()
        csvCollectJob = null
        when (csvLogger.phase.value) {
            RecordingPhase.WaitingForGps -> {
                csvLogger.finishLogging()
                tripStatsTracker.reset()
                gpsRideController.reset()
                LoggingStateStore.clear(appContext)
            }
            RecordingPhase.Recording -> {
                csvLogger.pauseLogging()
                tripStatsTracker.pause()
                gpsRideController.forceNewSegment()
                persistLoggingState(paused = true, manualPauseWhilePaused = manualPause)
            }
            else -> return
        }
        reconcileSpeedBasedRidePolicy()
    }

    fun resumeCsvCollection() {
        if (csvLogger.phase.value != RecordingPhase.Paused) return
        csvLogger.currentFileName()?.let { restoreTrackAndStatsFromFile(it) }
        csvLogger.resumeWriting()
        gpsRideController.forceNewSegment()
        tripStatsTracker.resume(scope)
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
        gpsRideController.reset()
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
            RecordingPhase.Idle, RecordingPhase.WaitingForGps -> return
            RecordingPhase.Paused ->
                if (LoggingStateStore.isManualPauseWhilePaused(appContext)) return
            RecordingPhase.Recording -> Unit
        }

        speedPolicyJob = combine(
            csvLogger.phase,
            gpsRideController.speedKmh,
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
                    RecordingPhase.Idle, RecordingPhase.WaitingForGps -> Unit
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

    private fun beginNewSessionWaitingForGps() {
        activeTrackStore.clear()
        gpsRideController.beginWaitingForFirstFix()
        tripStatsTracker.reset()
        tripStatsTracker.beginWaitingForGps()
        csvLogger.beginWaitingForGps()
    }

    private fun resumeFromPersistedFile(fileName: String): File? {
        val file = csvLogger.resumeLogging(fileName) ?: return null
        restoreTrackAndStatsFromFile(fileName)
        val points = TrackCsvParser.parseAcceptedPoints(file)
        gpsRideController.restoreFromAcceptedPoints(points, TrackCsvParser.segmentsCount(points))
        return file
    }

    private fun restoreTrackAndStatsFromFile(fileName: String) {
        val file = File(appContext.filesDir, fileName)
        if (!file.exists()) return
        val points = TrackCsvParser.parseAcceptedPoints(file)
        activeTrackStore.restoreFromAcceptedPoints(points)
        parseTrackStartMillis(fileName)?.let { startedAtMillis ->
            tripStatsTracker.restoreFromAcceptedPoints(points, startedAtMillis)
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
                if (location == null) return@onEach
                when (val result = gpsRideController.processRaw(location)) {
                    is GpsFilterResult.Accepted -> handleAcceptedPoint(result, bpm)
                    is GpsFilterResult.Rejected -> Unit
                }
            }
            .launchIn(scope)
    }

    private fun handleAcceptedPoint(result: GpsFilterResult.Accepted, bpm: Int?) {
        if (csvLogger.phase.value == RecordingPhase.WaitingForGps) {
            csvLogger.startLoggingAfterFirstFix()
            tripStatsTracker.endWaitingForGps(result.point.timestampMillis)
            tripStatsTracker.start(scope, startedAtMillis = result.point.timestampMillis)
            persistLoggingState(paused = false)
            reconcileSpeedBasedRidePolicy()
        }

        if (csvLogger.phase.value != RecordingPhase.Recording) return

        csvLogger.writeAcceptedPoint(result.point, bpm)
        activeTrackStore.appendAccepted(result.point, result.newSegment)
        tripStatsTracker.onAcceptedPoint(
            point = result.point,
            newSegment = result.newSegment,
            currentSpeedKmh = gpsRideController.speedKmh.value,
        )
    }

    companion object {
        private const val SPEED_POLICY_TICK_MS = 1_000L
        private const val AUTO_PAUSE_BELOW_KMH = 2f
        private const val AUTO_PAUSE_HOLD_MS = 10_000L
        private const val AUTO_RESUME_MIN_KMH = 2f
        private const val AUTO_RESUME_HOLD_MS = 2_000L
    }
}
