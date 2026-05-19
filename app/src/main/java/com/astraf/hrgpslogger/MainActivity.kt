package com.astraf.hrgpslogger

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DirectionsBike
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.astraf.hrgpslogger.RecordingPhase
import com.astraf.hrgpslogger.ui.screens.RideScreen
import com.astraf.hrgpslogger.ui.screens.StartScreen
import com.astraf.hrgpslogger.ui.screens.TracksScreen
import com.astraf.hrgpslogger.ui.theme.HrGpsLoggerTheme

class MainActivity : ComponentActivity() {

    private lateinit var session: LoggerSession
    private var permissionsGranted by mutableStateOf(false)

    private val permissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { result ->
            permissionsGranted = result.values.all { it }
            if (permissionsGranted) {
                session.ensureLocationTracking()
                LoggingRecovery.startIfNeeded(this@MainActivity)
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        LockScreenHelper.enable(this)
        enableEdgeToEdge()

        session = (application as HrGpsLoggerApp).session
        permissionsGranted = Permissions.hasAll(this)
        if (permissionsGranted) {
            session.ensureLocationTracking()
            LoggingRecovery.startIfNeeded(this)
        }

        setContent {
            HrGpsLoggerTheme {
                LoggerAppScreen(
                    permissionsGranted = permissionsGranted,
                    loggingPersisted = LoggingStateStore.isActive(this@MainActivity),
                    onRequestPermissions = { requestPermissions() },
                    session = session,
                    onStartLogging = { startBackgroundLogging() },
                    onPauseLogging = { pauseBackgroundLogging() },
                    onResumeLogging = { resumeBackgroundLogging() },
                    onFinishLogging = { stopBackgroundLogging() },
                    onRestorePausedSession = { restorePausedSession() },
                    onOpenBatterySettings = { openBatteryOptimizationSettings() },
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        LockScreenHelper.enable(this)
    }

    override fun onDestroy() {
        if (!LoggingStateStore.isActive(this) && !LoggingForegroundService.isRunning) {
            session.release()
        }
        super.onDestroy()
    }

    private fun requestPermissions() {
        permissionLauncher.launch(Permissions.requiredRuntimePermissions())
    }

    private fun startBackgroundLogging() {
        session.ensureLocationTracking()
        when (session.csvLogger.phase.value) {
            RecordingPhase.Paused -> LoggingForegroundService.resumeSession(this)
            else -> {
                val resume = LoggingStateStore.isActive(this) && !LoggingStateStore.isPaused(this)
                LoggingForegroundService.start(this, resume = resume)
            }
        }
    }

    private fun pauseBackgroundLogging() {
        LoggingForegroundService.pause(this)
    }

    private fun resumeBackgroundLogging() {
        LoggingForegroundService.resumeSession(this)
    }

    private fun stopBackgroundLogging() {
        LoggingForegroundService.stop(this)
    }

    private fun restorePausedSession() {
        val fileName = LoggingStateStore.getCsvFileName(this) ?: return
        session.restorePausedSession(fileName)
    }

    private fun openBatteryOptimizationSettings() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) return
        val packageUri = Uri.parse("package:$packageName")
        val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
            data = packageUri
        }
        if (intent.resolveActivity(packageManager) != null) {
            startActivity(intent)
        } else {
            startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
        }
    }
}

private enum class AppTab {
    Start,
    Ride,
    Tracks,
}

@Composable
private fun LoggerAppScreen(
    permissionsGranted: Boolean,
    loggingPersisted: Boolean,
    onRequestPermissions: () -> Unit,
    session: LoggerSession,
    onStartLogging: () -> Unit,
    onPauseLogging: () -> Unit,
    onResumeLogging: () -> Unit,
    onFinishLogging: () -> Unit,
    onRestorePausedSession: () -> Unit,
    onOpenBatterySettings: () -> Unit,
) {
    val recordingPhase by session.csvLogger.phase.collectAsStateWithLifecycle()
    val hasActiveSession = recordingPhase != RecordingPhase.Idle

    var selectedTab by rememberSaveable {
        mutableStateOf(if (loggingPersisted) AppTab.Ride else AppTab.Start)
    }

    val context = LocalContext.current
    LaunchedEffect(loggingPersisted) {
        if (loggingPersisted &&
            LoggingStateStore.isPaused(context) &&
            recordingPhase == RecordingPhase.Idle
        ) {
            onRestorePausedSession()
        }
    }

    LaunchedEffect(recordingPhase) {
        if (recordingPhase != RecordingPhase.Idle) {
            selectedTab = AppTab.Ride
        }
    }

    LaunchedEffect(recordingPhase) {
        if (recordingPhase == RecordingPhase.Recording) {
            session.ensureTripStatsTracking()
        }
    }

    DisposableEffect(permissionsGranted) {
        if (permissionsGranted) {
            session.ensureLocationTracking()
        }
        onDispose { }
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        bottomBar = {
            NavigationBar {
                NavigationBarItem(
                    selected = selectedTab == AppTab.Start,
                    onClick = { selectedTab = AppTab.Start },
                    icon = { Icon(Icons.Default.Settings, contentDescription = null) },
                    label = { Text(stringResource(R.string.tab_start)) },
                )
                NavigationBarItem(
                    selected = selectedTab == AppTab.Ride,
                    onClick = { selectedTab = AppTab.Ride },
                    icon = { Icon(Icons.Default.DirectionsBike, contentDescription = null) },
                    label = { Text(stringResource(R.string.tab_ride)) },
                )
                NavigationBarItem(
                    selected = selectedTab == AppTab.Tracks,
                    onClick = { selectedTab = AppTab.Tracks },
                    icon = { Icon(Icons.Default.History, contentDescription = null) },
                    label = { Text(stringResource(R.string.tab_tracks)) },
                )
            }
        },
    ) { padding ->
        val contentModifier = Modifier
            .fillMaxSize()
            .padding(padding)

        when (selectedTab) {
            AppTab.Start -> StartScreen(
                permissionsGranted = permissionsGranted,
                session = session,
                hasActiveSession = hasActiveSession,
                onRequestPermissions = onRequestPermissions,
                onStartLogging = {
                    onStartLogging()
                    selectedTab = AppTab.Ride
                },
                onOpenBatterySettings = onOpenBatterySettings,
                modifier = contentModifier,
            )
            AppTab.Ride -> RideScreen(
                session = session,
                recordingPhase = recordingPhase,
                loggingPersisted = loggingPersisted,
                isMapActive = selectedTab == AppTab.Ride,
                onStartLogging = {
                    onStartLogging()
                },
                onPauseLogging = onPauseLogging,
                onResumeLogging = onResumeLogging,
                onFinishLogging = onFinishLogging,
                modifier = contentModifier,
            )
            AppTab.Tracks -> TracksScreen(
                session = session,
                modifier = contentModifier,
            )
        }
    }
}
