package com.astraf.hrgpslogger.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.astraf.hrgpslogger.BleConnectionState
import com.astraf.hrgpslogger.BleHeartRateClient
import com.astraf.hrgpslogger.CrashLogEntry
import com.astraf.hrgpslogger.CrashLogPrefs
import com.astraf.hrgpslogger.CrashLogStore
import com.astraf.hrgpslogger.LoggerSession
import com.astraf.hrgpslogger.R
import com.astraf.hrgpslogger.strava.StravaIntegration
import com.astraf.hrgpslogger.strava.StravaLinkState
import com.astraf.hrgpslogger.ui.formatTrackDateTime
import kotlinx.coroutines.launch

@Composable
fun SettingsScreen(
    session: LoggerSession,
    stravaIntegration: StravaIntegration,
    showBatteryOptimizationButton: Boolean,
    onConnect: () -> Unit,
    onOpenBatterySettings: () -> Unit,
    onShareLatestCrashLog: () -> Unit,
    onClearCrashLogs: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val bleClient = session.bleClient
    val connectionState by bleClient.connectionState.collectAsStateWithLifecycle()
    val heartRate by bleClient.heartRateBpm.collectAsStateWithLifecycle()
    val isScanning by bleClient.isScanning.collectAsStateWithLifecycle()
    val bleStatus by bleClient.statusMessage.collectAsStateWithLifecycle()
    val stravaLinkState by stravaIntegration.linkState.collectAsStateWithLifecycle()
    val stravaLinkMessage by stravaIntegration.linkMessage.collectAsStateWithLifecycle()
    var crashLogsRevision by remember { mutableStateOf(0) }
    var crashCaptureEnabled by remember {
        mutableStateOf(CrashLogPrefs.isCaptureEnabled(context))
    }
    val crashLogs = remember(crashLogsRevision) { CrashLogStore.list(context) }

    val statusLine = formatBleStatusLine(
        bleClient = bleClient,
        connectionState = connectionState,
        heartRate = heartRate,
        isScanning = isScanning,
        detailStatus = bleStatus,
    )

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            text = stringResource(R.string.settings_screen_title),
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
        )

        Card(modifier = Modifier.fillMaxWidth()) {
            Text(
                text = statusLine,
                modifier = Modifier.padding(12.dp),
                style = MaterialTheme.typography.bodyLarge,
            )
        }

        Button(
            onClick = onConnect,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(stringResource(R.string.scan))
        }

        StravaSettingsCard(
            linkState = stravaLinkState,
            linkMessage = stravaLinkMessage,
            initialClientId = stravaIntegration.getClientId(),
            initialClientSecret = stravaIntegration.getClientSecret(),
            onLinkClick = { clientId, clientSecret ->
                val intent = stravaIntegration.buildAuthorizationIntent(clientId, clientSecret)
                if (intent != null) {
                    context.startActivity(intent)
                }
            },
            onDisconnectClick = {
                scope.launch { stravaIntegration.disconnect() }
            },
        )

        CrashLogsCard(
            crashLogs = crashLogs,
            captureEnabled = crashCaptureEnabled,
            onCaptureEnabledChange = { enabled ->
                CrashLogPrefs.setCaptureEnabled(context, enabled)
                crashCaptureEnabled = enabled
            },
            onShareLatest = onShareLatestCrashLog,
            onClear = {
                onClearCrashLogs()
                crashLogsRevision++
            },
        )

        if (showBatteryOptimizationButton) {
            Button(
                onClick = onOpenBatterySettings,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(stringResource(R.string.battery_optimization))
            }
        }
    }
}

@Composable
private fun CrashLogsCard(
    crashLogs: List<CrashLogEntry>,
    captureEnabled: Boolean,
    onCaptureEnabledChange: (Boolean) -> Unit,
    onShareLatest: () -> Unit,
    onClear: () -> Unit,
) {
    val latest = crashLogs.firstOrNull()
    val captureLabel = stringResource(R.string.crash_logs_capture_enabled)
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = stringResource(R.string.crash_logs_section_title),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f),
                )
                Switch(
                    checked = captureEnabled,
                    onCheckedChange = onCaptureEnabledChange,
                    modifier = Modifier.semantics {
                        contentDescription = captureLabel
                    },
                )
            }
            if (crashLogs.isEmpty()) {
                Text(
                    text = stringResource(R.string.crash_logs_empty),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                Text(
                    text = stringResource(R.string.crash_logs_count, crashLogs.size),
                    style = MaterialTheme.typography.bodyMedium,
                )
                latest?.let { entry ->
                    Text(
                        text = stringResource(
                            R.string.crash_logs_latest,
                            formatTrackDateTime(entry.timestampMillis),
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Button(
                    onClick = onShareLatest,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(stringResource(R.string.crash_logs_share))
                }
                OutlinedButton(
                    onClick = onClear,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(stringResource(R.string.crash_logs_clear))
                }
            }
        }
    }
}

@Composable
private fun StravaSettingsCard(
    linkState: StravaLinkState,
    linkMessage: String?,
    initialClientId: String,
    initialClientSecret: String,
    onLinkClick: (clientId: String, clientSecret: String) -> Unit,
    onDisconnectClick: () -> Unit,
) {
    var clientId by remember(initialClientId) { mutableStateOf(initialClientId) }
    var clientSecret by remember(initialClientSecret) { mutableStateOf(initialClientSecret) }
    var showCredentialsDialog by remember { mutableStateOf(false) }

    if (showCredentialsDialog) {
        AlertDialog(
            onDismissRequest = { showCredentialsDialog = false },
            title = { Text(stringResource(R.string.strava_link)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = stringResource(R.string.strava_oauth_hint),
                        style = MaterialTheme.typography.bodySmall,
                    )
                    OutlinedTextField(
                        value = clientId,
                        onValueChange = { clientId = it },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text(stringResource(R.string.strava_client_id)) },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    )
                    OutlinedTextField(
                        value = clientSecret,
                        onValueChange = { clientSecret = it },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text(stringResource(R.string.strava_client_secret)) },
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showCredentialsDialog = false
                        onLinkClick(clientId, clientSecret)
                    },
                ) {
                    Text(stringResource(R.string.strava_link))
                }
            },
            dismissButton = {
                TextButton(onClick = { showCredentialsDialog = false }) {
                    Text(stringResource(R.string.finish_dialog_cancel))
                }
            },
        )
    }

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = stringResource(R.string.strava_section_title),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            when (linkState) {
                StravaLinkState.NotLinked -> {
                    Text(
                        text = stringResource(R.string.strava_not_linked),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    StravaLinkButton(
                        onClick = {
                            if (clientId.isNotBlank() && clientSecret.isNotBlank()) {
                                onLinkClick(clientId, clientSecret)
                            } else {
                                showCredentialsDialog = true
                            }
                        },
                    )
                }
                is StravaLinkState.Linked -> {
                    val statusText = linkState.athleteName?.let { name ->
                        stringResource(R.string.strava_linked, name)
                    } ?: stringResource(R.string.strava_linked_unknown)
                    Text(
                        text = statusText,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    OutlinedButton(
                        onClick = onDisconnectClick,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(stringResource(R.string.strava_disconnect))
                    }
                }
            }
            linkMessage?.let { message ->
                Text(
                    text = message,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }
        }
    }
}

@Composable
private fun StravaLinkButton(onClick: () -> Unit) {
    Button(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text(stringResource(R.string.strava_link))
    }
}

@Composable
private fun formatBleStatusLine(
    bleClient: BleHeartRateClient,
    connectionState: BleConnectionState,
    heartRate: Int?,
    isScanning: Boolean,
    detailStatus: String,
): String {
    if (!bleClient.isBluetoothAvailable()) {
        return stringResource(R.string.ble_status_bluetooth_off)
    }
    return when (connectionState) {
        BleConnectionState.CONNECTING,
        BleConnectionState.CONNECTED,
        -> stringResource(R.string.ble_status_connecting)
        BleConnectionState.READY -> {
            if (heartRate != null && heartRate > 0) {
                stringResource(R.string.ble_status_connected_hr, heartRate)
            } else {
                stringResource(R.string.ble_status_connected)
            }
        }
        BleConnectionState.DISCONNECTED -> {
            when {
                isScanning -> stringResource(R.string.ble_status_searching)
                detailStatus == "Пульсометр не найден" -> detailStatus
                detailStatus == "Нет разрешения Bluetooth" -> detailStatus
                detailStatus.startsWith("Ошибка") -> detailStatus
                else -> stringResource(R.string.ble_status_not_connected)
            }
        }
    }
}
