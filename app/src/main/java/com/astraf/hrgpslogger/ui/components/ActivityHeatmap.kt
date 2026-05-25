package com.astraf.hrgpslogger.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.astraf.hrgpslogger.ActivityDay
import com.astraf.hrgpslogger.HeatmapLayout
import com.astraf.hrgpslogger.StatsPeriod
import com.astraf.hrgpslogger.ui.formatStatsMovingTime
import com.astraf.hrgpslogger.ui.formatStatsTooltipDistance
import com.astraf.hrgpslogger.ui.formatStatsTooltipRideCount
import java.time.format.DateTimeFormatter
import java.util.Locale

private val MonthLabelWidth = 44.dp
private val HeatmapCellGap = 2.dp
private val YearMinCellSize = 7.dp
private val YearScrollCellSize = 9.dp

@Composable
fun ActivityHeatmap(
    layout: HeatmapLayout,
    period: StatsPeriod,
    modifier: Modifier = Modifier,
) {
    if (layout.weeks.isEmpty()) return

    var selectedDay by remember(layout) { mutableStateOf<ActivityDay?>(null) }
    val accent = MaterialTheme.colorScheme.primary
    val emptyColor = MaterialTheme.colorScheme.surfaceContainerHighest
    val columnCount = layout.weeks.maxOf { it.size }.coerceAtLeast(1)

    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        selectedDay?.let { day ->
            StatsTooltipCard(
                title = formatHeatmapDate(day.date),
                lines = listOf(
                    formatStatsTooltipDistance(day.distanceMeters),
                    formatStatsTooltipRideCount(day.rideCount),
                    formatStatsMovingTime(day.movingTimeMillis),
                ),
            )
        }

        when (period) {
            StatsPeriod.Week, StatsPeriod.Month -> {
                val cellSize = when (period) {
                    StatsPeriod.Week -> 36.dp
                    else -> 28.dp
                }
                HeatmapGrid(
                    layout = layout,
                    cellSize = cellSize,
                    gap = 4.dp,
                    accent = accent,
                    emptyColor = emptyColor,
                    selectedDay = selectedDay,
                    onDaySelected = { day -> selectedDay = day },
                )
            }
            StatsPeriod.Year, StatsPeriod.AllTime -> {
                BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
                    val gapsTotal = HeatmapCellGap * (columnCount - 1).coerceAtLeast(0)
                    val fitCellSize =
                        (maxWidth - MonthLabelWidth - gapsTotal) / columnCount
                    val useHorizontalScroll = fitCellSize < YearMinCellSize
                    val cellSize = if (useHorizontalScroll) {
                        YearScrollCellSize
                    } else {
                        fitCellSize.coerceAtMost(12.dp)
                    }
                    val gridModifier = if (useHorizontalScroll) {
                        Modifier.horizontalScroll(rememberScrollState())
                    } else {
                        Modifier.fillMaxWidth()
                    }
                    HeatmapGrid(
                        layout = layout,
                        cellSize = cellSize,
                        gap = HeatmapCellGap,
                        accent = accent,
                        emptyColor = emptyColor,
                        selectedDay = selectedDay,
                        onDaySelected = { day -> selectedDay = day },
                        modifier = gridModifier,
                        showMonthLabels = true,
                    )
                }
            }
        }
    }
}

@Composable
private fun HeatmapGrid(
    layout: HeatmapLayout,
    cellSize: Dp,
    gap: Dp,
    accent: Color,
    emptyColor: Color,
    selectedDay: ActivityDay?,
    onDaySelected: (ActivityDay?) -> Unit,
    modifier: Modifier = Modifier,
    showMonthLabels: Boolean = layout.monthLabels.isNotEmpty(),
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(gap),
    ) {
        layout.weeks.forEachIndexed { weekIndex, week ->
            Row(
                horizontalArrangement = Arrangement.spacedBy(gap),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (showMonthLabels) {
                    Text(
                        text = layout.monthLabels.getOrElse(weekIndex) { "" },
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.width(MonthLabelWidth),
                        maxLines = 1,
                    )
                }
                Row(
                    horizontalArrangement = Arrangement.spacedBy(gap),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    week.forEach { day ->
                        HeatmapCell(
                            day = day,
                            cellSize = cellSize,
                            accent = accent,
                            emptyColor = emptyColor,
                            selected = day != null && day.date == selectedDay?.date,
                            onSelected = {
                                onDaySelected(
                                    if (day != null && day.date == selectedDay?.date) null else day,
                                )
                            },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun HeatmapCell(
    day: ActivityDay?,
    cellSize: Dp,
    accent: Color,
    emptyColor: Color,
    selected: Boolean,
    onSelected: () -> Unit,
) {
    val modifier = Modifier.size(cellSize)
    if (day == null) {
        Canvas(modifier = modifier) {
            drawRoundRect(
                color = emptyColor.copy(alpha = 0.2f),
                topLeft = Offset.Zero,
                size = Size(size.width, size.height),
                cornerRadius = CornerRadius(2f, 2f),
            )
        }
        return
    }
    Canvas(
        modifier = modifier.pointerInput(day.date) {
            detectTapGestures { onSelected() }
        },
    ) {
        val color = heatmapColor(day.intensity, accent, emptyColor, selected)
        drawRoundRect(
            color = color,
            topLeft = Offset.Zero,
            size = Size(size.width, size.height),
            cornerRadius = CornerRadius(2f, 2f),
        )
    }
}

private fun heatmapColor(
    intensity: Float,
    accent: Color,
    empty: Color,
    selected: Boolean,
): Color {
    if (intensity <= 0f) {
        return if (selected) {
            accent.copy(alpha = 0.2f)
        } else {
            empty.copy(alpha = 0.35f)
        }
    }
    val alpha = if (selected) 1f else 0.25f + intensity * 0.75f
    return accent.copy(alpha = alpha)
}

private fun formatHeatmapDate(date: java.time.LocalDate): String {
    val formatter = DateTimeFormatter.ofPattern("d MMMM", Locale.forLanguageTag("ru"))
    return formatter.format(date)
}
