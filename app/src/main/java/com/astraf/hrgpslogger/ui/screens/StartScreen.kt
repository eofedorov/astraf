package com.astraf.hrgpslogger.ui.screens

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
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.astraf.hrgpslogger.BleConnectionState
import com.astraf.hrgpslogger.LoggerSession
import com.astraf.hrgpslogger.R

@Composable
fun StartScreen(
    permissionsGranted: Boolean,
    session: LoggerSession,
    hasActiveSession: Boolean,
    onRequestPermissions: () -> Unit,
    onStartLogging: () -> Unit,
    onOpenBatterySettings: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val bleClient = session.bleClient
    val devices by bleClient.devices.collectAsStateWithLifecycle()
    val connectionState by bleClient.connectionState.collectAsStateWithLifecycle()
    val bleStatus by bleClient.statusMessage.collectAsStateWithLifecycle()

    var selectedAddress by remember { mutableStateOf<String?>(null) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            text = stringResource(R.string.app_name),
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
        )

        if (!permissionsGranted) {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text(stringResource(R.string.permissions_required))
                    Spacer(modifier = Modifier.height(8.dp))
                    Button(onClick = onRequestPermissions) {
                        Text(stringResource(R.string.grant_permissions))
                    }
                }
            }
            return@Column
        }

        Card(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier.padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text("${stringResource(R.string.ble_status)}: $bleStatus")
                Text("${stringResource(R.string.connection_status)}: ${connectionState.name}")
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(
                onClick = { bleClient.startScan() },
                enabled = !hasActiveSession,
            ) { Text(stringResource(R.string.scan)) }
            Button(
                onClick = {
                    selectedAddress?.let { bleClient.connect(it) }
                },
                enabled = selectedAddress != null && connectionState != BleConnectionState.CONNECTING,
            ) { Text(stringResource(R.string.connect)) }
            Button(onClick = { bleClient.disconnect() }) {
                Text(stringResource(R.string.disconnect))
            }
        }

        Button(
            onClick = onStartLogging,
            enabled = !hasActiveSession,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(stringResource(R.string.start_logging))
        }

        Button(
            onClick = onOpenBatterySettings,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(stringResource(R.string.battery_optimization))
        }

        HorizontalDivider()
        Text(
            text = stringResource(R.string.found_devices),
            fontWeight = FontWeight.SemiBold,
        )

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
                        Text(
                            text = device.address,
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }
            }
        }
    }
}
