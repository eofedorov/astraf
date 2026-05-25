package com.astraf.hrgpslogger.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.astraf.hrgpslogger.RoutePreviewPoint
import com.astraf.hrgpslogger.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.max

private val RoutePurple = Color(0xFF7B5CF0)
private val StartGreen = Color(0xFF4CAF50)
private val FinishFlagBlack = Color(0xFF212121)
private val FinishFlagWhite = Color(0xFFFFFFFF)

@Composable
fun TrackRouteMiniMap(
    routePoints: List<RoutePreviewPoint>,
    modifier: Modifier = Modifier,
) {
    val viewport = remember(routePoints) { OsmMiniMapTiles.viewportForRoute(routePoints) }
    var tiles by remember(routePoints) { mutableStateOf<List<LoadedMiniMapTile>>(emptyList()) }

    LaunchedEffect(routePoints) {
        tiles = withContext(Dispatchers.IO) {
            OsmMiniMapTiles.loadTiles(viewport)
        }
    }

    val placeholderColor = MaterialTheme.colorScheme.surfaceContainerHigh

    Box(
        modifier = modifier.background(placeholderColor),
        contentAlignment = Alignment.Center,
    ) {
        when {
            routePoints.isEmpty() -> {
                Text(
                    text = stringResource(R.string.track_list_no_route),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(8.dp),
                )
            }
            else -> {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    clipRect {
                        if (tiles.isNotEmpty()) {
                            drawTileBackground(tiles, viewport)
                        }
                        drawRouteOverlay(
                            routePoints = routePoints,
                            viewport = viewport,
                        )
                    }
                }
            }
        }
    }
}

private data class MapCoverTransform(
    val scale: Float,
    val offsetX: Float,
    val offsetY: Float,
)

private fun DrawScope.coverTransform(viewport: MiniMapViewport): MapCoverTransform {
    val scale = max(
        size.width / viewport.widthPx.toFloat(),
        size.height / viewport.heightPx.toFloat(),
    )
    return MapCoverTransform(
        scale = scale,
        offsetX = (size.width - viewport.widthPx * scale) / 2f,
        offsetY = (size.height - viewport.heightPx * scale) / 2f,
    )
}

private fun DrawScope.drawTileBackground(
    tiles: List<LoadedMiniMapTile>,
    viewport: MiniMapViewport,
) {
    val transform = coverTransform(viewport)
    withTransform({
        translate(transform.offsetX, transform.offsetY)
        scale(transform.scale, transform.scale, pivot = Offset.Zero)
    }) {
        tiles.forEach { tile ->
            val left = (tile.tileX - viewport.minTileX) * 256f
            val top = (tile.tileY - viewport.minTileY) * 256f
            drawImage(
                image = tile.bitmap,
                topLeft = Offset(left, top),
            )
        }
    }
}

private fun DrawScope.drawRouteOverlay(
    routePoints: List<RoutePreviewPoint>,
    viewport: MiniMapViewport,
) {
    val transform = coverTransform(viewport)

    fun toCanvasOffset(latitude: Double, longitude: Double): Offset {
        val (localX, localY) = OsmMiniMapTiles.latLonToLocalPx(latitude, longitude, viewport)
        return Offset(
            x = transform.offsetX + localX * transform.scale,
            y = transform.offsetY + localY * transform.scale,
        )
    }

    if (routePoints.size == 1) {
        val center = toCanvasOffset(routePoints.first().latitude, routePoints.first().longitude)
        drawStartMarker(center)
        return
    }

    val projected = routePoints.map { point ->
        toCanvasOffset(point.latitude, point.longitude)
    }
    if (projected.size < 2) return

    val path = Path().apply {
        moveTo(projected.first().x, projected.first().y)
        projected.drop(1).forEach { point ->
            lineTo(point.x, point.y)
        }
    }
    drawPath(
        path = path,
        color = RoutePurple,
        style = Stroke(
            width = 4f,
            cap = StrokeCap.Round,
            join = StrokeJoin.Round,
        ),
    )
    drawStartMarker(projected.first())
    drawFinishFlag(projected.last())
}

private fun DrawScope.drawStartMarker(center: Offset) {
    drawCircle(color = Color.White, radius = 8f, center = center)
    drawCircle(color = StartGreen, radius = 6f, center = center)
}

private fun DrawScope.drawFinishFlag(center: Offset) {
    val poleTop = Offset(center.x, center.y - 14f)
    drawLine(
        color = Color(0xFF616161),
        start = center,
        end = poleTop,
        strokeWidth = 2f,
    )
    val flagWidth = 16f
    val flagHeight = 10f
    val flagLeft = poleTop.x
    val flagTop = poleTop.y - flagHeight
    val cellW = flagWidth / 4f
    val cellH = flagHeight / 2f
    for (row in 0..1) {
        for (col in 0..3) {
            val color = if ((row + col) % 2 == 0) FinishFlagWhite else FinishFlagBlack
            drawRect(
                color = color,
                topLeft = Offset(flagLeft + col * cellW, flagTop + row * cellH),
                size = Size(cellW, cellH),
            )
        }
    }
    drawRect(
        color = FinishFlagBlack,
        topLeft = Offset(flagLeft, flagTop),
        size = Size(flagWidth, flagHeight),
        style = Stroke(width = 1f),
    )
}
