package com.astraf.hrgpslogger.ui.components

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.MonitorHeart
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.Speed
import androidx.compose.material.icons.outlined.Terrain
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.astraf.hrgpslogger.R
import com.astraf.hrgpslogger.TrackSummary
import com.astraf.hrgpslogger.ui.formatDistanceNumber
import com.astraf.hrgpslogger.ui.formatDistanceUnit
import com.astraf.hrgpslogger.ui.formatDuration
import com.astraf.hrgpslogger.ui.formatHeartRateBpm
import com.astraf.hrgpslogger.ui.formatListElevationMeters
import com.astraf.hrgpslogger.ui.formatRideCardHeader
import com.astraf.hrgpslogger.ui.formatSpeedKmh
import com.astraf.hrgpslogger.ui.formatSpeedKmhNumber

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun TrackCard(
    track: TrackSummary,
    onOpen: () -> Unit,
    onLongPress: () -> Unit,
    onMenuClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val dash = stringResource(R.string.value_dash)
    val accent = MaterialTheme.colorScheme.primary

    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface,
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 3.dp),
        shape = RoundedCornerShape(12.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(IntrinsicSize.Max),
        ) {
            Column(
                modifier = Modifier
                    .weight(1.3f)
                    .padding(start = 12.dp, top = 12.dp, bottom = 12.dp, end = 0.dp),
               verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Row(
                        modifier = Modifier
                            .weight(1f)
                            .combinedClickable(
                                onClick = onOpen,
                                onLongClick = onLongPress,
                            ),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.CalendarMonth,
                            contentDescription = null,
                            tint = accent,
                            modifier = Modifier.size(20.dp),
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = formatRideCardHeader(track.startedAtMillis),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                    IconButton(
                        onClick = onMenuClick,
                        modifier = Modifier.size(32.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.MoreVert,
                            contentDescription = stringResource(R.string.track_card_menu),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }

                Column(
                    modifier = Modifier.combinedClickable(
                        onClick = onOpen,
                        onLongClick = onLongPress,
                    ),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        val distanceText = if (track.distanceMeters != null) {
                            buildAnnotatedString {
                                withStyle(style = SpanStyle(fontSize = 32.sp, fontWeight = FontWeight.Bold)) {
                                    append(formatDistanceNumber(track.distanceMeters))
                                }
                                append(" ")
                                withStyle(style = SpanStyle(fontSize = 14.sp, fontWeight = FontWeight.SemiBold)) {
                                    append(formatDistanceUnit(track.distanceMeters))
                                }
                            }
                        } else {
                            buildAnnotatedString { append(dash) }
                        }

                        Text(
                            text = distanceText,
                            modifier = Modifier.weight(1f),
                            lineHeight = 32.sp,
                        )
                        VerticalDivider(
                            modifier = Modifier
                                .padding(horizontal = 12.dp)
                                .height(48.dp),
                            color = MaterialTheme.colorScheme.outlineVariant,
                        )
                        Column(
                            horizontalAlignment = Alignment.Start,
                            modifier = Modifier.weight(1f),
                        ) {
                            Text(
                                text = track.durationMillis?.let { formatDuration(it) } ?: dash,
                                style = MaterialTheme.typography.headlineSmall,
                                fontWeight = FontWeight.Bold,
                            )
                            Text(
                                text = stringResource(R.string.metric_label_duration),
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }

                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            TrackCardMetricIconCell(
                                icon = Icons.Outlined.Speed,
                                value = formatSpeedKmh(track.averageSpeedKmh),
                                label = stringResource(R.string.track_metric_avg_speed),
                                modifier = Modifier.weight(1f),
                            )
                            TrackCardMetricIconCell(
                                icon = Icons.Outlined.Terrain,
                                value = formatListElevationMeters(track.totalClimbMeters),
                                label = stringResource(R.string.metric_label_elevation_gain),
                                modifier = Modifier.weight(1f),
                            )
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            TrackCardMetricIconCell(
                                icon = Icons.Outlined.Speed,
                                value = track.maxSpeedKmh?.let { "${formatSpeedKmhNumber(it)} км/ч" } ?: dash,
                                label = stringResource(R.string.track_list_label_max_speed),
                                modifier = Modifier.weight(1f),
                            )
                            TrackCardMetricIconCell(
                                icon = Icons.Outlined.MonitorHeart,
                                value = formatHeartRateBpm(track.averageHeartRateBpm),
                                label = stringResource(R.string.track_metric_avg_hr),
                                modifier = Modifier.weight(1f),
                            )
                        }
                    }
                }
            }

            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
            ) {
                TrackRouteMiniMap(
                    routePoints = track.routePoints,
                    modifier = Modifier
                        .fillMaxSize()
                        .clip(RoundedCornerShape(0.dp)),
                )
            }
        }
    }
}

@Composable
private fun TrackCardMetricIconCell(
    icon: ImageVector,
    value: String,
    label: String,
    modifier: Modifier = Modifier,
) {
    val accent = MaterialTheme.colorScheme.primary
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = accent,
                modifier = Modifier.size(18.dp),
            )
            Text(
                text = value,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
        }
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
