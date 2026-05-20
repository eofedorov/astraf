package com.astraf.hrgpslogger

import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.lifecycle.lifecycleScope
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DirectionsBike
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.Alignment
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.astraf.hrgpslogger.RecordingPhase
import com.astraf.hrgpslogger.ui.screens.RideScreen
import com.astraf.hrgpslogger.ui.screens.SettingsScreen
import com.astraf.hrgpslogger.strava.StravaIntegration
import com.astraf.hrgpslogger.ui.screens.TracksScreen
import com.astraf.hrgpslogger.ui.theme.HrGpsLoggerTheme
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    private lateinit var session: LoggerSession
    private lateinit var stravaIntegration: StravaIntegration
    private var permissionsGranted by mutableStateOf(false)
    private var batteryOptimizationEnabled by mutableStateOf(false)
    private var pendingBleScanAfterPermissions = false

    private val permissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { result ->
            permissionsGranted = result.values.all { it }
            if (permissionsGranted) {
                session.ensureLocationTracking()
                LoggingRecovery.startIfNeeded(this@MainActivity)
                if (pendingBleScanAfterPermissions) {
                    pendingBleScanAfterPermissions = false
                    startPreferredBleScan()
                }
            } else {
                pendingBleScanAfterPermissions = false
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        LockScreenHelper.enable(this)
        enableEdgeToEdge()

        val app = application as HrGpsLoggerApp
        session = app.session
        stravaIntegration = app.stravaIntegration
        handleStravaCallback(intent?.data)
        permissionsGranted = Permissions.hasAll(this)
        refreshBatteryOptimizationState()
        if (permissionsGranted) {
            session.ensureLocationTracking()
            LoggingRecovery.startIfNeeded(this)
            startPreferredBleScan()
        } else {
            pendingBleScanAfterPermissions = true
            permissionLauncher.launch(Permissions.requiredRuntimePermissions())
        }

        setContent {
            HrGpsLoggerTheme {
                LoggerAppScreen(
                    permissionsGranted = permissionsGranted,
                    showBatteryOptimizationButton = batteryOptimizationEnabled,
                    loggingPersisted = LoggingStateStore.isActive(this@MainActivity),
                    session = session,
                    stravaIntegration = stravaIntegration,
                    onConnectBle = { onConnectBleClicked() },
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

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleStravaCallback(intent.data)
    }

    override fun onResume() {
        super.onResume()
        LockScreenHelper.enable(this)
        refreshBatteryOptimizationState()
    }

    private fun handleStravaCallback(uri: Uri?) {
        if (uri == null || uri.scheme != "astraf" || uri.host != "localhost") return
        if (uri.path?.contains("strava-auth") != true) return
        lifecycleScope.launch {
            stravaIntegration.handleAuthorizationCallback(uri)
        }
    }

    override fun onDestroy() {
        if (!LoggingStateStore.isActive(this) && !LoggingForegroundService.isRunning) {
            session.release()
        }
        super.onDestroy()
    }

    private fun onConnectBleClicked() {
        if (!Permissions.hasAll(this)) {
            pendingBleScanAfterPermissions = true
            permissionLauncher.launch(Permissions.requiredRuntimePermissions())
            return
        }
        startPreferredBleScan()
    }

    private fun startPreferredBleScan() {
        session.bleClient.scanAndConnectToPreferredDevice()
    }

    private fun refreshBatteryOptimizationState() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
            batteryOptimizationEnabled = false
            return
        }
        val powerManager = getSystemService(POWER_SERVICE) as PowerManager
        batteryOptimizationEnabled = !powerManager.isIgnoringBatteryOptimizations(packageName)
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

private val TabBarHeight = 48.dp

private enum class AppTab {
    Settings,
    Ride,
    Tracks,
}

@Composable
private fun LoggerAppScreen(
    permissionsGranted: Boolean,
    showBatteryOptimizationButton: Boolean,
    loggingPersisted: Boolean,
    session: LoggerSession,
    stravaIntegration: StravaIntegration,
    onConnectBle: () -> Unit,
    onStartLogging: () -> Unit,
    onPauseLogging: () -> Unit,
    onResumeLogging: () -> Unit,
    onFinishLogging: () -> Unit,
    onRestorePausedSession: () -> Unit,
    onOpenBatterySettings: () -> Unit,
) {
    val recordingPhase by session.csvLogger.phase.collectAsStateWithLifecycle()

    var selectedTab by rememberSaveable {
        mutableStateOf(if (loggingPersisted) AppTab.Ride else AppTab.Settings)
    }

    val context = LocalContext.current
    LaunchedEffect(loggingPersisted, recordingPhase) {
        if (!loggingPersisted) return@LaunchedEffect
        val fileName = LoggingStateStore.getCsvFileName(context) ?: return@LaunchedEffect
        when {
            LoggingStateStore.isPaused(context) && recordingPhase == RecordingPhase.Idle -> {
                onRestorePausedSession()
            }
            !LoggingStateStore.isPaused(context) -> {
                session.ensureTrackLoadedFromFile(fileName)
            }
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
            ThinTabBar(
                selectedTab = selectedTab,
                onTabSelected = { selectedTab = it },
            )
        },
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize()) {
            SettingsScreen(
                session = session,
                stravaIntegration = stravaIntegration,
                showBatteryOptimizationButton = showBatteryOptimizationButton,
                onConnect = onConnectBle,
                onOpenBatterySettings = onOpenBatterySettings,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .tabPanelVisible(selectedTab == AppTab.Settings)
                    .tabZIndex(selectedTab == AppTab.Settings),
            )
            RideScreen(
                session = session,
                recordingPhase = recordingPhase,
                isMapActive = selectedTab == AppTab.Ride,
                onStartLogging = onStartLogging,
                onPauseLogging = onPauseLogging,
                onResumeLogging = onResumeLogging,
                onFinishLogging = onFinishLogging,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(bottom = TabBarHeight)
                    .tabPanelVisible(selectedTab == AppTab.Ride)
                    .tabZIndex(selectedTab == AppTab.Ride),
            )
            TracksScreen(
                session = session,
                stravaIntegration = stravaIntegration,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .tabPanelVisible(selectedTab == AppTab.Tracks)
                    .tabZIndex(selectedTab == AppTab.Tracks),
            )
        }
    }
}

@Composable
private fun ThinTabBar(
    selectedTab: AppTab,
    onTabSelected: (AppTab) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(48.dp)
            .background(MaterialTheme.colorScheme.surfaceContainer),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = { onTabSelected(AppTab.Settings) }) {
            Icon(
                imageVector = Icons.Default.Settings,
                contentDescription = stringResource(R.string.tab_start),
                tint = if (selectedTab == AppTab.Settings) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
            )
        }
        IconButton(onClick = { onTabSelected(AppTab.Ride) }) {
            Icon(
                imageVector = Icons.Default.DirectionsBike,
                contentDescription = stringResource(R.string.tab_ride),
                tint = if (selectedTab == AppTab.Ride) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
            )
        }
        IconButton(onClick = { onTabSelected(AppTab.Tracks) }) {
            Icon(
                imageVector = Icons.Default.History,
                contentDescription = stringResource(R.string.tab_tracks),
                tint = if (selectedTab == AppTab.Tracks) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
            )
        }
    }
}

private fun Modifier.tabPanelVisible(visible: Boolean): Modifier =
    if (visible) {
        this
    } else {
        then(
            Modifier
                .size(0.dp)
                .alpha(0f),
        )
    }

private fun Modifier.tabZIndex(selected: Boolean): Modifier =
    zIndex(if (selected) 1f else 0f)
