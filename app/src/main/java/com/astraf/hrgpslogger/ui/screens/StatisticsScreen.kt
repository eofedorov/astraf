package com.astraf.hrgpslogger.ui.screens

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.astraf.hrgpslogger.LoggerSession
import com.astraf.hrgpslogger.RecordingPhase
import com.astraf.hrgpslogger.StatisticsCalculator
import com.astraf.hrgpslogger.StatsPeriod
import com.astraf.hrgpslogger.StatsRecord
import com.astraf.hrgpslogger.StatsRecordType
import com.astraf.hrgpslogger.StatsSnapshot
import com.astraf.hrgpslogger.TrackDetail
import com.astraf.hrgpslogger.TrackRepository
import com.astraf.hrgpslogger.TrackSummary
import com.astraf.hrgpslogger.R
import com.astraf.hrgpslogger.strava.StravaIntegration
import com.astraf.hrgpslogger.strava.StravaUploadUiStatus
import com.astraf.hrgpslogger.ui.components.ActivityHeatmap
import com.astraf.hrgpslogger.ui.components.ActivityMapView
import com.astraf.hrgpslogger.ui.components.StatsBarChart
import com.astraf.hrgpslogger.ui.components.StatsBestRideCard
import com.astraf.hrgpslogger.ui.components.StatsDistributionBars
import com.astraf.hrgpslogger.ui.components.StatsSummaryGrid
import com.astraf.hrgpslogger.ui.formatRideHumanDate
import com.astraf.hrgpslogger.ui.formatStatsDistance
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.ZoneId

private val StatsContentPadding = 12.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StatisticsScreen(
    session: LoggerSession,
    stravaIntegration: StravaIntegration,
    isTabSelected: Boolean,
    onGoToRide: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val statsLoadError = stringResource(R.string.stats_load_error)
    val scope = rememberCoroutineScope()
    val csvPath by session.csvLogger.currentFilePath.collectAsStateWithLifecycle()
    val recordingPhase by session.csvLogger.phase.collectAsStateWithLifecycle()
    val uploadStatus by stravaIntegration.uploadStatus.collectAsStateWithLifecycle()

    var allTracks by remember { mutableStateOf<List<TrackSummary>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var loadError by remember { mutableStateOf<String?>(null) }

    var selectedPeriod by rememberSaveable { mutableStateOf(StatsPeriod.Month) }
    val snapshot = remember(allTracks, selectedPeriod) {
        StatisticsCalculator.buildSnapshot(allTracks, selectedPeriod)
    }
    val hasAnyCompleted = remember(allTracks) {
        StatisticsCalculator.hasAnyEligible(allTracks)
    }

    var selectedFileName by rememberSaveable { mutableStateOf<String?>(null) }
    var trackDetail by remember { mutableStateOf<TrackDetail?>(null) }
    var isDetailLoading by remember { mutableStateOf(false) }

    var dayRidesDate by remember { mutableStateOf<LocalDate?>(null) }
    var mapSelectionFileNames by remember { mutableStateOf<List<String>?>(null) }

    fun reloadTracks() {
        scope.launch {
            isLoading = allTracks.isEmpty()
            loadError = null
            try {
                val activePath = if (recordingPhase != RecordingPhase.Idle) csvPath else null
                allTracks = withContext(Dispatchers.IO) {
                    TrackRepository(context).listTracks(activeFilePath = activePath)
                }
            } catch (e: Exception) {
                loadError = e.message ?: statsLoadError
            } finally {
                isLoading = false
            }
        }
    }

    LaunchedEffect(isTabSelected, csvPath, recordingPhase) {
        if (isTabSelected) {
            reloadTracks()
        }
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

    dayRidesDate?.let { date ->
        val dayTracks = StatisticsCalculator.tracksForDay(
            snapshot.tracksInPeriod,
            date,
            ZoneId.systemDefault(),
        )
        DayRidesDialog(
            dateLabel = formatRideHumanDate(
                date.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli(),
            ),
            tracks = dayTracks,
            onDismiss = { dayRidesDate = null },
            onTrackSelected = { fileName ->
                dayRidesDate = null
                selectedFileName = fileName
            },
        )
    }

    mapSelectionFileNames?.let { fileNames ->
        val tracks = snapshot.tracksInPeriod.filter { it.fileName in fileNames }
        MapTracksPickerDialog(
            tracks = tracks,
            onDismiss = { mapSelectionFileNames = null },
            onTrackSelected = { fileName ->
                mapSelectionFileNames = null
                selectedFileName = fileName
            },
        )
    }

    Column(
        modifier = modifier.fillMaxSize(),
    ) {
        Text(
            text = stringResource(R.string.stats_screen_title),
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(
                start = StatsContentPadding,
                top = StatsContentPadding,
                end = StatsContentPadding,
            ),
        )

        when {
            isLoading && allTracks.isEmpty() -> {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator()
                }
            }

            !hasAnyCompleted -> {
                StatsEmptyState(
                    onGoToRide = onGoToRide,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(StatsContentPadding),
                )
            }

            else -> {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(
                        start = StatsContentPadding,
                        end = StatsContentPadding,
                        bottom = StatsContentPadding,
                    ),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    item {
                        StatsPeriodSelector(
                            selected = selectedPeriod,
                            onSelected = { selectedPeriod = it },
                            modifier = Modifier.padding(top = 8.dp),
                        )
                    }

                    if (snapshot.tracksInPeriod.isEmpty()) {
                        item {
                            Text(
                                text = stringResource(R.string.stats_period_empty),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    } else {
                        item {
                            StatsSectionTitle(stringResource(R.string.stats_section_summary))
                            StatsSummaryGrid(summary = snapshot.summary)
                        }

                        item {
                            StatsSectionTitle(stringResource(R.string.stats_section_activity_chart))
                            StatsBarChart(
                                buckets = snapshot.activityBuckets,
                                xAxisCaption = when (selectedPeriod) {
                                    StatsPeriod.Week -> stringResource(R.string.stats_chart_axis_week)
                                    StatsPeriod.Month -> stringResource(R.string.stats_chart_axis_day_of_month)
                                    StatsPeriod.Year -> stringResource(R.string.stats_chart_axis_month)
                                    StatsPeriod.AllTime -> null
                                },
                            )
                        }

                        item {
                            StatsSectionTitle(stringResource(R.string.stats_section_heatmap))
                            ActivityHeatmap(
                                layout = snapshot.heatmapLayout,
                                period = selectedPeriod,
                            )
                        }

                        item {
                            StatsSectionTitle(stringResource(R.string.stats_section_records))
                            StatsRecordsList(
                                records = snapshot.records,
                                onRecordClick = { record ->
                                    when {
                                        record.dayDate != null -> dayRidesDate = record.dayDate
                                        record.trackFileName != null -> selectedFileName = record.trackFileName
                                    }
                                },
                            )
                        }

                        item {
                            StatsSectionTitle(stringResource(R.string.stats_section_distribution))
                            StatsDistributionBars(buckets = snapshot.distribution)
                        }

                        item {
                            StatsSectionTitle(stringResource(R.string.stats_section_activity_map))
                            ActivityMapView(
                                tracks = snapshot.mapTracks,
                                isMapActive = isTabSelected,
                                onTracksSelected = { fileNames ->
                                    val matches = snapshot.tracksInPeriod.filter {
                                        it.fileName in fileNames
                                    }
                                    when (matches.size) {
                                        1 -> selectedFileName = matches.first().fileName
                                        else -> mapSelectionFileNames = fileNames
                                    }
                                },
                            )
                        }

                        item {
                            StatsSectionTitle(stringResource(R.string.stats_section_best_rides))
                            StatsBestRidesSection(
                                snapshot = snapshot,
                                onTrackClick = { selectedFileName = it.fileName },
                            )
                        }
                    }
                }
            }
        }

        loadError?.let { error ->
            Text(
                text = error,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(StatsContentPadding),
            )
        }
    }
}

@Composable
private fun StatsPeriodSelector(
    selected: StatsPeriod,
    onSelected: (StatsPeriod) -> Unit,
    modifier: Modifier = Modifier,
) {
    val scrollState = rememberScrollState()
    Row(
        modifier = modifier
            .fillMaxWidth()
            .horizontalScroll(scrollState),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        StatsPeriod.entries.forEach { period ->
            FilterChip(
                selected = period == selected,
                onClick = { onSelected(period) },
                label = {
                    Text(
                        text = when (period) {
                            StatsPeriod.Week -> stringResource(R.string.stats_period_week)
                            StatsPeriod.Month -> stringResource(R.string.stats_period_month)
                            StatsPeriod.Year -> stringResource(R.string.stats_period_year)
                            StatsPeriod.AllTime -> stringResource(R.string.stats_period_all_time)
                        },
                    )
                },
            )
        }
    }
}

@Composable
private fun StatsSectionTitle(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier.padding(bottom = 8.dp),
    )
}

@Composable
private fun StatsRecordsList(
    records: List<StatsRecord>,
    onRecordClick: (StatsRecord) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        records.forEach { record ->
            StatsRecordCard(
                record = record,
                onClick = { onRecordClick(record) },
            )
        }
    }
}

@Composable
private fun StatsRecordCard(
    record: StatsRecord,
    onClick: () -> Unit,
) {
    androidx.compose.material3.Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        colors = androidx.compose.material3.CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        ),
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                text = recordTitle(record.type),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = record.valueLabel,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = record.dateLabel,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun recordTitle(type: StatsRecordType): String = when (type) {
    StatsRecordType.LongestRide -> stringResource(R.string.stats_record_longest_ride)
    StatsRecordType.BiggestClimb -> stringResource(R.string.stats_record_biggest_climb)
    StatsRecordType.HighestAvgSpeed -> stringResource(R.string.stats_record_highest_avg_speed)
    StatsRecordType.MaxSpeed -> stringResource(R.string.stats_record_max_speed)
    StatsRecordType.MostActiveDay -> stringResource(R.string.stats_record_most_active_day)
}

@Composable
private fun StatsBestRidesSection(
    snapshot: StatsSnapshot,
    onTrackClick: (TrackSummary) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        snapshot.bestRides.longest?.let { track ->
            StatsBestRideCard(
                title = stringResource(R.string.stats_best_longest),
                track = track,
                onClick = { onTrackClick(track) },
            )
        }
        snapshot.bestRides.fastest?.let { track ->
            StatsBestRideCard(
                title = stringResource(R.string.stats_best_fastest),
                track = track,
                onClick = { onTrackClick(track) },
            )
        }
        snapshot.bestRides.biggestClimb?.let { track ->
            StatsBestRideCard(
                title = stringResource(R.string.stats_best_climb),
                track = track,
                onClick = { onTrackClick(track) },
            )
        }
    }
}

@Composable
private fun StatsEmptyState(
    onGoToRide: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = stringResource(R.string.stats_empty_message),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Button(
            onClick = onGoToRide,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(stringResource(R.string.stats_empty_go_ride))
        }
    }
}

@Composable
private fun DayRidesDialog(
    dateLabel: String,
    tracks: List<TrackSummary>,
    onDismiss: () -> Unit,
    onTrackSelected: (String) -> Unit,
) {
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(dateLabel) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                tracks.forEach { track ->
                    val distance = formatStatsDistance(track.distanceMeters ?: 0.0)
                    TextButton(
                        onClick = { onTrackSelected(track.fileName) },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text("${distance.first} ${distance.second}")
                    }
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
private fun MapTracksPickerDialog(
    tracks: List<TrackSummary>,
    onDismiss: () -> Unit,
    onTrackSelected: (String) -> Unit,
) {
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.stats_map_pick_ride)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                tracks.forEach { track ->
                    val distance = formatStatsDistance(track.distanceMeters ?: 0.0)
                    TextButton(
                        onClick = { onTrackSelected(track.fileName) },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(
                            "${formatRideHumanDate(track.startedAtMillis)} · " +
                                "${distance.first} ${distance.second}",
                        )
                    }
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
