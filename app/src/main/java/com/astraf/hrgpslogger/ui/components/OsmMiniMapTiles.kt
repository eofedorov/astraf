package com.astraf.hrgpslogger.ui.components

import android.graphics.BitmapFactory
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import com.astraf.hrgpslogger.RoutePreviewPoint
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.min
import kotlin.math.tan
import okhttp3.OkHttpClient
import okhttp3.Request

internal data class MiniMapViewport(
    val zoom: Int,
    val minTileX: Int,
    val maxTileX: Int,
    val minTileY: Int,
    val maxTileY: Int,
    val widthPx: Int,
    val heightPx: Int,
)

internal data class LoadedMiniMapTile(
    val tileX: Int,
    val tileY: Int,
    val bitmap: ImageBitmap,
)

internal object OsmMiniMapTiles {
    private const val TILE_SIZE_PX = 256
    private const val DEFAULT_LAT = 55.751244
    private const val DEFAULT_LON = 37.618423
    private const val USER_AGENT = "AstrafHrGpsLogger/1.0"

    private val httpClient = OkHttpClient.Builder().build()
    private val tileCache = mutableMapOf<String, ImageBitmap>()

    fun viewportForRoute(
        routePoints: List<RoutePreviewPoint>,
        maxTileSpan: Int = 3,
    ): MiniMapViewport {
        val (minLat, maxLat, minLon, maxLon) = if (routePoints.isEmpty()) {
            val pad = 0.02
            Quad(DEFAULT_LAT - pad, DEFAULT_LAT + pad, DEFAULT_LON - pad, DEFAULT_LON + pad)
        } else {
            Quad(
                routePoints.minOf { it.latitude },
                routePoints.maxOf { it.latitude },
                routePoints.minOf { it.longitude },
                routePoints.maxOf { it.longitude },
            )
        }
        val latPad = max((maxLat - minLat) * 0.04, 0.0008)
        val lonPad = max((maxLon - minLon) * 0.04, 0.0008)
        val bounds = Quad(minLat - latPad, maxLat + latPad, minLon - lonPad, maxLon + lonPad)

        var chosenZoom = 10
        for (zoom in 17 downTo 4) {
            val minTileX = lonToTileX(bounds.minLon, zoom)
            val maxTileX = lonToTileX(bounds.maxLon, zoom)
            val minTileY = latToTileY(bounds.maxLat, zoom)
            val maxTileY = latToTileY(bounds.minLat, zoom)
            val tilesWide = maxTileX - minTileX + 1
            val tilesHigh = maxTileY - minTileY + 1
            if (tilesWide <= maxTileSpan && tilesHigh <= maxTileSpan) {
                chosenZoom = zoom
                break
            }
        }

        val zoom = chosenZoom
        val minTileX = lonToTileX(bounds.minLon, zoom)
        val maxTileX = lonToTileX(bounds.maxLon, zoom)
        val minTileY = latToTileY(bounds.maxLat, zoom)
        val maxTileY = latToTileY(bounds.minLat, zoom)

        return MiniMapViewport(
            zoom = zoom,
            minTileX = minTileX,
            maxTileX = maxTileX,
            minTileY = minTileY,
            maxTileY = maxTileY,
            widthPx = (maxTileX - minTileX + 1) * TILE_SIZE_PX,
            heightPx = (maxTileY - minTileY + 1) * TILE_SIZE_PX,
        )
    }

    fun loadTiles(viewport: MiniMapViewport): List<LoadedMiniMapTile> {
        val tiles = mutableListOf<LoadedMiniMapTile>()
        for (tileX in viewport.minTileX..viewport.maxTileX) {
            for (tileY in viewport.minTileY..viewport.maxTileY) {
                val bitmap = loadTile(viewport.zoom, tileX, tileY) ?: continue
                tiles.add(LoadedMiniMapTile(tileX, tileY, bitmap))
            }
        }
        return tiles
    }

    fun latLonToLocalPx(
        latitude: Double,
        longitude: Double,
        viewport: MiniMapViewport,
    ): Pair<Float, Float> {
        val worldX = lonToWorldPx(longitude, viewport.zoom)
        val worldY = latToWorldPx(latitude, viewport.zoom)
        val originX = viewport.minTileX * TILE_SIZE_PX.toDouble()
        val originY = viewport.minTileY * TILE_SIZE_PX.toDouble()
        return (worldX - originX).toFloat() to (worldY - originY).toFloat()
    }

    private fun loadTile(zoom: Int, tileX: Int, tileY: Int): ImageBitmap? {
        val key = "$zoom/$tileX/$tileY"
        tileCache[key]?.let { return it }
        val request = Request.Builder()
            .url("https://tile.openstreetmap.org/$zoom/$tileX/$tileY.png")
            .header("User-Agent", USER_AGENT)
            .build()
        val bytes = runCatching {
            httpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@use null
                response.body.bytes()
            }
        }.getOrNull() ?: return null
        val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.asImageBitmap() ?: return null
        tileCache[key] = bitmap
        return bitmap
    }

    private fun lonToTileX(lon: Double, zoom: Int): Int =
        floor((lon + 180.0) / 360.0 * (1 shl zoom)).toInt()

    private fun latToTileY(lat: Double, zoom: Int): Int {
        val latRad = Math.toRadians(lat)
        val n = (1.0 - ln(tan(latRad) + 1.0 / cos(latRad)) / Math.PI) / 2.0
        return floor(n * (1 shl zoom)).toInt()
    }

    private fun lonToWorldPx(lon: Double, zoom: Int): Double =
        (lon + 180.0) / 360.0 * TILE_SIZE_PX * (1 shl zoom)

    private fun latToWorldPx(lat: Double, zoom: Int): Double {
        val sinLat = kotlin.math.sin(Math.toRadians(lat))
        val y = 0.5 - ln((1.0 + sinLat) / (1.0 - sinLat)) / (4.0 * Math.PI)
        return y * TILE_SIZE_PX * (1 shl zoom)
    }

    private data class Quad(
        val minLat: Double,
        val maxLat: Double,
        val minLon: Double,
        val maxLon: Double,
    )
}
