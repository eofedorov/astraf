package com.astraf.hrgpslogger.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.astraf.hrgpslogger.R
import com.astraf.hrgpslogger.TrackChartPoint
import com.astraf.hrgpslogger.ui.formatDuration
import java.util.Locale

enum class TrackChartYAxis {
    Speed,
    HeartRate,
    Elevation,
}

private const val INTERNAL_VERTICAL_GRID_LINES = 4

@Composable
fun TrackTimeSeriesChart(
    series: List<TrackChartPoint>,
    yAxis: TrackChartYAxis,
    modifier: Modifier = Modifier,
) {
    if (series.size < 2) return

    val chartSpec = remember(yAxis) { chartSpecFor(yAxis) }
    val minX = 0L
    val maxX = series.maxOf { it.elapsedMillis }.coerceAtLeast(1L)
    val rawMinY = series.minOf { it.value }
    val rawMaxY = series.maxOf { it.value }
    val yPadding = (rawMaxY - rawMinY).coerceAtLeast(chartSpec.minSpan) * 0.08f
    val plotMinY = if (chartSpec.yMin.isFinite()) {
        chartSpec.yMin
    } else {
        rawMinY - yPadding
    }
    val plotMaxY = rawMaxY + yPadding
    val yTicks = remember(plotMinY, plotMaxY, yAxis) {
        buildFloatTicks(plotMinY, plotMaxY, tickCount = 4)
    }
    val xGridLines = remember(maxX) {
        (1..INTERNAL_VERTICAL_GRID_LINES).map { index ->
            maxX * index / (INTERNAL_VERTICAL_GRID_LINES + 1).toLong()
        }
    }
    val xLabelTicks = remember(maxX) {
        listOf(0L, maxX / 2, maxX)
    }

    val lineColor = MaterialTheme.colorScheme.primary
    val gridColor = MaterialTheme.colorScheme.outlineVariant
    val labelColor = MaterialTheme.colorScheme.onSurfaceVariant
    val labelStyle = MaterialTheme.typography.labelSmall

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(156.dp),
        ) {
            Column(
                modifier = Modifier
                    .width(44.dp)
                    .fillMaxHeight(),
                verticalArrangement = Arrangement.SpaceBetween,
                horizontalAlignment = Alignment.End,
            ) {
                yTicks.asReversed().forEach { tick ->
                    Text(
                        text = chartSpec.formatValue(tick),
                        style = labelStyle,
                        color = labelColor,
                        textAlign = TextAlign.End,
                    )
                }
            }
            Canvas(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .padding(start = 4.dp),
            ) {
                val plotLeft = 0f
                val plotRight = size.width
                val plotTop = 4f
                val plotBottom = size.height - 4f
                val plotWidth = plotRight - plotLeft
                val plotHeight = plotBottom - plotTop
                if (plotWidth <= 0f || plotHeight <= 0f) return@Canvas

                val yRange = (plotMaxY - plotMinY).coerceAtLeast(chartSpec.minSpan)

                fun toX(elapsedMillis: Long): Float =
                    plotLeft + elapsedMillis.toFloat() / maxX.toFloat() * plotWidth

                fun toY(value: Float): Float =
                    plotBottom - ((value - plotMinY) / yRange * plotHeight)

                yTicks.forEach { tick ->
                    val y = toY(tick)
                    drawLine(
                        color = gridColor,
                        start = Offset(plotLeft, y),
                        end = Offset(plotRight, y),
                        strokeWidth = 1f,
                    )
                }

                drawLine(
                    color = gridColor,
                    start = Offset(plotLeft, plotBottom),
                    end = Offset(plotRight, plotBottom),
                    strokeWidth = 1f,
                )

                xGridLines.forEach { tick ->
                    val x = toX(tick)
                    drawLine(
                        color = gridColor,
                        start = Offset(x, plotTop),
                        end = Offset(x, plotBottom),
                        strokeWidth = 1f,
                    )
                }

                val path = Path()
                series.forEachIndexed { index, point ->
                    val x = toX(point.elapsedMillis)
                    val yValue = if (chartSpec.yMin.isFinite()) {
                        point.value.coerceAtLeast(chartSpec.yMin)
                    } else {
                        point.value
                    }
                    val y = toY(yValue)
                    if (index == 0) {
                        path.moveTo(x, y)
                    } else {
                        path.lineTo(x, y)
                    }
                }
                drawPath(
                    path = path,
                    color = lineColor,
                    style = Stroke(width = 2.5f, cap = StrokeCap.Round),
                )
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 48.dp, end = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            xLabelTicks.forEach { tick ->
                Text(
                    text = formatDuration(tick),
                    style = labelStyle,
                    color = labelColor,
                )
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 48.dp, end = 4.dp),
        ) {
            Text(
                text = stringResource(R.string.track_chart_axis_time),
                style = labelStyle,
                color = labelColor,
            )
            Spacer(modifier = Modifier.weight(1f))
            Text(
                text = chartSpec.unit,
                style = labelStyle,
                color = labelColor,
            )
        }
    }
}

private data class ChartSpec(
    val unit: String,
    val minSpan: Float,
    val yMin: Float,
    val formatValue: (Float) -> String,
)

private fun chartSpecFor(yAxis: TrackChartYAxis): ChartSpec = when (yAxis) {
    TrackChartYAxis.Speed -> ChartSpec(
        unit = "км/ч",
        minSpan = 1f,
        yMin = 0f,
        formatValue = { value ->
            String.format(Locale.getDefault(), "%.0f", value.coerceAtLeast(0f))
        },
    )
    TrackChartYAxis.HeartRate -> ChartSpec(
        unit = "уд/мин",
        minSpan = 5f,
        yMin = 0f,
        formatValue = { value ->
            String.format(Locale.getDefault(), "%.0f", value)
        },
    )
    TrackChartYAxis.Elevation -> ChartSpec(
        unit = "м",
        minSpan = 5f,
        yMin = Float.NEGATIVE_INFINITY,
        formatValue = { value ->
            String.format(Locale.getDefault(), "%.0f", value)
        },
    )
}

private fun buildFloatTicks(min: Float, max: Float, tickCount: Int): List<Float> {
    if (tickCount <= 1) return listOf(min)
    if (max <= min) return List(tickCount) { min }
    val step = (max - min) / (tickCount - 1)
    return List(tickCount) { index -> min + step * index }
}
