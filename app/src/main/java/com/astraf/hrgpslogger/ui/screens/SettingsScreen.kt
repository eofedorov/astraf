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
import com.astraf.hrgpslogger.LoggerSession
import com.astraf.hrgpslogger.R

@Composable
fun SettingsScreen(
    session: LoggerSession,
    showBatteryOptimizationButton: Boolean,
    onScan: () -> Unit,
    onOpenBatterySettings: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val bleClient = session.bleClient
    val connectionState by bleClient.connectionState.collectAsStateWithLifecycle()
    val bleStatus by bleClient.statusMessage.collectAsStateWithLifecycle()

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
            Column(
                modifier = Modifier.padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text("${stringResource(R.string.ble_status)}: $bleStatus")
                Text("${stringResource(R.string.connection_status)}: ${connectionState.name}")
            }
        }

        Button(
            onClick = onScan,
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
