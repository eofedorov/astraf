package com.astraf.hrgpslogger.ui.screens

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.astraf.hrgpslogger.LoggerSession
import com.astraf.hrgpslogger.R
import com.astraf.hrgpslogger.RecordingPhase
import com.astraf.hrgpslogger.ui.components.RideMapView
import com.astraf.hrgpslogger.ui.components.RideMetricCell
import com.astraf.hrgpslogger.ui.components.RideMetricsFullscreenGrid
import com.astraf.hrgpslogger.ui.components.RideRecordingControls
import com.astraf.hrgpslogger.ui.components.RideTopMetricCards
import com.astraf.hrgpslogger.ui.formatCurrentTime
import com.astraf.hrgpslogger.ui.formatDistanceNumber
import com.astraf.hrgpslogger.ui.formatDistanceUnit
import com.astraf.hrgpslogger.ui.formatDuration
import com.astraf.hrgpslogger.ui.formatElevationClimbNumber
import com.astraf.hrgpslogger.ui.formatElevationClimbUnit
import com.astraf.hrgpslogger.ui.formatSpeedKmhNumber
import kotlinx.coroutines.delay

private val RideControlsHeight = 80.dp
private val RideControlsBottomPadding = 8.dp
private val MapRecenterBottomExtra = 12.dp

@Composable
fun RideScreen(
    session: LoggerSession,
    recordingPhase: RecordingPhase,
    isMapActive: Boolean,
    onStartLogging: () -> Unit,
    onPauseLogging: () -> Unit,
    onResumeLogging: () -> Unit,
    onFinishLogging: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val heartRate by session.bleClient.heartRateBpm.collectAsStateWithLifecycle()
    val tripStats by session.tripStatsTracker.stats.collectAsStateWithLifecycle()

    var mapCollapsed by rememberSaveable { mutableStateOf(false) }
    val mapVisible = isMapActive && !mapCollapsed

    var currentTime by remember { mutableStateOf(formatCurrentTime()) }
    LaunchedEffect(Unit) {
        while (true) {
            currentTime = formatCurrentTime()
            delay(1_000)
        }
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

    val bpmUnit = stringResource(R.string.bpm_unit)
    val speedUnit = stringResource(R.string.speed_kmh_unit)

    val fullscreenCells = listOf(
        RideMetricCell(
            label = stringResource(R.string.metric_label_duration),
            number = formatDuration(tripStats.durationMillis),
        ),
        RideMetricCell(
            label = stringResource(R.string.metric_label_heart_rate),
            number = heartRate?.toString() ?: "—",
            unit = heartRate?.let { bpmUnit },
        ),
        RideMetricCell(
            label = stringResource(R.string.metric_label_current_speed),
            number = formatSpeedKmhNumber(tripStats.currentSpeedKmh),
            unit = tripStats.currentSpeedKmh?.let { speedUnit },
        ),
        RideMetricCell(
            label = stringResource(R.string.metric_label_average_speed),
            number = formatSpeedKmhNumber(tripStats.averageSpeedKmh),
            unit = tripStats.averageSpeedKmh?.let { speedUnit },
        ),
        RideMetricCell(
            label = stringResource(R.string.metric_label_max_speed),
            number = formatSpeedKmhNumber(tripStats.maxSpeedKmh.takeIf { it > 0f }),
            unit = tripStats.maxSpeedKmh.takeIf { it > 0f }?.let { speedUnit },
        ),
        RideMetricCell(
            label = stringResource(R.string.metric_label_distance),
            number = formatDistanceNumber(tripStats.distanceMeters),
            unit = formatDistanceUnit(tripStats.distanceMeters),
        ),
        RideMetricCell(
            label = stringResource(R.string.metric_label_elevation_gain),
            number = formatElevationClimbNumber(tripStats.totalClimbMeters),
            unit = formatElevationClimbUnit(tripStats.totalClimbMeters),
        ),
        RideMetricCell(
            label = stringResource(R.string.metric_label_current_time),
            number = currentTime,
        ),
    )

    val mapRecenterBottomPadding = RideControlsHeight + RideControlsBottomPadding + MapRecenterBottomExtra

    Box(modifier = modifier.fillMaxSize()) {
        when {
            mapCollapsed -> {
                RideMetricsFullscreenGrid(
                    cells = fullscreenCells,
                    modifier = Modifier.fillMaxSize(),
                )
            }
            mapVisible -> {
                RideMapView(
                    location = session.locationTracker.location,
                    acceptedPoint = session.gpsRideController.acceptedPoint,
                    trackSegments = session.activeTrackStore.segments,
                    isMapActive = true,
                    controlsBottomPadding = mapRecenterBottomPadding,
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }

        if (!mapCollapsed) {
            RideTopMetricCards(
                speedValue = formatSpeedKmhNumber(tripStats.currentSpeedKmh),
                speedUnit = tripStats.currentSpeedKmh?.let { speedUnit },
                heartRateValue = heartRate?.toString() ?: "—",
                heartRateUnit = heartRate?.let { bpmUnit },
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .fillMaxWidth()
                    .windowInsetsPadding(WindowInsets.safeDrawing)
                    .padding(horizontal = 12.dp, vertical = 12.dp),
            )
        }

        RideRecordingControls(
            mapCollapsed = mapCollapsed,
            onMapCollapsedChange = { mapCollapsed = !mapCollapsed },
            recordingPhase = recordingPhase,
            onStartLogging = onStartLogging,
            onPauseLogging = onPauseLogging,
            onResumeLogging = onResumeLogging,
            onFinishClick = { showFinishDialog = true },
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = RideControlsBottomPadding),
        )
    }
}
