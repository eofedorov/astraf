package com.astraf.hrgpslogger.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.min

data class RideMetricCell(
    val label: String,
    val number: String,
    val unit: String? = null,
)

@Composable
fun RideCompactMetricsBar(
    line: String,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(36.dp)
            .background(MaterialTheme.colorScheme.surfaceContainerHigh),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = line,
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 12.dp),
        )
    }
}

@Composable
private fun rememberFittingNumberFontSize(
    text: String,
    maxWidthPx: Int,
    maxHeightPx: Int,
    maxSp: Float = 152f,
    minSp: Float = 40f,
): Float {
    val textMeasurer = rememberTextMeasurer()
    val density = LocalDensity.current.density
    return remember(text, maxWidthPx, maxHeightPx) {
        if (maxWidthPx <= 0 || maxHeightPx <= 0) return@remember minSp
        var size = min(maxWidthPx, maxHeightPx) * 0.42f / density
        size = size.coerceIn(minSp, maxSp)
        val constraints = Constraints(maxWidth = maxWidthPx, maxHeight = maxHeightPx)
        while (size >= minSp) {
            val result = textMeasurer.measure(
                text = text,
                style = TextStyle(
                    fontSize = size.sp,
                    lineHeight = size.sp,
                    fontWeight = FontWeight.Bold,
                ),
                constraints = constraints,
            )
            if (result.size.width <= maxWidthPx && result.size.height <= maxHeightPx) {
                break
            }
            size -= 2f
        }
        size.coerceAtLeast(minSp)
    }
}

@Composable
private fun RideMetricFullscreenCell(
    cell: RideMetricCell,
    modifier: Modifier = Modifier,
) {
    val unitStyle = MaterialTheme.typography.labelMedium
    val unitColor = MaterialTheme.colorScheme.onSurfaceVariant
    val density = LocalDensity.current

    BoxWithConstraints(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        val unitWidthPx = if (cell.unit != null) {
            with(density) { 52.dp.roundToPx() }
        } else {
            0
        }
        val contentWidthPx = with(density) { maxWidth.roundToPx() } - with(density) { 12.dp.roundToPx() }
        val numberMaxWidthPx = (contentWidthPx - unitWidthPx).coerceAtLeast(1)
        val numberMaxHeightPx = with(density) { (maxHeight * 0.55f).roundToPx() }.coerceAtLeast(1)
        val numberSize = rememberFittingNumberFontSize(
            text = cell.number,
            maxWidthPx = numberMaxWidthPx,
            maxHeightPx = numberMaxHeightPx,
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 6.dp, vertical = 4.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Text(
                text = cell.label,
                style = unitStyle,
                color = unitColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.Bottom,
                horizontalArrangement = Arrangement.Center,
            ) {
                Text(
                    text = cell.number,
                    fontSize = numberSize.sp,
                    lineHeight = numberSize.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.widthIn(max = with(density) { numberMaxWidthPx.toDp() }),
                )
                if (cell.unit != null) {
                    Text(
                        text = cell.unit,
                        style = unitStyle,
                        color = unitColor,
                        fontWeight = FontWeight.Normal,
                        modifier = Modifier.padding(
                            start = 4.dp,
                            bottom = (numberSize * 0.08f).dp,
                        ),
                        maxLines = 1,
                    )
                }
            }
        }
    }
}

@Composable
fun RideMetricsFullscreenGrid(
    cells: List<RideMetricCell>,
    modifier: Modifier = Modifier,
) {
    require(cells.isNotEmpty()) { "Expected at least one metric cell" }
    val rows = cells.chunked(2)
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surface),
    ) {
        rows.forEachIndexed { rowIndex, rowCells ->
            if (rowIndex > 0) {
                HorizontalDivider(
                    thickness = 1.dp,
                    color = MaterialTheme.colorScheme.outlineVariant,
                )
            }
            if (rowCells.size == 1) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                ) {
                    RideMetricFullscreenCell(cell = rowCells.single())
                }
            } else {
                Row(modifier = Modifier.weight(1f).fillMaxWidth()) {
                    rowCells.forEachIndexed { colIndex, cell ->
                        if (colIndex > 0) {
                            VerticalDivider(
                                thickness = 1.dp,
                                color = MaterialTheme.colorScheme.outlineVariant,
                            )
                        }
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight(),
                        ) {
                            RideMetricFullscreenCell(cell = cell)
                        }
                    }
                }
            }
        }
    }
}
