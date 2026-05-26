package com.astraf.hrgpslogger.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.astraf.hrgpslogger.GpxExporter
import com.astraf.hrgpslogger.GpxSharing
import com.astraf.hrgpslogger.LoggerSession
import com.astraf.hrgpslogger.R
import com.astraf.hrgpslogger.RecordingPhase
import com.astraf.hrgpslogger.TrackCsvParser
import com.astraf.hrgpslogger.TrackDetail
import com.astraf.hrgpslogger.TrackRepository
import com.astraf.hrgpslogger.TrackSummary
import com.astraf.hrgpslogger.strava.StravaIntegration
import com.astraf.hrgpslogger.strava.StravaUploadUiStatus
import com.astraf.hrgpslogger.ui.components.TrackCard
import com.astraf.hrgpslogger.ui.formatRideHumanDate
import com.astraf.hrgpslogger.ui.groupTracksByMonth
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/** Совпадает с отступом текста в [TrackCard], чтобы заголовки не прилипали к краю экрана. */
private val TracksHeaderHorizontalPadding = 12.dp

@Composable
fun TracksScreen(
    session: LoggerSession,
    stravaIntegration: StravaIntegration,
    isTabSelected: Boolean,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val gpxExportSuccess = stringResource(R.string.track_gpx_export_success)
    val gpxExportFailed = stringResource(R.string.track_gpx_export_failed)
    val tracksLoadError = stringResource(R.string.tracks_load_error)
    val scope = rememberCoroutineScope()
    val csvPath by session.csvLogger.currentFilePath.collectAsStateWithLifecycle()
    val recordingPhase by session.csvLogger.phase.collectAsStateWithLifecycle()
    val uploadStatus by stravaIntegration.uploadStatus.collectAsStateWithLifecycle()

    var tracks by remember { mutableStateOf<List<TrackSummary>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var loadError by remember { mutableStateOf<String?>(null) }
    var selectedFileName by rememberSaveable { mutableStateOf<String?>(null) }
    var trackDetail by remember { mutableStateOf<TrackDetail?>(null) }
    var isDetailLoading by remember { mutableStateOf(false) }
    var contextMenuTrack by remember { mutableStateOf<TrackSummary?>(null) }
    var deleteTarget by remember { mutableStateOf<TrackSummary?>(null) }
    var renameTarget by remember { mutableStateOf<TrackSummary?>(null) }
    var renameDraft by remember { mutableStateOf("") }
    var actionMessage by remember { mutableStateOf<String?>(null) }
    var pendingGpxExportFileName by remember { mutableStateOf<String?>(null) }

    val gpxExportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/gpx+xml"),
    ) { uri ->
        val fileName = pendingGpxExportFileName
        pendingGpxExportFileName = null
        if (uri == null || fileName == null) return@rememberLauncherForActivityResult
        scope.launch {
            val exported = withContext(Dispatchers.IO) {
                exportGpxToUri(context, fileName, uri)
            }
            actionMessage = if (exported) gpxExportSuccess else gpxExportFailed
        }
    }

    fun reloadTracks() {
        scope.launch {
            isLoading = tracks.isEmpty()
            loadError = null
            try {
                val repository = TrackRepository(context)
                val activePath = if (recordingPhase != RecordingPhase.Idle) csvPath else null
                tracks = withContext(Dispatchers.IO) {
                    repository.listTracks(activeFilePath = activePath)
                }
            } catch (e: Exception) {
                loadError = e.message ?: tracksLoadError
            } finally {
                isLoading = false
            }
        }
    }

    LaunchedEffect(csvPath, recordingPhase) {
        reloadTracks()
    }

    LaunchedEffect(uploadStatus) {
        if (uploadStatus is StravaUploadUiStatus.Success) {
            reloadTracks()
        }
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

    contextMenuTrack?.let { track ->
        TrackContextMenuDialog(
            track = track,
            onDismiss = { contextMenuTrack = null },
            onUploadToStrava = {
                contextMenuTrack = null
                scope.launch { stravaIntegration.uploadTrack(track.fileName) }
            },
            onOpenInStrava = { activityId ->
                contextMenuTrack = null
                val intent = Intent(Intent.ACTION_VIEW, stravaIntegration.buildActivityUri(activityId))
                context.startActivity(intent)
            },
            onExportGpx = {
                contextMenuTrack = null
                pendingGpxExportFileName = track.fileName
                gpxExportLauncher.launch(GpxExporter.fileNameForCsv(track.fileName))
            },
            onShare = {
                contextMenuTrack = null
                scope.launch {
                    shareGpx(context, track.fileName)
                }
            },
            onRename = {
                contextMenuTrack = null
                renameTarget = track
                renameDraft = track.displayName.orEmpty()
            },
            onDelete = {
                contextMenuTrack = null
                deleteTarget = track
            },
        )
    }

    deleteTarget?.let { track ->
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            title = { Text(stringResource(R.string.track_action_delete_confirm_title)) },
            text = { Text(stringResource(R.string.track_action_delete_confirm_message)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        val fileName = track.fileName
                        deleteTarget = null
                        scope.launch {
                            withContext(Dispatchers.IO) {
                                TrackRepository(context).deleteTrack(fileName)
                            }
                            reloadTracks()
                        }
                    },
                ) {
                    Text(stringResource(R.string.track_action_delete_confirm))
                }
            },
            dismissButton = {
                TextButton(onClick = { deleteTarget = null }) {
                    Text(stringResource(R.string.track_action_cancel))
                }
            },
        )
    }

    renameTarget?.let { track ->
        AlertDialog(
            onDismissRequest = { renameTarget = null },
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
                        val fileName = track.fileName
                        val newName = renameDraft.trim().ifEmpty { null }
                        renameTarget = null
                        scope.launch {
                            withContext(Dispatchers.IO) {
                                TrackRepository(context).renameTrackDisplayName(fileName, newName)
                            }
                            reloadTracks()
                        }
                    },
                ) {
                    Text(stringResource(R.string.track_rename_dialog_save))
                }
            },
            dismissButton = {
                TextButton(onClick = { renameTarget = null }) {
                    Text(stringResource(R.string.track_action_cancel))
                }
            },
        )
    }

    Column(
        modifier = modifier
            .fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(
            text = stringResource(R.string.tracks_screen_title),
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(start = TracksHeaderHorizontalPadding),
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

        actionMessage?.let { message ->
            Text(
                text = message,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        when {
            isLoading && tracks.isEmpty() -> {
                Box(
                    modifier = Modifier.fillMaxWidth().weight(1f),
                    contentAlignment = Alignment.Center,
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator()
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = stringResource(R.string.tracks_loading),
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                }
            }

            loadError != null && tracks.isEmpty() -> {
                Text(
                    text = loadError ?: stringResource(R.string.tracks_load_error),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error,
                )
            }

            tracks.none { !it.isActive } -> {
                Text(
                    text = stringResource(R.string.tracks_empty),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            else -> {
                val completedTracks = remember(tracks) { tracks.filter { !it.isActive } }
                val sections = remember(completedTracks) { groupTracksByMonth(completedTracks) }
                LazyColumn(
                    modifier = Modifier.fillMaxWidth().weight(1f),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    sections.forEach { section ->
                        item(key = "header-${section.title}") {
                            Text(
                                text = section.title,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.padding(
                                    start = TracksHeaderHorizontalPadding,
                                    top = 4.dp,
                                    bottom = 4.dp,
                                ),
                            )
                        }
                        items(section.tracks, key = { it.fileName }) { track ->
                            TrackCard(
                                track = track,
                                onOpen = { selectedFileName = track.fileName },
                                onLongPress = { contextMenuTrack = track },
                                onMenuClick = { contextMenuTrack = track },
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TrackContextMenuDialog(
    track: TrackSummary,
    onDismiss: () -> Unit,
    onUploadToStrava: () -> Unit,
    onOpenInStrava: (Long) -> Unit,
    onExportGpx: () -> Unit,
    onShare: () -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(formatRideHumanDate(track.startedAtMillis)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                track.stravaActivityId?.let { activityId ->
                    TextButton(
                        onClick = { onOpenInStrava(activityId) },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(stringResource(R.string.strava_open_activity))
                    }
                } ?: run {
                    if (!track.isActive && track.hasGpsData) {
                        TextButton(
                            onClick = onUploadToStrava,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text(stringResource(R.string.strava_upload))
                        }
                    }
                }
                TextButton(
                    onClick = onExportGpx,
                    enabled = track.hasGpsData,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(stringResource(R.string.track_action_export_gpx))
                }
                TextButton(
                    onClick = onShare,
                    enabled = track.hasGpsData,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(stringResource(R.string.track_menu_share))
                }
                TextButton(
                    onClick = onRename,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(stringResource(R.string.track_action_rename))
                }
                TextButton(
                    onClick = onDelete,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        text = stringResource(R.string.track_action_delete),
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.track_action_cancel))
            }
        },
    )
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

private suspend fun shareGpx(context: android.content.Context, fileName: String) {
    withContext(Dispatchers.IO) {
        val file = File(context.filesDir, fileName)
        if (!file.exists()) return@withContext
        val samples = runCatching { TrackCsvParser.parseSamples(file) }.getOrNull() ?: return@withContext
        if (samples.isEmpty()) return@withContext
        val gpxFile = GpxSharing.exportCacheFile(context, fileName)
        GpxExporter.exportToFile(samples, gpxFile)
        val shareIntent = GpxSharing.buildShareIntent(
            context = context,
            gpxFile = gpxFile,
            subject = context.getString(R.string.track_gpx_share_subject, fileName),
        )
        withContext(Dispatchers.Main) {
            context.startActivity(
                Intent.createChooser(shareIntent, context.getString(R.string.track_gpx_share_chooser)),
            )
        }
    }
}

private fun exportGpxToUri(
    context: android.content.Context,
    fileName: String,
    uri: Uri,
): Boolean {
    val file = File(context.filesDir, fileName)
    if (!file.exists()) return false
    val samples = runCatching { TrackCsvParser.parseSamples(file) }.getOrNull() ?: return false
    if (samples.isEmpty()) return false
    return runCatching {
        val gpxFile = GpxSharing.exportCacheFile(context, fileName)
        GpxExporter.exportToFile(samples, gpxFile)
        context.contentResolver.openOutputStream(uri)?.use { output ->
            gpxFile.inputStream().use { input -> input.copyTo(output) }
        } ?: return false
        true
    }.getOrDefault(false)
}
