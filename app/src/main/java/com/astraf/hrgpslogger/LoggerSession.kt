package com.astraf.hrgpslogger

import android.content.Context
import android.os.SystemClock
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
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
    val elevationClimbTracker = ElevationClimbTracker()
    val activeTrackStore = ActiveTrackStore()
    val gpsRideController = GpsRideController()

    private val rideAutoPausePolicy = RideAutoPausePolicy()

    private var csvCollectJob: Job? = null
    private var motionObserveJob: Job? = null
    private var autoPausePolicyJob: Job? = null

    private val _isAutoPaused = MutableStateFlow(false)
    val isAutoPaused: StateFlow<Boolean> = _isAutoPaused.asStateFlow()

    private val _manualPauseWhilePaused = MutableStateFlow(false)
    val manualPauseWhilePaused: StateFlow<Boolean> = _manualPauseWhilePaused.asStateFlow()

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
        syncPauseKindFromStore()
        reconcileAutoPausePolicy()
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
                reconcileAutoPausePolicy()
            }
            RecordingPhase.WaitingForGps -> {
                startCollectJob()
                reconcileAutoPausePolicy()
            }
            RecordingPhase.Paused -> {
                syncPauseKindFromStore()
                reconcileAutoPausePolicy()
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
                elevationClimbTracker.reset()
                gpsRideController.reset()
                clearPauseKind()
                LoggingStateStore.clear(appContext)
                stopAutoPausePolicy()
            }
            RecordingPhase.Recording -> {
                csvLogger.pauseLogging()
                tripStatsTracker.pause()
                gpsRideController.forceNewSegment()
                setPauseKind(manualPause)
                persistLoggingState(paused = true, manualPauseWhilePaused = manualPause)
                reconcileAutoPausePolicy()
            }
            else -> return
        }
    }

    fun resumeCsvCollection() {
        if (csvLogger.phase.value != RecordingPhase.Paused) return
        csvLogger.currentFileName()?.let { restoreTrackAndStatsFromFile(it) }
        csvLogger.resumeWriting()
        gpsRideController.forceNewSegment()
        tripStatsTracker.resume(scope)
        clearPauseKind()
        startCollectJob()
        persistLoggingState(paused = false)
        reconcileAutoPausePolicy()
    }

    fun stopCsvCollection() {
        csvCollectJob?.cancel()
        csvCollectJob = null
        stopAutoPausePolicy()
        clearPauseKind()
        csvLogger.currentFileName()?.let { saveTrackMetadata(it) }
        tripStatsTracker.stop()
        tripStatsTracker.reset()
        elevationClimbTracker.reset()
        activeTrackStore.clear()
        gpsRideController.reset()
        csvLogger.finishLogging()
    }

    fun persistLoggingState(
        paused: Boolean = csvLogger.phase.value == RecordingPhase.Paused,
        manualPauseWhilePaused: Boolean = _manualPauseWhilePaused.value,
    ) {
        if (!csvLogger.hasActiveSession() && !LoggingStateStore.isActive(appContext)) return
        if (paused) {
            setPauseKind(manualPauseWhilePaused)
        } else {
            clearPauseKind()
        }
        LoggingStateStore.save(
            appContext,
            csvFileName = csvLogger.currentFileName(),
            bleAddress = bleClient.connectedDeviceAddress.value
                ?: LoggingStateStore.getBleAddress(appContext),
            paused = paused,
            manualPauseWhilePaused = if (paused) manualPauseWhilePaused else false,
        )
    }

    /** Перезапускает motion-политику автопаузы / автовозобновления. */
    internal fun reconcileAutoPausePolicy() {
        autoPausePolicyJob?.cancel()
        autoPausePolicyJob = null
        rideAutoPausePolicy.resetTimers()

        if (!csvLogger.hasActiveSession()) {
            stopMotionObserveJob()
            return
        }

        when (csvLogger.phase.value) {
            RecordingPhase.Idle -> {
                stopMotionObserveJob()
                return
            }
            RecordingPhase.WaitingForGps, RecordingPhase.Recording -> {
                startMotionObserveJob()
            }
            RecordingPhase.Paused -> {
                startMotionObserveJob()
                if (_manualPauseWhilePaused.value) {
                    stopAutoPausePolicyJobOnly()
                    return
                }
            }
        }

        autoPausePolicyJob = combine(
            csvLogger.phase,
            manualPauseWhilePaused,
            autoPausePolicyTickFlow(),
        ) { phase, manualPause, _ ->
            rideAutoPausePolicy.evaluate(
                phase = phase,
                manualPauseWhilePaused = manualPause,
                nowElapsed = SystemClock.elapsedRealtime(),
            )
        }
            .onEach { action ->
                when (action) {
                    AutoPauseAction.Pause -> applyAutoPauseFromSpeedPolicy()
                    AutoPauseAction.Resume -> applyAutoResumeFromSpeedPolicy()
                    AutoPauseAction.None -> Unit
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
        if (_manualPauseWhilePaused.value) return
        LoggingForegroundService.resumeSession(appContext)
    }

    private fun autoPausePolicyTickFlow() = flow {
        while (true) {
            emit(Unit)
            delay(RideAutoPausePolicy.TICK_MS)
        }
    }

    private fun startMotionObserveJob() {
        if (motionObserveJob?.isActive == true) return
        motionObserveJob = locationTracker.location
            .onEach { location ->
                if (location != null) {
                    rideAutoPausePolicy.onLocation(location)
                }
            }
            .launchIn(scope)
    }

    private fun stopMotionObserveJob() {
        motionObserveJob?.cancel()
        motionObserveJob = null
    }

    private fun stopAutoPausePolicyJobOnly() {
        autoPausePolicyJob?.cancel()
        autoPausePolicyJob = null
        rideAutoPausePolicy.resetTimers()
    }

    private fun stopAutoPausePolicy() {
        stopAutoPausePolicyJobOnly()
        stopMotionObserveJob()
        rideAutoPausePolicy.reset()
    }

    private fun setPauseKind(manualPause: Boolean) {
        _manualPauseWhilePaused.value = manualPause
        _isAutoPaused.value = !manualPause
    }

    private fun clearPauseKind() {
        _manualPauseWhilePaused.value = false
        _isAutoPaused.value = false
    }

    private fun syncPauseKindFromStore() {
        if (csvLogger.phase.value != RecordingPhase.Paused) {
            clearPauseKind()
            return
        }
        val manual = LoggingStateStore.isManualPauseWhilePaused(appContext)
        setPauseKind(manual)
    }

    fun release() {
        if (LoggingStateStore.isActive(appContext)) return
        csvCollectJob?.cancel()
        stopAutoPausePolicy()
        bleClient.release()
        locationTracker.release()
        csvLogger.release()
        scope.cancel()
    }

    private fun beginNewSessionWaitingForGps() {
        activeTrackStore.clear()
        gpsRideController.beginWaitingForFirstFix()
        tripStatsTracker.reset()
        elevationClimbTracker.reset()
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
        elevationClimbTracker.restoreFromAcceptedPoints(points)
        parseTrackStartMillis(fileName)?.let { startedAtMillis ->
            tripStatsTracker.restoreFromAcceptedPoints(points, startedAtMillis)
        }
        syncTotalClimbToTripStats()
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
            reconcileAutoPausePolicy()
        }

        if (csvLogger.phase.value != RecordingPhase.Recording) return

        csvLogger.writeAcceptedPoint(result.point, bpm)
        activeTrackStore.appendAccepted(result.point, result.newSegment)
        tripStatsTracker.onAcceptedPoint(
            point = result.point,
            newSegment = result.newSegment,
            currentSpeedKmh = gpsRideController.speedKmh.value,
        )
        elevationClimbTracker.onAcceptedPoint(result.point)
        syncTotalClimbToTripStats()
    }

    private fun syncTotalClimbToTripStats() {
        val climb = if (elevationClimbTracker.hasAltitudeData()) {
            elevationClimbTracker.state.value.totalClimbMeters
        } else {
            null
        }
        tripStatsTracker.updateTotalClimbMeters(climb)
    }

    private fun saveTrackMetadata(csvFileName: String) {
        if (!elevationClimbTracker.hasAltitudeData()) return
        val debug = elevationClimbTracker.debugStats.value
        TrackMetadataStore.save(
            appContext,
            csvFileName,
            TrackMetadata(
                totalClimbMeters = elevationClimbTracker.state.value.totalClimbMeters,
                pointsWithAltitude = debug.pointsWithAltitude,
                pointsWithoutAltitude = debug.pointsWithoutAltitude,
            ),
        )
    }
}
