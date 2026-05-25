package com.astraf.hrgpslogger.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.astraf.hrgpslogger.R
import com.astraf.hrgpslogger.TrackSummary
import com.astraf.hrgpslogger.ui.formatListElevationMeters
import com.astraf.hrgpslogger.ui.formatRideHumanDate
import com.astraf.hrgpslogger.ui.formatSpeedKmh
import com.astraf.hrgpslogger.ui.formatStatsDistance
import com.astraf.hrgpslogger.ui.formatStatsMovingTime

@Composable
fun StatsBestRideCard(
    title: String,
    track: TrackSummary,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val distance = formatStatsDistance(track.distanceMeters ?: 0.0)
    Card(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface,
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(IntrinsicSize.Min),
        ) {
            Column(
                modifier = Modifier
                    .weight(1.4f)
                    .padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = formatRideHumanDate(track.startedAtMillis),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = "${distance.first} ${distance.second}",
                    style = MaterialTheme.typography.bodyLarge,
                )
                Text(
                    text = track.movingTimeMillis?.let { formatStatsMovingTime(it) }
                        ?: stringResource(R.string.value_dash),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = stringResource(
                        R.string.stats_best_ride_climb_speed,
                        formatListElevationMeters(track.totalClimbMeters),
                        formatSpeedKmh(track.averageSpeedKmh),
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            TrackRouteMiniMap(
                routePoints = track.routePoints,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(topEnd = 12.dp, bottomEnd = 12.dp)),
            )
        }
    }
}
