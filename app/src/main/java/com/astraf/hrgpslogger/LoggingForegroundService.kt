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
    private lateinit var session: LoggerSession

    override fun onCreate() {
        super.onCreate()
        session = (application as HrGpsLoggerApp).session
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                stopLoggingAndSelf()
                return START_NOT_STICKY
            }
        }

        val resume = intent?.action == ACTION_RESUME ||
            (intent == null && LoggingStateStore.isActive(this))

        startLogging(resume)
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
        if (isRunning && session.csvLogger.isLogging.value) return

        acquireWakeLock()
        session.ensureLocationTracking()

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
            LoggingStateStore.save(
                this,
                csvFileName = csvFileName,
                bleAddress = session.bleClient.connectedDeviceAddress.value,
            )
        }

        session.persistLoggingState()
        isRunning = true

        val notification = buildNotification(
            bpm = session.bleClient.heartRateBpm.value,
            location = session.locationTracker.location.value,
            speedKmh = session.locationTracker.speedKmh.value,
            recovering = resume,
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

        notificationUpdateJob?.cancel()
        notificationUpdateJob = combine(
            session.bleClient.heartRateBpm,
            session.locationTracker.location,
            session.locationTracker.speedKmh,
        ) { bpm, location, speedKmh -> Triple(bpm, location, speedKmh) }
            .onEach { (bpm, location, speedKmh) ->
                session.persistLoggingState()
                val manager = getSystemService(NotificationManager::class.java)
                manager.notify(
                    NOTIFICATION_ID,
                    buildNotification(bpm, location, speedKmh, recovering = false),
                )
            }
            .launchIn(serviceScope)
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
        bpm: Int?,
        location: LocationSample?,
        speedKmh: Float?,
        recovering: Boolean = false,
    ): Notification {
        val bpmText = bpm?.let { getString(R.string.notification_bpm, it) }
            ?: getString(R.string.notification_bpm_unknown)
        val locationText = location?.let {
            getString(
                R.string.notification_location,
                it.latitude,
                it.longitude,
            )
        } ?: getString(R.string.notification_location_unknown)
        val speedText = speedKmh?.let { getString(R.string.notification_speed, it) }
            ?: getString(R.string.notification_speed_unknown)

        val title = if (recovering) {
            getString(R.string.notification_title_recovering)
        } else {
            getString(R.string.notification_title)
        }

        val openAppIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val stopIntent = PendingIntent.getService(
            this,
            1,
            Intent(this, LoggingForegroundService::class.java).apply { action = ACTION_STOP },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText("$bpmText · $speedText")
            .setStyle(
                NotificationCompat.BigTextStyle().bigText("$bpmText\n$speedText\n$locationText"),
            )
            .setContentIntent(openAppIntent)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .addAction(0, getString(R.string.notification_stop), stopIntent)
            .build()
    }

    companion object {
        const val ACTION_STOP = "com.astraf.hrgpslogger.action.STOP_LOGGING"
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

        fun stop(context: Context) {
            context.startService(
                Intent(context, LoggingForegroundService::class.java).apply {
                    action = ACTION_STOP
                },
            )
        }
    }
}
