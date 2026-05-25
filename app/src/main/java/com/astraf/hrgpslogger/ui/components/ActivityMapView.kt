package com.astraf.hrgpslogger.ui.components

import android.graphics.PointF
import android.os.Bundle
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.astraf.hrgpslogger.ActivityMapTrack
import com.astraf.hrgpslogger.RoutePreviewPoint
import org.maplibre.geojson.Feature
import org.maplibre.geojson.FeatureCollection
import org.maplibre.geojson.LineString
import org.maplibre.geojson.Point
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.geometry.LatLngBounds
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.MapView
import org.maplibre.android.maps.Style
import org.maplibre.android.style.layers.LineLayer
import org.maplibre.android.style.layers.PropertyFactory.lineColor
import org.maplibre.android.style.layers.PropertyFactory.lineWidth
import org.maplibre.android.style.sources.GeoJsonSource
import org.maplibre.android.style.sources.RasterSource

private const val OSM_STYLE_URI = "asset://osm_raster_style.json"
private const val OSM_SOURCE_ID = "osm"
private const val ACTIVITY_SOURCE_ID = "activity-tracks"
private const val ACTIVITY_LAYER_ID = "activity-tracks-layer"
private const val FILE_NAME_PROPERTY = "fileName"
private const val ROUTE_COLOR = "#7B5CF0"
private const val DEFAULT_LAT = 55.751244
private const val DEFAULT_LON = 37.618423
private const val DEFAULT_ZOOM = 10.0
private const val BOUNDS_PADDING_PX = 56
@Composable
fun ActivityMapView(
    tracks: List<ActivityMapTrack>,
    isMapActive: Boolean,
    onTracksSelected: (List<String>) -> Unit,
    modifier: Modifier = Modifier,
) {
    val lifecycleOwner = LocalLifecycleOwner.current
    var mapLibreMap by remember { mutableStateOf<MapLibreMap?>(null) }
    var style by remember { mutableStateOf<Style?>(null) }
    var isDisposed by remember { mutableStateOf(false) }
    var fitKey by remember { mutableStateOf<String?>(null) }

    val context = LocalContext.current
    val mapView = remember {
        MapView(context).apply {
            onCreate(Bundle())
        }
    }

    fun syncMapLifecycleToOwner() {
        if (isDisposed) return
        val state = lifecycleOwner.lifecycle.currentState
        if (state.isAtLeast(Lifecycle.State.STARTED)) {
            mapView.onStart()
        }
        if (state.isAtLeast(Lifecycle.State.RESUMED) && isMapActive) {
            mapView.onResume()
        } else {
            mapView.onPause()
        }
    }

    DisposableEffect(lifecycleOwner, isMapActive) {
        val observer = LifecycleEventObserver { _, event ->
            if (isDisposed) return@LifecycleEventObserver
            when (event) {
                Lifecycle.Event.ON_START -> mapView.onStart()
                Lifecycle.Event.ON_RESUME -> if (isMapActive) mapView.onResume()
                Lifecycle.Event.ON_PAUSE -> mapView.onPause()
                Lifecycle.Event.ON_STOP -> mapView.onStop()
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        syncMapLifecycleToOwner()
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    LaunchedEffect(isMapActive) {
        if (isDisposed) return@LaunchedEffect
        if (isMapActive && lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) {
            mapView.onResume()
        } else {
            mapView.onPause()
        }
    }

    DisposableEffect(mapView) {
        mapView.getMapAsync { map ->
            if (isDisposed) return@getMapAsync
            map.uiSettings.apply {
                isZoomGesturesEnabled = true
                isScrollGesturesEnabled = true
                isRotateGesturesEnabled = false
                isTiltGesturesEnabled = false
                isAttributionEnabled = true
            }
            mapLibreMap = map
            map.addOnMapClickListener { latLng ->
                val screen = map.projection.toScreenLocation(latLng)
                val features = map.queryRenderedFeatures(
                    PointF(screen.x, screen.y),
                    ACTIVITY_LAYER_ID,
                )
                val fileNames = features
                    .mapNotNull { it.getStringProperty(FILE_NAME_PROPERTY) }
                    .distinct()
                if (fileNames.isNotEmpty()) {
                    onTracksSelected(fileNames)
                }
                true
            }
            map.setStyle(OSM_STYLE_URI) { loadedStyle ->
                if (isDisposed) return@setStyle
                style = loadedStyle
                setupActivityLayers(loadedStyle)
                loadedStyle.getSourceAs<RasterSource>(OSM_SOURCE_ID)?.setVolatile(false)
            }
        }
        onDispose {
            isDisposed = true
            mapLibreMap = null
            style = null
            runCatching {
                mapView.onPause()
                mapView.onStop()
                mapView.onDestroy()
            }
        }
    }

    val tracksKey = remember(tracks) { tracks.joinToString { it.fileName } }

    LaunchedEffect(style, tracksKey) {
        if (isDisposed) return@LaunchedEffect
        val currentStyle = style ?: return@LaunchedEffect
        val map = mapLibreMap ?: return@LaunchedEffect
        updateActivityTracks(currentStyle, tracks)
        if (fitKey != tracksKey) {
            fitCameraToTracks(map, tracks)
            fitKey = tracksKey
        }
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(220.dp),
    ) {
        AndroidView(
            factory = { mapView },
            modifier = Modifier.fillMaxSize(),
        )
    }
}

private fun setupActivityLayers(style: Style) {
    if (style.getSource(ACTIVITY_SOURCE_ID) == null) {
        style.addSource(GeoJsonSource(ACTIVITY_SOURCE_ID))
        style.addLayer(
            LineLayer(ACTIVITY_LAYER_ID, ACTIVITY_SOURCE_ID).withProperties(
                lineColor(ROUTE_COLOR),
                lineWidth(2.5f),
            ),
        )
    }
}

private fun updateActivityTracks(style: Style, tracks: List<ActivityMapTrack>) {
    val source = style.getSourceAs<GeoJsonSource>(ACTIVITY_SOURCE_ID) ?: return
    val features = tracks.mapNotNull { track ->
        val coordinates = track.routePoints
            .takeIf { it.size >= 2 }
            ?.map { Point.fromLngLat(it.longitude, it.latitude) }
            ?: return@mapNotNull null
        Feature.fromGeometry(LineString.fromLngLats(coordinates)).apply {
            addStringProperty(FILE_NAME_PROPERTY, track.fileName)
        }
    }
    source.setGeoJson(FeatureCollection.fromFeatures(features))
}

private fun fitCameraToTracks(map: MapLibreMap, tracks: List<ActivityMapTrack>) {
    val points = tracks.flatMap { it.routePoints }
    if (points.isEmpty()) {
        map.moveCamera(
            CameraUpdateFactory.newLatLngZoom(LatLng(DEFAULT_LAT, DEFAULT_LON), DEFAULT_ZOOM),
        )
        return
    }
    if (points.size == 1) {
        val p = points.first()
        map.moveCamera(
            CameraUpdateFactory.newLatLngZoom(LatLng(p.latitude, p.longitude), 14.0),
        )
        return
    }
    val builder = LatLngBounds.Builder()
    points.forEach { point ->
        builder.include(LatLng(point.latitude, point.longitude))
    }
    map.moveCamera(
        CameraUpdateFactory.newLatLngBounds(builder.build(), BOUNDS_PADDING_PX),
    )
}
