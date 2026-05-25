package com.astraf.hrgpslogger.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.astraf.hrgpslogger.ActivityBucket
import com.astraf.hrgpslogger.ui.formatStatsTooltipDistance
import com.astraf.hrgpslogger.ui.formatStatsTooltipRideCount
import kotlin.math.max

@Composable
fun StatsBarChart(
    buckets: List<ActivityBucket>,
    xAxisCaption: String? = null,
    modifier: Modifier = Modifier,
) {
    if (buckets.isEmpty()) return

    var selectedIndex by remember(buckets) { mutableIntStateOf(-1) }
    val maxDistance = remember(buckets) {
        buckets.maxOf { it.distanceMeters }.coerceAtLeast(1.0)
    }
    val barColor = MaterialTheme.colorScheme.primary
    val gridColor = MaterialTheme.colorScheme.outlineVariant
    val labelColor = MaterialTheme.colorScheme.onSurfaceVariant
    val selectedLabelColor = MaterialTheme.colorScheme.primary
    val density = LocalDensity.current
    val labelIndices = remember(buckets.size) { xAxisLabelIndices(buckets.size) }

    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        selectedIndex.takeIf { it in buckets.indices }?.let { index ->
            val bucket = buckets[index]
            StatsTooltipCard(
                title = bucket.tooltipDateLabel,
                lines = listOf(
                    formatStatsTooltipDistance(bucket.distanceMeters),
                    formatStatsTooltipRideCount(bucket.rideCount),
                ),
            )
        }

        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(176.dp)
                .pointerInput(buckets) {
                    detectTapGestures { offset ->
                        val barCount = buckets.size
                        if (barCount == 0) return@detectTapGestures
                        val barWidth = size.width / barCount
                        val index = (offset.x / barWidth).toInt().coerceIn(0, barCount - 1)
                        selectedIndex = if (selectedIndex == index) -1 else index
                    }
                },
        ) {
            val labelAreaHeight = 28f
            val plotBottom = size.height - labelAreaHeight
            val plotTop = 8f
            val plotHeight = plotBottom - plotTop
            val barCount = buckets.size
            val slotWidth = size.width / barCount
            val barWidth = slotWidth * 0.65f

            drawLine(
                color = gridColor,
                start = Offset(0f, plotBottom),
                end = Offset(size.width, plotBottom),
                strokeWidth = 1f,
            )

            buckets.forEachIndexed { index, bucket ->
                val fraction = (bucket.distanceMeters / maxDistance).toFloat()
                val barHeight = max(fraction * plotHeight, if (bucket.distanceMeters > 0) 4f else 0f)
                val left = index * slotWidth + (slotWidth - barWidth) / 2f
                val top = plotBottom - barHeight
                val color = if (index == selectedIndex) {
                    barColor.copy(alpha = 1f)
                } else {
                    barColor.copy(alpha = 0.75f)
                }
                drawRoundRect(
                    color = color,
                    topLeft = Offset(left, top),
                    size = Size(barWidth, barHeight),
                    cornerRadius = CornerRadius(4f, 4f),
                )
            }

            val labelTextSizePx = with(density) { 11.sp.toPx() }
            val labelPaint = android.graphics.Paint().apply {
                textSize = labelTextSizePx
                textAlign = android.graphics.Paint.Align.CENTER
                isAntiAlias = true
            }
            val labelBaselineY = plotBottom + labelTextSizePx + 6f

            labelIndices.forEach { index ->
                val bucket = buckets[index]
                labelPaint.color = if (index == selectedIndex) {
                    selectedLabelColor.toArgb()
                } else {
                    labelColor.toArgb()
                }
                val centerX = index * slotWidth + slotWidth / 2f
                drawContext.canvas.nativeCanvas.drawText(
                    bucket.label,
                    centerX,
                    labelBaselineY,
                    labelPaint,
                )
            }
        }

        xAxisCaption?.let { caption ->
            Text(
                text = caption,
                style = MaterialTheme.typography.labelSmall,
                color = labelColor,
                modifier = Modifier.padding(start = 2.dp),
            )
        }
    }
}

@Composable
fun StatsTooltipCard(
    title: String,
    lines: List<String>,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        ),
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface,
            )
            lines.forEach { line ->
                Text(
                    text = line,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/** Индексы подписей оси X: для месяца — 1, 5, 10, … без обрезания в узких ячейках. */
internal fun xAxisLabelIndices(barCount: Int): List<Int> = when {
    barCount <= 12 -> (0 until barCount).toList()
    barCount <= 20 -> (0 until barCount).filter { it % 2 == 0 || it == barCount - 1 }
    else -> (0 until barCount).filter { it % 5 == 0 || it == barCount - 1 }
}
