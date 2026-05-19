package com.astraf.hrgpslogger.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.astraf.hrgpslogger.BleConnectionState
import com.astraf.hrgpslogger.BleHeartRateClient
import com.astraf.hrgpslogger.LoggerSession
import com.astraf.hrgpslogger.R

@Composable
fun SettingsScreen(
    session: LoggerSession,
    showBatteryOptimizationButton: Boolean,
    onConnect: () -> Unit,
    onOpenBatterySettings: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val bleClient = session.bleClient
    val connectionState by bleClient.connectionState.collectAsStateWithLifecycle()
    val heartRate by bleClient.heartRateBpm.collectAsStateWithLifecycle()
    val isScanning by bleClient.isScanning.collectAsStateWithLifecycle()
    val bleStatus by bleClient.statusMessage.collectAsStateWithLifecycle()

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
