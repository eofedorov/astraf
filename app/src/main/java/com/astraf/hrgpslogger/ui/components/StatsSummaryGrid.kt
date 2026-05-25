package com.astraf.hrgpslogger.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.astraf.hrgpslogger.R
import com.astraf.hrgpslogger.StatsSummary
import com.astraf.hrgpslogger.ui.formatStatsClimbMeters
import com.astraf.hrgpslogger.ui.formatStatsDistance
import com.astraf.hrgpslogger.ui.formatStatsMovingTime
import com.astraf.hrgpslogger.ui.formatStatsRideCount
import com.astraf.hrgpslogger.ui.formatStatsSpeedKmh

@Composable
fun StatsSummaryGrid(
    summary: StatsSummary,
    modifier: Modifier = Modifier,
) {
    val distance = formatStatsDistance(summary.totalDistanceMeters)
    val movingTime = formatStatsMovingTime(summary.totalMovingTimeMillis)
    val rideCount = formatStatsRideCount(summary.rideCount)
    val climb = formatStatsClimbMeters(summary.totalClimbMeters)
    val avgSpeed = formatStatsSpeedKmh(summary.averageMovingSpeedKmh)
    val maxSpeed = formatStatsSpeedKmh(summary.maxSpeedKmh)

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            StatsMetricCard(
                label = stringResource(R.string.stats_metric_distance),
                value = distance.first,
                unit = distance.second,
                modifier = Modifier.weight(1f),
            )
            StatsMetricCard(
                label = stringResource(R.string.stats_metric_moving_time),
                value = movingTime,
                unit = "",
                modifier = Modifier.weight(1f),
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            StatsMetricCard(
                label = stringResource(R.string.stats_metric_ride_count),
                value = rideCount.first,
                unit = rideCount.second,
                modifier = Modifier.weight(1f),
            )
            StatsMetricCard(
                label = stringResource(R.string.stats_metric_climb),
                value = climb.first,
                unit = climb.second,
                modifier = Modifier.weight(1f),
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            StatsMetricCard(
                label = stringResource(R.string.stats_metric_avg_speed),
                value = avgSpeed.first,
                unit = avgSpeed.second,
                modifier = Modifier.weight(1f),
            )
            StatsMetricCard(
                label = stringResource(R.string.stats_metric_max_speed),
                value = maxSpeed.first,
                unit = maxSpeed.second,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun StatsMetricCard(
    label: String,
    value: String,
    unit: String,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier.height(96.dp),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        ),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Column(horizontalAlignment = Alignment.Start) {
                Text(
                    text = value,
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                )
                if (unit.isNotEmpty()) {
                    Text(
                        text = unit,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}
