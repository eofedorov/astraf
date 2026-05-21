package com.astraf.hrgpslogger.ui.screens

import android.content.Intent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import com.astraf.hrgpslogger.GpxExporter
import com.astraf.hrgpslogger.GpxSharing
import com.astraf.hrgpslogger.R
import com.astraf.hrgpslogger.TrackDetail
import com.astraf.hrgpslogger.TrackDetailStatus
import com.astraf.hrgpslogger.strava.StravaIntegration
import com.astraf.hrgpslogger.strava.StravaUploadUiStatus
import com.astraf.hrgpslogger.ui.components.MetricTile
import com.astraf.hrgpslogger.ui.components.StaticTrackMapView
import com.astraf.hrgpslogger.ui.components.TrackChartYAxis
import com.astraf.hrgpslogger.ui.components.TrackTimeSeriesChart
import com.astraf.hrgpslogger.ui.formatDistanceMeters
import com.astraf.hrgpslogger.ui.formatDuration
import com.astraf.hrgpslogger.ui.formatElevationClimbMeters
import com.astraf.hrgpslogger.ui.formatHeartRateBpm
import com.astraf.hrgpslogger.ui.formatSpeedKmh
import com.astraf.hrgpslogger.ui.formatTrackDateTime
import com.astraf.hrgpslogger.ui.formatTrackHeaderDateTime
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TrackDetailScreen(
    detail: TrackDetail?,
    isLoading: Boolean,
    isMapActive: Boolean,
    stravaUploadStatus: StravaUploadUiStatus?,
    onBack: () -> Unit,
    onRename: (String?) -> Unit,
    onDelete: () -> Unit,
    onUploadToStrava: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    var showDeleteDialog by remember { mutableStateOf(false) }
    var showRenameDialog by remember { mutableStateOf(false) }
    var renameDraft by remember(detail?.fileName) {
        mutableStateOf(detail?.displayName.orEmpty())
    }
    var actionMessage by remember { mutableStateOf<String?>(null) }

    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text(stringResource(R.string.track_action_delete_confirm_title)) },
            text = { Text(stringResource(R.string.track_action_delete_confirm_message)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDeleteDialog = false
                        onDelete()
                    },
                ) {
                    Text(stringResource(R.string.track_action_delete_confirm))
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) {
                    Text(stringResource(R.string.track_action_cancel))
                }
            },
        )
    }

    if (showRenameDialog && detail != null) {
        AlertDialog(
            onDismissRequest = { showRenameDialog = false },
            title = { Text(stringResource(R.string.track_rename_dialog_title)) },
            text = {
                OutlinedTextField(
                    value = renameDraft,
                    onValueChange = { renameDraft = it },
                    label = { Text(stringResource(R.string.track_rename_dialog_hint)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                    modifier = Modifier.fillMaxWidth(),
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showRenameDialog = false
                        onRename(renameDraft.trim().ifEmpty { null })
                    },
                ) {
                    Text(stringResource(R.string.track_rename_dialog_save))
                }
            },
            dismissButton = {
                TextButton(onClick = { showRenameDialog = false }) {
                    Text(stringResource(R.string.track_action_cancel))
                }
            },
        )
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            TopAppBar(
                windowInsets = WindowInsets(0, 0, 0, 0),
                title = {
                    Text(
                        text = detail?.displayName
                            ?: detail?.startedAtMillis?.let { formatTrackHeaderDateTime(it) }
                            ?: detail?.fileName
                            ?: stringResource(R.string.tracks_screen_title),
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.track_detail_back),
                        )
                    }
                },
            )
        },
    ) { padding ->
        when {
            isLoading -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator()
                }
            }
            detail == null -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(stringResource(R.string.track_status_file_not_found))
                }
            }
            else -> {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding),
                    contentPadding = PaddingValues(
                        start = 16.dp,
                        end = 16.dp,
                        top = 8.dp,
                        bottom = 16.dp,
                    ),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    if (detail.status != TrackDetailStatus.Completed) {
                        item {
                            TrackHeaderSection(detail = detail)
                        }
                    }
                    item {
                        TrackMapSection(detail = detail, isMapActive = isMapActive)
                    }
                    item {
                        TrackMetricsSection(detail = detail)
                    }
                    item {
                        TrackChartsSection(detail = detail)
                    }
                    item {
                        TrackRecordingInfoSection(detail = detail)
                    }
                    item {
                        TrackActionsSection(
                            detail = detail,
                            stravaUploadStatus = stravaUploadStatus,
                            actionMessage = actionMessage,
                            onExportGpx = {
                                if (detail.samples.isEmpty()) {
                                    actionMessage = context.getString(R.string.track_gpx_export_failed)
                                    return@TrackActionsSection
                                }
                                runCatching {
                                    val gpxFile = GpxSharing.exportCacheFile(context, detail.fileName)
                                    GpxExporter.exportToFile(detail.samples, gpxFile)
                                    val shareIntent = GpxSharing.buildShareIntent(
                                        context = context,
                                        gpxFile = gpxFile,
                                        subject = context.getString(
                                            R.string.track_gpx_share_subject,
                                            detail.displayName ?: detail.fileName,
                                        ),
                                    )
                                    context.startActivity(
                                        Intent.createChooser(
                                            shareIntent,
                                            context.getString(R.string.track_gpx_share_chooser),
                                        ),
                                    )
                                    actionMessage = null
                                }.onFailure {
                                    actionMessage = context.getString(R.string.track_gpx_export_failed)
                                }
                            },
                            onRename = {
                                renameDraft = detail.displayName.orEmpty()
                                showRenameDialog = true
                            },
                            onUploadToStrava = onUploadToStrava,
                            onDelete = { showDeleteDialog = true },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun TrackHeaderSection(detail: TrackDetail) {
    Text(
        text = buildHeaderStatusLine(detail),
        style = MaterialTheme.typography.bodyLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun buildHeaderStatusLine(detail: TrackDetail): String = when (detail.status) {
    TrackDetailStatus.RecordingActive -> stringResource(R.string.track_status_recording)
    TrackDetailStatus.InsufficientData -> stringResource(R.string.track_status_insufficient_data)
    TrackDetailStatus.FileNotFound -> stringResource(R.string.track_status_file_not_found)
    TrackDetailStatus.Unreadable -> stringResource(R.string.track_status_unreadable)
    TrackDetailStatus.Completed -> ""
}

@Composable
private fun TrackMapSection(detail: TrackDetail, isMapActive: Boolean) {
    if (!detail.hasGpsData) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            ),
        ) {
            Text(
                text = stringResource(R.string.track_map_no_gps),
                modifier = Modifier.padding(24.dp),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        return
    }
    StaticTrackMapView(
        segments = detail.segments,
        points = detail.samples.map { it.point },
        isMapActive = isMapActive,
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun TrackMetricsSection(detail: TrackDetail) {
    val dash = stringResource(R.string.value_dash)
    val metrics = listOf(
        stringResource(R.string.metric_distance) to (
            detail.distanceMeters?.let { formatDistanceMeters(it) } ?: dash
            ),
        stringResource(R.string.metric_duration) to (
            detail.durationMillis?.let { formatDuration(it) } ?: dash
            ),
        stringResource(R.string.track_metric_avg_speed) to formatSpeedKmh(detail.averageSpeedKmh),
        stringResource(R.string.track_metric_max_speed) to formatSpeedKmh(detail.maxSpeedKmh),
        stringResource(R.string.track_metric_avg_hr) to formatHeartRateBpm(detail.averageHeartRateBpm),
        stringResource(R.string.track_metric_max_hr) to formatHeartRateBpm(detail.maxHeartRateBpm),
        stringResource(R.string.metric_elevation_gain) to formatElevationClimbMeters(detail.totalClimbMeters),
        stringResource(R.string.track_metric_points) to detail.pointCount.toString(),
    )

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        metrics.chunked(2).forEach { row ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                row.forEach { (label, value) ->
                    MetricTile(
                        title = label,
                        value = value,
                        modifier = Modifier.weight(1f),
                    )
                }
                if (row.size == 1) {
                    Box(modifier = Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun TrackChartsSection(detail: TrackDetail) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        ChartBlock(
            title = stringResource(R.string.track_chart_speed_title),
            placeholder = stringResource(R.string.track_chart_speed_insufficient),
            series = detail.speedSeries,
            yAxis = TrackChartYAxis.Speed,
        )
        ChartBlock(
            title = stringResource(R.string.track_chart_hr_title),
            placeholder = stringResource(R.string.track_chart_hr_missing),
            series = detail.heartRateSeries,
            yAxis = TrackChartYAxis.HeartRate,
        )
        ChartBlock(
            title = stringResource(R.string.track_chart_elevation_title),
            placeholder = stringResource(R.string.track_chart_elevation_missing),
            series = detail.elevationSeries,
            yAxis = TrackChartYAxis.Elevation,
        )
    }
}

@Composable
private fun ChartBlock(
    title: String,
    placeholder: String,
    series: List<com.astraf.hrgpslogger.TrackChartPoint>,
    yAxis: TrackChartYAxis,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        ),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            if (series.size < 2) {
                Text(
                    text = placeholder,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                TrackTimeSeriesChart(series = series, yAxis = yAxis)
            }
        }
    }
}

@Composable
private fun TrackRecordingInfoSection(detail: TrackDetail) {
    val dash = stringResource(R.string.value_dash)
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer,
        ),
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(
                text = stringResource(R.string.track_info_section_title),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = stringResource(R.string.track_info_file, detail.fileName),
                style = MaterialTheme.typography.bodySmall,
            )
            Text(
                text = stringResource(
                    R.string.track_info_created,
                    formatTrackDateTime(detail.createdAtMillis),
                ),
                style = MaterialTheme.typography.bodySmall,
            )
            Text(
                text = stringResource(
                    R.string.track_info_started,
                    detail.startedAtMillis?.let { formatTrackDateTime(it) } ?: dash,
                ),
                style = MaterialTheme.typography.bodySmall,
            )
            Text(
                text = stringResource(
                    R.string.track_info_ended,
                    detail.endedAtMillis?.let { formatTrackDateTime(it) } ?: dash,
                ),
                style = MaterialTheme.typography.bodySmall,
            )
            Text(
                text = stringResource(R.string.track_info_gps_points, detail.pointCount),
                style = MaterialTheme.typography.bodySmall,
            )
            Text(
                text = stringResource(
                    if (detail.hasGpsData) R.string.track_info_gps_yes else R.string.track_info_gps_no,
                ),
                style = MaterialTheme.typography.bodySmall,
            )
            Text(
                text = stringResource(
                    if (detail.hasHeartRateData) R.string.track_info_hr_yes else R.string.track_info_hr_no,
                ),
                style = MaterialTheme.typography.bodySmall,
            )
            Text(
                text = stringResource(
                    if (detail.hasAltitudeData) R.string.track_info_elevation_yes
                    else R.string.track_info_elevation_no,
                ),
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}

@Composable
private fun TrackActionsSection(
    detail: TrackDetail,
    stravaUploadStatus: StravaUploadUiStatus?,
    actionMessage: String?,
    onExportGpx: () -> Unit,
    onRename: () -> Unit,
    onUploadToStrava: () -> Unit,
    onDelete: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(
            text = stringResource(R.string.track_actions_section_title),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
        )

        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(
                onClick = onExportGpx,
                enabled = detail.samples.isNotEmpty(),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(stringResource(R.string.track_action_export_gpx))
            }
            OutlinedButton(
                onClick = onRename,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(stringResource(R.string.track_action_rename))
            }
            if (!detail.isActive && detail.samples.isNotEmpty()) {
                Button(
                    onClick = onUploadToStrava,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(stringResource(R.string.strava_upload))
                }
            }
        }

        stravaUploadStatus?.let { status ->
            Text(
                text = formatStravaUploadStatus(status),
                style = MaterialTheme.typography.bodySmall,
                color = when (status) {
                    is StravaUploadUiStatus.Failed -> MaterialTheme.colorScheme.error
                    is StravaUploadUiStatus.Success -> MaterialTheme.colorScheme.primary
                    else -> MaterialTheme.colorScheme.onSurfaceVariant
                },
            )
        }

        actionMessage?.let { message ->
            Text(
                text = message,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
            )
        }

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.35f),
            ),
        ) {
            OutlinedButton(
                onClick = onDelete,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp),
            ) {
                Text(
                    text = stringResource(R.string.track_action_delete),
                    color = MaterialTheme.colorScheme.error,
                )
            }
        }
    }
}

@Composable
private fun formatStravaUploadStatus(status: StravaUploadUiStatus): String = when (status) {
    StravaUploadUiStatus.Exporting -> stringResource(R.string.strava_upload_exporting)
    StravaUploadUiStatus.Uploading -> stringResource(R.string.strava_upload_uploading)
    StravaUploadUiStatus.Processing -> stringResource(R.string.strava_upload_processing)
    is StravaUploadUiStatus.Success ->
        stringResource(R.string.strava_upload_success, status.activityId)
    is StravaUploadUiStatus.Failed ->
        stringResource(R.string.strava_upload_failed, status.message)
}
