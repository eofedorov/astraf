package com.astraf.hrgpslogger.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
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
import com.astraf.hrgpslogger.TrackRepository
import com.astraf.hrgpslogger.TrackSummary
import com.astraf.hrgpslogger.ui.formatDistanceMeters
import com.astraf.hrgpslogger.ui.formatDuration
import com.astraf.hrgpslogger.ui.formatTrackDateTime

@Composable
fun TracksScreen(
    session: LoggerSession,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val csvPath by session.csvLogger.currentFilePath.collectAsStateWithLifecycle()
    val recordingPhase by session.csvLogger.phase.collectAsStateWithLifecycle()

    var tracks by remember { mutableStateOf<List<TrackSummary>>(emptyList()) }

    LaunchedEffect(csvPath, recordingPhase) {
        val repository = TrackRepository(context)
        val activePath = if (recordingPhase != RecordingPhase.Idle) csvPath else null
        tracks = repository.listTracks(activeFilePath = activePath)
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
                TrackCard(track = track)
            }
        }
    }
}

@Composable
private fun TrackCard(track: TrackSummary) {
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
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = track.fileName,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
