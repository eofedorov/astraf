package com.astraf.hrgpslogger.ui.components

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
import com.astraf.hrgpslogger.AcceptedGpsPoint
import com.astraf.hrgpslogger.TrackSegment
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
import org.maplibre.android.style.layers.CircleLayer
import org.maplibre.android.style.layers.LineLayer
import org.maplibre.android.style.layers.PropertyFactory.circleColor
import org.maplibre.android.style.layers.PropertyFactory.circleRadius
import org.maplibre.android.style.layers.PropertyFactory.circleStrokeColor
import org.maplibre.android.style.layers.PropertyFactory.circleStrokeWidth
import org.maplibre.android.style.layers.PropertyFactory.lineColor
import org.maplibre.android.style.layers.PropertyFactory.lineWidth
import org.maplibre.android.style.sources.GeoJsonSource
import org.maplibre.android.style.sources.RasterSource

private const val OSM_STYLE_URI = "asset://osm_raster_style.json"
private const val OSM_SOURCE_ID = "osm"
private const val TRACK_SOURCE_ID = "track-detail-line"
private const val TRACK_LAYER_ID = "track-detail-line-layer"
private const val START_SOURCE_ID = "track-detail-start"
private const val START_LAYER_ID = "track-detail-start-layer"
private const val FINISH_SOURCE_ID = "track-detail-finish"
private const val FINISH_LAYER_ID = "track-detail-finish-layer"
private const val DEFAULT_LAT = 55.751244
private const val DEFAULT_LON = 37.618423
private const val DEFAULT_ZOOM = 12.0
private const val SINGLE_POINT_ZOOM = 15.0
private const val BOUNDS_PADDING_PX = 48

@Composable
fun StaticTrackMapView(
    segments: List<TrackSegment>,
    points: List<AcceptedGpsPoint>,
    isMapActive: Boolean,
    modifier: Modifier = Modifier,
) {
    val lifecycleOwner = LocalLifecycleOwner.current
    var mapLibreMap by remember { mutableStateOf<MapLibreMap?>(null) }
    var style by remember { mutableStateOf<Style?>(null) }
    var isDisposed by remember { mutableStateOf(false) }
    var didFitBounds by remember { mutableStateOf(false) }

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
                isRotateGesturesEnabled = true
                isTiltGesturesEnabled = true
                isAttributionEnabled = true
            }
            mapLibreMap = map
            map.setStyle(OSM_STYLE_URI) { loadedStyle ->
                if (isDisposed) return@setStyle
                style = loadedStyle
                setupTrackLayers(loadedStyle)
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

    LaunchedEffect(style, segments, points) {
        if (isDisposed) return@LaunchedEffect
        val currentStyle = style ?: return@LaunchedEffect
        val map = mapLibreMap ?: return@LaunchedEffect
        updateTrackLayers(currentStyle, segments, points)
        if (!didFitBounds && points.isNotEmpty()) {
            fitCameraToRoute(map, points)
            didFitBounds = true
        } else if (points.isEmpty() && !didFitBounds) {
            map.cameraPosition = org.maplibre.android.camera.CameraPosition.Builder()
                .target(LatLng(DEFAULT_LAT, DEFAULT_LON))
                .zoom(DEFAULT_ZOOM)
                .build()
            didFitBounds = true
        }
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(240.dp),
    ) {
        AndroidView(
            factory = { mapView },
            modifier = Modifier.fillMaxSize(),
        )
    }
}

private fun setupTrackLayers(style: Style) {
    if (style.getSource(TRACK_SOURCE_ID) == null) {
        style.addSource(GeoJsonSource(TRACK_SOURCE_ID))
        style.addLayer(
            LineLayer(TRACK_LAYER_ID, TRACK_SOURCE_ID).withProperties(
                lineColor("#2196F3"),
                lineWidth(4f),
            ),
        )
    }
    if (style.getSource(START_SOURCE_ID) == null) {
        style.addSource(GeoJsonSource(START_SOURCE_ID))
        style.addLayer(
            CircleLayer(START_LAYER_ID, START_SOURCE_ID).withProperties(
                circleRadius(7f),
                circleColor("#4CAF50"),
                circleStrokeWidth(2f),
                circleStrokeColor("#FFFFFF"),
            ),
        )
    }
    if (style.getSource(FINISH_SOURCE_ID) == null) {
        style.addSource(GeoJsonSource(FINISH_SOURCE_ID))
        style.addLayer(
            CircleLayer(FINISH_LAYER_ID, FINISH_SOURCE_ID).withProperties(
                circleRadius(7f),
                circleColor("#FF9800"),
                circleStrokeWidth(2f),
                circleStrokeColor("#FFFFFF"),
            ),
        )
    }
}

private fun updateTrackLayers(
    style: Style,
    segments: List<TrackSegment>,
    points: List<AcceptedGpsPoint>,
) {
    val trackSource = style.getSourceAs<GeoJsonSource>(TRACK_SOURCE_ID) ?: return
    val startSource = style.getSourceAs<GeoJsonSource>(START_SOURCE_ID) ?: return
    val finishSource = style.getSourceAs<GeoJsonSource>(FINISH_SOURCE_ID) ?: return

    val drawableSegments = segments.filter { it.points.size >= 2 }
    if (drawableSegments.isEmpty()) {
        trackSource.setGeoJson(FeatureCollection.fromFeatures(emptyList()))
    } else {
        val features = drawableSegments.map { segment ->
            val coordinates = segment.points.map { Point.fromLngLat(it.longitude, it.latitude) }
            Feature.fromGeometry(LineString.fromLngLats(coordinates))
        }
        trackSource.setGeoJson(FeatureCollection.fromFeatures(features))
    }

    val first = points.firstOrNull()
    val last = points.lastOrNull()
    if (first != null) {
        startSource.setGeoJson(
            Feature.fromGeometry(Point.fromLngLat(first.longitude, first.latitude)),
        )
    } else {
        startSource.setGeoJson(FeatureCollection.fromFeatures(emptyList()))
    }
    if (last != null && points.size > 1) {
        finishSource.setGeoJson(
            Feature.fromGeometry(Point.fromLngLat(last.longitude, last.latitude)),
        )
    } else {
        finishSource.setGeoJson(FeatureCollection.fromFeatures(emptyList()))
    }
}

private fun fitCameraToRoute(map: MapLibreMap, points: List<AcceptedGpsPoint>) {
    if (points.size == 1) {
        val point = points.first()
        map.moveCamera(
            CameraUpdateFactory.newLatLngZoom(
                LatLng(point.latitude, point.longitude),
                SINGLE_POINT_ZOOM,
            ),
        )
        return
    }
    val builder = LatLngBounds.Builder()
    points.forEach { point ->
        builder.include(LatLng(point.latitude, point.longitude))
    }
    val bounds = builder.build()
    map.moveCamera(
        CameraUpdateFactory.newLatLngBounds(bounds, BOUNDS_PADDING_PX),
    )
}
