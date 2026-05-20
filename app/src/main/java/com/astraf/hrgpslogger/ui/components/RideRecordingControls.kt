package com.astraf.hrgpslogger.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.astraf.hrgpslogger.R
import com.astraf.hrgpslogger.RecordingPhase

@Composable
fun RideRecordingControls(
    mapCollapsed: Boolean,
    onMapCollapsedChange: () -> Unit,
    recordingPhase: RecordingPhase,
    onStartLogging: () -> Unit,
    onPauseLogging: () -> Unit,
    onResumeLogging: () -> Unit,
    onFinishClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(48.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        FilledTonalIconButton(
            onClick = onMapCollapsedChange,
            modifier = Modifier.size(48.dp),
        ) {
            Icon(
                imageVector = if (mapCollapsed) Icons.Default.Map else Icons.Default.GridView,
                contentDescription = stringResource(
                    if (mapCollapsed) R.string.map_expand else R.string.map_collapse,
                ),
            )
        }

        Box(
            modifier = Modifier.weight(1f),
            contentAlignment = Alignment.Center,
        ) {
            when (recordingPhase) {
                RecordingPhase.Idle -> {
                    FilledIconButton(onClick = onStartLogging) {
                        Icon(
                            imageVector = Icons.Default.PlayArrow,
                            contentDescription = stringResource(R.string.cd_start_logging),
                        )
                    }
                }
                RecordingPhase.Recording -> {
                    FilledIconButton(onClick = onPauseLogging) {
                        Icon(
                            imageVector = Icons.Default.Pause,
                            contentDescription = stringResource(R.string.cd_pause_logging),
                        )
                    }
                }
                RecordingPhase.Paused -> {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        FilledIconButton(onClick = onResumeLogging) {
                            Icon(
                                imageVector = Icons.Default.PlayArrow,
                                contentDescription = stringResource(R.string.cd_resume_logging),
                            )
                        }
                        IconButton(onClick = onFinishClick) {
                            Icon(
                                imageVector = Icons.Default.Stop,
                                contentDescription = stringResource(R.string.cd_finish_logging),
                            )
                        }
                    }
                }
            }
        }

        // Балансировка, чтобы play/pause оставался по центру экрана.
        Box(modifier = Modifier.size(48.dp))
    }
}
