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
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
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
                LoggerScreen(
                    permissionsGranted = permissionsGranted,
                    loggingPersisted = LoggingStateStore.isActive(this@MainActivity),
                    onRequestPermissions = { requestPermissions() },
                    session = session,
                    onStartLogging = { startBackgroundLogging() },
                    onStopLogging = { stopBackgroundLogging() },
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
        val resume = LoggingStateStore.isActive(this)
        LoggingForegroundService.start(this, resume = resume)
    }

    private fun stopBackgroundLogging() {
        LoggingForegroundService.stop(this)
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

@Composable
private fun LoggerScreen(
    permissionsGranted: Boolean,
    loggingPersisted: Boolean,
    onRequestPermissions: () -> Unit,
    session: LoggerSession,
    onStartLogging: () -> Unit,
    onStopLogging: () -> Unit,
    onOpenBatterySettings: () -> Unit,
) {
    val bleClient = session.bleClient
    val locationTracker = session.locationTracker
    val csvLogger = session.csvLogger

    val devices by bleClient.devices.collectAsStateWithLifecycle()
    val connectionState by bleClient.connectionState.collectAsStateWithLifecycle()
    val heartRate by bleClient.heartRateBpm.collectAsStateWithLifecycle()
    val bleStatus by bleClient.statusMessage.collectAsStateWithLifecycle()
    val location by locationTracker.location.collectAsStateWithLifecycle()
    val speedKmh by locationTracker.speedKmh.collectAsStateWithLifecycle()
    val gpsStatus by locationTracker.statusMessage.collectAsStateWithLifecycle()
    val isLogging by csvLogger.isLogging.collectAsStateWithLifecycle()
    val csvPath by csvLogger.currentFilePath.collectAsStateWithLifecycle()
    val isBackgroundServiceRunning = LoggingForegroundService.isRunning

    var selectedAddress by remember { mutableStateOf<String?>(null) }

    DisposableEffect(permissionsGranted) {
        if (permissionsGranted) {
            session.ensureLocationTracking()
        }
        onDispose { }
    }

    Scaffold(modifier = Modifier.fillMaxSize()) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = "HR GPS Logger",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
            )

            if (!permissionsGranted) {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text("Нужны разрешения Bluetooth, геолокации и уведомлений.")
                        Spacer(modifier = Modifier.height(8.dp))
                        Button(onClick = onRequestPermissions) {
                            Text("Выдать разрешения")
                        }
                    }
                }
                return@Column
            }

            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("Пульс: ${heartRate?.let { "$it уд/мин" } ?: "—"}")
                    Text(
                        "GPS: ${
                            location?.let {
                                "${"%.5f".format(it.latitude)}, ${"%.5f".format(it.longitude)} (±${"%.0f".format(it.accuracyMeters)} м)"
                            } ?: "—"
                        }",
                    )
                    Text(
                        "Скорость: ${
                            speedKmh?.let { "${"%.1f".format(it)} км/ч" } ?: "—"
                        }",
                    )
                    Text("BLE: $bleStatus")
                    Text("Статус GPS: $gpsStatus")
                    Text("Подключение: ${connectionState.name}")
                    Text("CSV: ${csvPath ?: "—"}")
                    if (isBackgroundServiceRunning || loggingPersisted) {
                        Text(
                            text = if (isBackgroundServiceRunning) {
                                "Фон: активен (экран может быть выключен)"
                            } else {
                                "Запись восстанавливается…"
                            },
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                }
            }

            if (isLogging) {
                Text(
                    text = "Запись продолжается в фоне. Не закрывайте приложение из списка недавних во время тренировки.",
                    style = MaterialTheme.typography.bodySmall,
                )
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = { bleClient.startScan() },
                    enabled = !isLogging,
                ) { Text("Scan") }
                Button(
                    onClick = {
                        selectedAddress?.let { bleClient.connect(it) }
                    },
                    enabled = selectedAddress != null && connectionState != BleConnectionState.CONNECTING,
                ) { Text("Connect") }
                Button(onClick = { bleClient.disconnect() }) { Text("Disconnect") }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = onStartLogging,
                    enabled = !isLogging,
                ) { Text("Start logging") }
                Button(
                    onClick = onStopLogging,
                    enabled = isLogging,
                ) { Text("Stop logging") }
            }

            Button(
                onClick = onOpenBatterySettings,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Отключить оптимизацию батареи")
            }

            HorizontalDivider()
            Text("Найденные устройства", fontWeight = FontWeight.SemiBold)

            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                items(devices, key = { it.address }) { device ->
                    val selected = device.address == selectedAddress
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { selectedAddress = device.address },
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text(
                                text = device.name,
                                fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                            )
                            Text(text = device.address, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }
        }
    }
}
