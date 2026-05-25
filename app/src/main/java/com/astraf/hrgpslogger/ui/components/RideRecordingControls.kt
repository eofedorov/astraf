package com.astraf.hrgpslogger.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.astraf.hrgpslogger.R
import com.astraf.hrgpslogger.RecordingPhase
import com.astraf.hrgpslogger.ui.theme.RideAutoPauseAccent
import com.astraf.hrgpslogger.ui.theme.RideFloatingControlSurface
import com.astraf.hrgpslogger.ui.theme.RidePrimaryControl
import com.astraf.hrgpslogger.ui.theme.RideStopControl

private val SideButtonSize = 52.dp
private val CenterButtonSize = 64.dp

@Composable
fun RideRecordingControls(
    mapCollapsed: Boolean,
    onMapCollapsedChange: () -> Unit,
    recordingPhase: RecordingPhase,
    isAutoPaused: Boolean = false,
    onStartLogging: () -> Unit,
    onPauseLogging: () -> Unit,
    onResumeLogging: () -> Unit,
    onFinishClick: () -> Unit,
    exportDebugEnabled: Boolean = false,
    onExportDebug: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(80.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Surface(
            onClick = onMapCollapsedChange,
            shape = CircleShape,
            color = RideFloatingControlSurface,
            shadowElevation = 4.dp,
            modifier = Modifier.size(SideButtonSize),
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = if (mapCollapsed) Icons.Default.Map else Icons.Default.GridView,
                    contentDescription = stringResource(
                        if (mapCollapsed) R.string.map_expand else R.string.map_collapse,
                    ),
                    tint = RidePrimaryControl,
                )
            }
        }

        val sessionActive = recordingPhase != RecordingPhase.Idle

        if (sessionActive) {
            Surface(
                onClick = onExportDebug,
                enabled = exportDebugEnabled,
                shape = CircleShape,
                color = RideFloatingControlSurface,
                shadowElevation = 4.dp,
                modifier = Modifier.size(SideButtonSize),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Default.Share,
                        contentDescription = stringResource(R.string.cd_export_debug),
                        tint = if (exportDebugEnabled) {
                            RidePrimaryControl
                        } else {
                            RidePrimaryControl.copy(alpha = 0.38f)
                        },
                    )
                }
            }
        } else {
            Box(modifier = Modifier.size(SideButtonSize))
        }

        when (recordingPhase) {
            RecordingPhase.Idle -> {
                FloatingActionButton(
                    onClick = onStartLogging,
                    modifier = Modifier.size(CenterButtonSize),
                    containerColor = RidePrimaryControl,
                    contentColor = Color.White,
                ) {
                    Icon(
                        imageVector = Icons.Default.PlayArrow,
                        contentDescription = stringResource(R.string.cd_start_logging),
                    )
                }
            }
            RecordingPhase.WaitingForGps, RecordingPhase.Recording -> {
                FloatingActionButton(
                    onClick = onPauseLogging,
                    modifier = Modifier.size(CenterButtonSize),
                    containerColor = RidePrimaryControl,
                    contentColor = Color.White,
                ) {
                    Icon(
                        imageVector = Icons.Default.Pause,
                        contentDescription = stringResource(R.string.cd_pause_logging),
                    )
                }
            }
            RecordingPhase.Paused -> {
                val pauseAccentColor = if (isAutoPaused) RideAutoPauseAccent else Color.White
                FloatingActionButton(
                    onClick = onResumeLogging,
                    modifier = Modifier.size(CenterButtonSize),
                    containerColor = RidePrimaryControl,
                    contentColor = pauseAccentColor,
                ) {
                    Icon(
                        imageVector = Icons.Default.PlayArrow,
                        contentDescription = stringResource(R.string.cd_resume_logging),
                    )
                }
            }
        }

        val showStop = recordingPhase == RecordingPhase.Recording ||
            recordingPhase == RecordingPhase.WaitingForGps ||
            recordingPhase == RecordingPhase.Paused

        if (showStop) {
            Surface(
                onClick = onFinishClick,
                shape = CircleShape,
                color = RideFloatingControlSurface,
                shadowElevation = 4.dp,
                modifier = Modifier.size(SideButtonSize),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Default.Stop,
                        contentDescription = stringResource(R.string.cd_finish_logging),
                        tint = RideStopControl,
                    )
                }
            }
        } else {
            Box(modifier = Modifier.size(SideButtonSize))
        }
    }
}
