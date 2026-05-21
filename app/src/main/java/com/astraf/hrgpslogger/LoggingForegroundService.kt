package com.astraf.hrgpslogger

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.text.SpannableString
import android.text.style.ForegroundColorSpan
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach

class LoggingForegroundService : Service() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var wakeLock: PowerManager.WakeLock? = null
    private var notificationUpdateJob: Job? = null
    private var showRecoveringInNotification = false
    private lateinit var session: LoggerSession

    override fun onCreate() {
        super.onCreate()
        session = (application as HrGpsLoggerApp).session
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP, ACTION_FINISH -> {
                stopLoggingAndSelf()
                return START_NOT_STICKY
            }
            ACTION_PAUSE -> {
                pauseLogging()
                return START_STICKY
            }
            ACTION_RESUME_SESSION -> {
                resumeLogging()
                return START_STICKY
            }
        }

        val recoveryResume = intent?.action == ACTION_RESUME ||
            (intent == null && LoggingStateStore.isActive(this) && !LoggingStateStore.isPaused(this))

        if (recoveryResume) {
            startLogging(resume = true)
        } else if (!isRunning) {
            startLogging(resume = false)
        }
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        releaseWakeLock()
        notificationUpdateJob?.cancel()
        serviceScope.cancel()
        isRunning = false
        super.onDestroy()
    }

    private fun startLogging(resume: Boolean) {
        if (isRunning &&
            (session.csvLogger.phase.value == RecordingPhase.Recording ||
                session.csvLogger.phase.value == RecordingPhase.WaitingForGps)
        ) {
            return
        }

        acquireWakeLock()
        session.ensureLocationTracking()

        showRecoveringInNotification = resume

        val csvFileName: String?
        if (resume && LoggingStateStore.isActive(this)) {
            csvFileName = LoggingStateStore.getCsvFileName(this)
            session.startCsvCollection(resumeFileName = csvFileName)
            LoggingStateStore.getBleAddress(this)?.let { address ->
                if (session.bleClient.connectionState.value != BleConnectionState.READY) {
                    session.bleClient.connect(address)
                }
            }
        } else {
            csvFileName = session.startCsvCollection()
            showRecoveringInNotification = false
            LoggingStateStore.save(
                this,
                csvFileName = csvFileName,
                bleAddress = session.bleClient.connectedDeviceAddress.value,
                paused = false,
            )
        }

        if (session.csvLogger.phase.value == RecordingPhase.Recording) {
            showRecoveringInNotification = false
        }

        if (!session.csvLogger.hasActiveSession()) {
            showRecoveringInNotification = false
            return
        }

        session.persistLoggingState(paused = session.csvLogger.phase.value == RecordingPhase.Paused)
        isRunning = true
        promoteForeground(recovering = showRecoveringInNotification)
        startNotificationUpdates()
    }

    private fun pauseLogging() {
        when (session.csvLogger.phase.value) {
            RecordingPhase.Recording -> session.pauseCsvCollection(manualPause = true)
            RecordingPhase.WaitingForGps -> session.stopCsvCollection()
            else -> return
        }
        updateNotification(
            paused = session.csvLogger.phase.value == RecordingPhase.Paused,
            autoPaused = session.isAutoPaused.value,
            waitingForGps = session.csvLogger.phase.value == RecordingPhase.WaitingForGps,
        )
        if (!session.csvLogger.hasActiveSession()) {
            stopLoggingAndSelf()
        }
    }

    private fun resumeLogging() {
        if (session.csvLogger.phase.value != RecordingPhase.Paused) return
        session.ensureLocationTracking()
        session.resumeCsvCollection()
        session.persistLoggingState(paused = false)
        isRunning = true
        showRecoveringInNotification = false
        promoteForeground(recovering = false)
        startNotificationUpdates()
    }

    private fun stopLoggingAndSelf() {
        notificationUpdateJob?.cancel()
        notificationUpdateJob = null
        session.stopCsvCollection()
        LoggingStateStore.clear(this)
        releaseWakeLock()
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        isRunning = false
        stopSelf()
    }

    private fun promoteForeground(recovering: Boolean) {
        val paused = session.csvLogger.phase.value == RecordingPhase.Paused
        val notification = buildNotification(
            recovering = recovering,
            paused = paused,
            autoPaused = paused && session.isAutoPaused.value,
            waitingForGps = session.csvLogger.phase.value == RecordingPhase.WaitingForGps,
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val type = ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION or
                ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE
            ServiceCompat.startForeground(
                this,
                NOTIFICATION_ID,
                notification,
                type,
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun startNotificationUpdates() {
        notificationUpdateJob?.cancel()
        notificationUpdateJob = combine(
            session.csvLogger.phase,
            session.isAutoPaused,
        ) { phase, autoPaused -> phase to autoPaused }
            .onEach { (phase, autoPaused) ->
                if (phase == RecordingPhase.Recording) {
                    showRecoveringInNotification = false
                    session.persistLoggingState(paused = false)
                }
                updateNotification(
                    recovering = showRecoveringInNotification,
                    paused = phase == RecordingPhase.Paused,
                    autoPaused = phase == RecordingPhase.Paused && autoPaused,
                    waitingForGps = phase == RecordingPhase.WaitingForGps,
                )
            }
            .launchIn(serviceScope)
    }

    private fun updateNotification(
        recovering: Boolean = false,
        paused: Boolean = session.csvLogger.phase.value == RecordingPhase.Paused,
        autoPaused: Boolean = paused && session.isAutoPaused.value,
        waitingForGps: Boolean = session.csvLogger.phase.value == RecordingPhase.WaitingForGps,
    ) {
        val manager = getSystemService(NotificationManager::class.java)
        manager.notify(NOTIFICATION_ID, buildNotification(recovering, paused, autoPaused, waitingForGps))
    }

    private fun acquireWakeLock() {
        if (wakeLock?.isHeld == true) return
        val powerManager = getSystemService(POWER_SERVICE) as PowerManager
        wakeLock = powerManager.newWakeLock(
            PowerManager.PARTIAL_WAKE_LOCK,
            "HrGpsLogger::LoggingWakeLock",
        ).apply {
            setReferenceCounted(false)
            acquire(MAX_WAKE_LOCK_MS)
        }
    }

    private fun releaseWakeLock() {
        wakeLock?.let { lock ->
            if (lock.isHeld) lock.release()
        }
        wakeLock = null
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val channel = NotificationChannel(
            CHANNEL_ID,
            getString(R.string.notification_channel_name),
            NotificationManager.IMPORTANCE_LOW,
        ).apply {
            description = getString(R.string.notification_channel_description)
            setShowBadge(false)
        }
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    private fun buildNotification(
        recovering: Boolean = false,
        paused: Boolean = false,
        autoPaused: Boolean = false,
        waitingForGps: Boolean = false,
    ): Notification {
        val titleText = when {
            recovering -> getString(R.string.notification_title_recovering)
            waitingForGps -> getString(R.string.notification_title_waiting_gps)
            paused -> getString(R.string.notification_title_paused)
            else -> getString(R.string.notification_title)
        }
        val contentTitle: CharSequence = if (autoPaused) {
            SpannableString(titleText).apply {
                setSpan(
                    ForegroundColorSpan(RIDE_AUTO_PAUSE_COLOR_ARGB),
                    0,
                    length,
                    SpannableString.SPAN_INCLUSIVE_INCLUSIVE,
                )
            }
        } else {
            titleText
        }

        val openAppIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val toggleAction = if (paused) {
            PendingIntent.getService(
                this,
                2,
                Intent(this, LoggingForegroundService::class.java).apply {
                    action = ACTION_RESUME_SESSION
                },
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            ) to getString(R.string.resume_logging)
        } else {
            PendingIntent.getService(
                this,
                1,
                Intent(this, LoggingForegroundService::class.java).apply {
                    action = ACTION_PAUSE
                },
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            ) to getString(R.string.pause_logging)
        }

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(contentTitle)
            .setContentIntent(openAppIntent)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .addAction(0, toggleAction.second, toggleAction.first)
            .build()
    }

    companion object {
        const val ACTION_STOP = "com.astraf.hrgpslogger.action.STOP_LOGGING"
        const val ACTION_FINISH = "com.astraf.hrgpslogger.action.FINISH_LOGGING"
        const val ACTION_PAUSE = "com.astraf.hrgpslogger.action.PAUSE_LOGGING"
        const val ACTION_RESUME_SESSION = "com.astraf.hrgpslogger.action.RESUME_SESSION"
        const val ACTION_RESUME = "com.astraf.hrgpslogger.action.RESUME_LOGGING"

        private const val CHANNEL_ID = "logging_channel"
        private const val NOTIFICATION_ID = 1001
        private const val MAX_WAKE_LOCK_MS = 12 * 60 * 60 * 1000L

        @Volatile
        var isRunning: Boolean = false
            private set

        fun start(context: Context, resume: Boolean = false) {
            val intent = Intent(context, LoggingForegroundService::class.java).apply {
                if (resume) action = ACTION_RESUME
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun pause(context: Context) {
            context.startService(
                Intent(context, LoggingForegroundService::class.java).apply {
                    action = ACTION_PAUSE
                },
            )
        }

        fun resumeSession(context: Context) {
            val intent = Intent(context, LoggingForegroundService::class.java).apply {
                action = ACTION_RESUME_SESSION
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stop(context: Context) {
            context.startService(
                Intent(context, LoggingForegroundService::class.java).apply {
                    action = ACTION_FINISH
                },
            )
        }
    }
}
