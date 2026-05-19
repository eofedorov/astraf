package com.astraf.hrgpslogger.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.astraf.hrgpslogger.LoggerSession
import com.astraf.hrgpslogger.LoggingForegroundService
import com.astraf.hrgpslogger.R
import com.astraf.hrgpslogger.RecordingPhase
import com.astraf.hrgpslogger.ui.components.MetricTile
import com.astraf.hrgpslogger.ui.components.RideMapView
import com.astraf.hrgpslogger.ui.formatCurrentTime
import com.astraf.hrgpslogger.ui.formatDistanceMeters
import com.astraf.hrgpslogger.ui.formatDuration
import com.astraf.hrgpslogger.ui.formatSpeedKmh
import kotlinx.coroutines.delay

private data class RideMetric(
    val title: String,
    val value: String,
)

@Composable
fun RideScreen(
    session: LoggerSession,
    recordingPhase: RecordingPhase,
    loggingPersisted: Boolean,
    isMapActive: Boolean,
    onStartLogging: () -> Unit,
    onPauseLogging: () -> Unit,
    onResumeLogging: () -> Unit,
    onFinishLogging: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val heartRate by session.bleClient.heartRateBpm.collectAsStateWithLifecycle()
    val tripStats by session.tripStatsTracker.stats.collectAsStateWithLifecycle()
    val isBackgroundServiceRunning = LoggingForegroundService.isRunning

    var currentTime by remember { mutableStateOf(formatCurrentTime()) }
    val isRecording = recordingPhase == RecordingPhase.Recording
    LaunchedEffect(isRecording) {
        while (isRecording) {
            currentTime = formatCurrentTime()
            delay(1_000)
        }
        currentTime = formatCurrentTime()
    }

    var showFinishDialog by remember { mutableStateOf(false) }

    if (showFinishDialog) {
        AlertDialog(
            onDismissRequest = { showFinishDialog = false },
            title = { Text(stringResource(R.string.finish_dialog_title)) },
            text = { Text(stringResource(R.string.finish_dialog_message)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        showFinishDialog = false
                        onFinishLogging()
                    },
                ) {
                    Text(stringResource(R.string.finish_dialog_confirm))
                }
            },
            dismissButton = {
                TextButton(onClick = { showFinishDialog = false }) {
                    Text(stringResource(R.string.finish_dialog_cancel))
                }
            },
        )
    }

    val metrics = listOf(
        RideMetric(
            title = stringResource(R.string.metric_duration),
            value = formatDuration(tripStats.durationMillis),
        ),
        RideMetric(
            title = stringResource(R.string.metric_heart_rate),
            value = heartRate?.let { "$it ${stringResource(R.string.bpm_unit)}" } ?: "—",
        ),
        RideMetric(
            title = stringResource(R.string.metric_current_speed),
            value = formatSpeedKmh(tripStats.currentSpeedKmh),
        ),
        RideMetric(
            title = stringResource(R.string.metric_average_speed),
            value = formatSpeedKmh(tripStats.averageSpeedKmh),
        ),
        RideMetric(
            title = stringResource(R.string.metric_current_time),
            value = currentTime,
        ),
        RideMetric(
            title = stringResource(R.string.metric_max_speed),
            value = formatSpeedKmh(tripStats.maxSpeedKmh.takeIf { it > 0f }),
        ),
        RideMetric(
            title = stringResource(R.string.metric_distance),
            value = formatDistanceMeters(tripStats.distanceMeters),
        ),
    )

    Column(modifier = modifier.fillMaxSize()) {
        RideMapView(
            location = session.locationTracker.location,
            trackPoints = session.activeTrackStore.points,
            isMapActive = isMapActive,
            modifier = Modifier
                .fillMaxWidth()
                .weight(2f),
        )

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = stringResource(R.string.ride_screen_title),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )

            when (recordingPhase) {
                RecordingPhase.Idle -> {
                    Text(
                        text = stringResource(R.string.ride_not_active),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                RecordingPhase.Paused -> {
                    Text(
                        text = stringResource(R.string.recording_paused),
                        color = MaterialTheme.colorScheme.primary,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                RecordingPhase.Recording -> {
                    if (isBackgroundServiceRunning || loggingPersisted) {
                        Text(
                            text = if (isBackgroundServiceRunning) {
                                stringResource(R.string.background_active)
                            } else {
                                stringResource(R.string.background_recovering)
                            },
                            color = MaterialTheme.colorScheme.primary,
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }
            }

            LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(bottom = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                items(metrics, key = { it.title }) { metric ->
                    MetricTile(
                        title = metric.title,
                        value = metric.value,
                    )
                }
            }

            when (recordingPhase) {
                RecordingPhase.Idle -> {
                    Button(
                        onClick = onStartLogging,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(stringResource(R.string.start_logging))
                    }
                }
                RecordingPhase.Recording -> {
                    Button(
                        onClick = onPauseLogging,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(stringResource(R.string.pause_logging))
                    }
                }
                RecordingPhase.Paused -> {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Button(
                            onClick = onResumeLogging,
                            modifier = Modifier.weight(1f),
                        ) {
                            Text(stringResource(R.string.resume_logging))
                        }
                        OutlinedButton(
                            onClick = { showFinishDialog = true },
                            modifier = Modifier.weight(1f),
                        ) {
                            Text(stringResource(R.string.finish_logging))
                        }
                    }
                }
            }
        }
    }
}
