package com.astraf.hrgpslogger.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.astraf.hrgpslogger.LoggerSession
import com.astraf.hrgpslogger.R
import com.astraf.hrgpslogger.RecordingPhase
import com.astraf.hrgpslogger.TrackDetail
import com.astraf.hrgpslogger.TrackRepository
import com.astraf.hrgpslogger.TrackSummary
import com.astraf.hrgpslogger.strava.StravaIntegration
import com.astraf.hrgpslogger.strava.StravaUploadUiStatus
import com.astraf.hrgpslogger.ui.formatDistanceMeters
import com.astraf.hrgpslogger.ui.formatDuration
import com.astraf.hrgpslogger.ui.formatElevationClimbMeters
import com.astraf.hrgpslogger.ui.formatTrackDateTime
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun TracksScreen(
    session: LoggerSession,
    stravaIntegration: StravaIntegration,
    isTabSelected: Boolean,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val csvPath by session.csvLogger.currentFilePath.collectAsStateWithLifecycle()
    val recordingPhase by session.csvLogger.phase.collectAsStateWithLifecycle()
    val uploadStatus by stravaIntegration.uploadStatus.collectAsStateWithLifecycle()

    var tracks by remember { mutableStateOf<List<TrackSummary>>(emptyList()) }
    var selectedFileName by rememberSaveable { mutableStateOf<String?>(null) }
    var trackDetail by remember { mutableStateOf<TrackDetail?>(null) }
    var isDetailLoading by remember { mutableStateOf(false) }

    fun reloadTracks() {
        scope.launch {
            val repository = TrackRepository(context)
            val activePath = if (recordingPhase != RecordingPhase.Idle) csvPath else null
            tracks = withContext(Dispatchers.IO) {
                repository.listTracks(activeFilePath = activePath)
            }
        }
    }

    LaunchedEffect(csvPath, recordingPhase) {
        reloadTracks()
    }

    LaunchedEffect(selectedFileName, csvPath, recordingPhase) {
        val fileName = selectedFileName ?: run {
            trackDetail = null
            isDetailLoading = false
            return@LaunchedEffect
        }
        isDetailLoading = true
        trackDetail = withContext(Dispatchers.IO) {
            val activePath = if (recordingPhase != RecordingPhase.Idle) csvPath else null
            TrackRepository(context).loadTrackDetail(fileName, activePath)
        }
        isDetailLoading = false
    }

    if (selectedFileName != null) {
        TrackDetailScreen(
            detail = trackDetail,
            isLoading = isDetailLoading,
            isMapActive = isTabSelected,
            stravaUploadStatus = uploadStatus,
            onBack = { selectedFileName = null },
            onRename = { newName ->
                val fileName = selectedFileName ?: return@TrackDetailScreen
                scope.launch {
                    withContext(Dispatchers.IO) {
                        TrackRepository(context).renameTrackDisplayName(fileName, newName)
                    }
                    trackDetail = withContext(Dispatchers.IO) {
                        val activePath = if (recordingPhase != RecordingPhase.Idle) csvPath else null
                        TrackRepository(context).loadTrackDetail(fileName, activePath)
                    }
                    reloadTracks()
                }
            },
            onDelete = {
                val fileName = selectedFileName ?: return@TrackDetailScreen
                scope.launch {
                    withContext(Dispatchers.IO) {
                        TrackRepository(context).deleteTrack(fileName)
                    }
                    selectedFileName = null
                    trackDetail = null
                    reloadTracks()
                }
            },
            onUploadToStrava = {
                val fileName = selectedFileName ?: return@TrackDetailScreen
                scope.launch {
                    stravaIntegration.uploadTrack(fileName)
                }
            },
            modifier = modifier,
        )
        return
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            text = stringResource(R.string.tracks_screen_title),
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
        )

        uploadStatus?.let { status ->
            Text(
                text = formatUploadStatus(status),
                style = MaterialTheme.typography.bodySmall,
                color = when (status) {
                    is StravaUploadUiStatus.Failed -> MaterialTheme.colorScheme.error
                    is StravaUploadUiStatus.Success -> MaterialTheme.colorScheme.primary
                    else -> MaterialTheme.colorScheme.onSurfaceVariant
                },
            )
        }

        if (tracks.isEmpty()) {
            Text(
                text = stringResource(R.string.tracks_empty),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            return@Column
        }

        LazyColumn(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(tracks, key = { it.fileName }) { track ->
                TrackCard(
                    track = track,
                    onOpen = { selectedFileName = track.fileName },
                    onUploadToStrava = {
                        scope.launch {
                            stravaIntegration.uploadTrack(track.fileName)
                        }
                    },
                )
            }
        }
    }
}

@Composable
private fun TrackCard(
    track: TrackSummary,
    onOpen: () -> Unit,
    onUploadToStrava: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = if (track.isActive) {
            CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.primaryContainer,
            )
        } else {
            CardDefaults.cardColors()
        },
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onOpen),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
            Text(
                text = formatTrackDateTime(track.startedAtMillis),
                fontWeight = FontWeight.SemiBold,
            )
            if (track.isActive) {
                Text(
                    text = stringResource(R.string.track_recording_now),
                    color = MaterialTheme.colorScheme.primary,
                    style = MaterialTheme.typography.labelMedium,
                )
            }
            Text(
                text = stringResource(R.string.track_points, track.pointCount),
                style = MaterialTheme.typography.bodySmall,
            )
            track.durationMillis?.let { duration ->
                Text(
                    text = "${stringResource(R.string.metric_duration)}: ${formatDuration(duration)}",
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            track.distanceMeters?.let { distance ->
                Text(
                    text = "${stringResource(R.string.metric_distance)}: ${formatDistanceMeters(distance)}",
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            Text(
                text = "${stringResource(R.string.metric_elevation_gain)}: ${formatElevationClimbMeters(track.totalClimbMeters)}",
                style = MaterialTheme.typography.bodySmall,
            )
            }
            if (!track.isActive && track.pointCount > 0) {
                Spacer(modifier = Modifier.height(4.dp))
                Button(
                    onClick = onUploadToStrava,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(stringResource(R.string.strava_upload))
                }
            }
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = track.fileName,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun formatUploadStatus(status: StravaUploadUiStatus): String = when (status) {
    StravaUploadUiStatus.Exporting -> stringResource(R.string.strava_upload_exporting)
    StravaUploadUiStatus.Uploading -> stringResource(R.string.strava_upload_uploading)
    StravaUploadUiStatus.Processing -> stringResource(R.string.strava_upload_processing)
    is StravaUploadUiStatus.Success ->
        stringResource(R.string.strava_upload_success, status.activityId)
    is StravaUploadUiStatus.Failed ->
        stringResource(R.string.strava_upload_failed, status.message)
}
