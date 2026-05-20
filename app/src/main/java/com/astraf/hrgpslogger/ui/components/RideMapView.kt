package com.astraf.hrgpslogger.ui.components

import android.os.Bundle
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.astraf.hrgpslogger.AcceptedGpsPoint
import com.astraf.hrgpslogger.LocationSample
import com.astraf.hrgpslogger.R
import com.astraf.hrgpslogger.TrackSegment
import org.maplibre.geojson.Feature
import org.maplibre.geojson.FeatureCollection
import org.maplibre.geojson.LineString
import org.maplibre.geojson.Point
import kotlinx.coroutines.flow.StateFlow
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng
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
private const val TRACK_SOURCE_ID = "ride-track"
private const val TRACK_LAYER_ID = "ride-track-line"
private const val LOCATION_SOURCE_ID = "ride-location"
private const val LOCATION_LAYER_ID = "ride-location-dot"
private const val DEFAULT_LAT = 55.751244
private const val DEFAULT_LON = 37.618423
private const val DEFAULT_ZOOM = 12.0
private const val RECENTER_ZOOM = 16.5

@Composable
fun RideMapView(
    location: StateFlow<LocationSample?>,
    acceptedPoint: StateFlow<AcceptedGpsPoint?>,
    trackSegments: StateFlow<List<TrackSegment>>,
    isMapActive: Boolean,
    modifier: Modifier = Modifier,
    controlsBottomPadding: Dp = 12.dp,
) {
    val lifecycleOwner = LocalLifecycleOwner.current
    val locationSample by location.collectAsStateWithLifecycle()
    val accepted by acceptedPoint.collectAsStateWithLifecycle()
    val segments by trackSegments.collectAsStateWithLifecycle()

    var mapLibreMap by remember { mutableStateOf<MapLibreMap?>(null) }
    var style by remember { mutableStateOf<Style?>(null) }
    var userHasPanned by remember { mutableStateOf(false) }
    var didInitialCenter by remember { mutableStateOf(false) }
    var isDisposed by remember { mutableStateOf(false) }

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
        } else if (!isMapActive || state.isAtLeast(Lifecycle.State.CREATED)) {
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
        if (isMapActive) {
            if (lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) {
                mapView.onResume()
            }
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
            map.addOnCameraMoveStartedListener { reason ->
                if (reason == MapLibreMap.OnCameraMoveStartedListener.REASON_API_GESTURE) {
                    userHasPanned = true
                }
            }
            mapLibreMap = map
            map.setStyle(OSM_STYLE_URI) { loadedStyle ->
                if (isDisposed) return@setStyle
                style = loadedStyle
                setupOverlayLayers(loadedStyle)
                loadedStyle.getSourceAs<RasterSource>(OSM_SOURCE_ID)?.setVolatile(false)
                map.cameraPosition = org.maplibre.android.camera.CameraPosition.Builder()
                    .target(LatLng(DEFAULT_LAT, DEFAULT_LON))
                    .zoom(DEFAULT_ZOOM)
                    .build()
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

    LaunchedEffect(style, locationSample, accepted, segments) {
        if (isDisposed) return@LaunchedEffect
        val currentStyle = style ?: return@LaunchedEffect
        updateTrackLayer(currentStyle, segments)
        val positionSample = accepted?.let {
            LocationSample(
                latitude = it.latitude,
                longitude = it.longitude,
                timestampMillis = it.timestampMillis,
                accuracyMeters = it.accuracyMeters,
            )
        } ?: locationSample
        updateLocationLayer(currentStyle, positionSample)
        val map = mapLibreMap ?: return@LaunchedEffect
        if (positionSample != null && !didInitialCenter && !userHasPanned) {
            map.animateCamera(
                CameraUpdateFactory.newLatLngZoom(
                    LatLng(positionSample.latitude, positionSample.longitude),
                    RECENTER_ZOOM,
                ),
            )
            didInitialCenter = true
        }
    }

    Box(modifier = modifier) {
        AndroidView(
            factory = { mapView },
            modifier = Modifier.fillMaxSize(),
        )

        FloatingActionButton(
            onClick = {
                val sample = accepted?.let {
                    LocationSample(it.latitude, it.longitude, it.timestampMillis, it.accuracyMeters)
                } ?: locationSample ?: return@FloatingActionButton
                mapLibreMap?.animateCamera(
                    CameraUpdateFactory.newLatLngZoom(
                        LatLng(sample.latitude, sample.longitude),
                        RECENTER_ZOOM,
                    ),
                )
                userHasPanned = false
            },
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(end = 12.dp, bottom = controlsBottomPadding),
        ) {
            Icon(
                imageVector = Icons.Default.MyLocation,
                contentDescription = stringResource(R.string.map_recenter),
            )
        }
    }
}

private fun setupOverlayLayers(style: Style) {
    if (style.getSource(TRACK_SOURCE_ID) == null) {
        style.addSource(GeoJsonSource(TRACK_SOURCE_ID))
        style.addLayer(
            LineLayer(TRACK_LAYER_ID, TRACK_SOURCE_ID).withProperties(
                lineColor("#2196F3"),
                lineWidth(4f),
            ),
        )
    }
    if (style.getSource(LOCATION_SOURCE_ID) == null) {
        style.addSource(GeoJsonSource(LOCATION_SOURCE_ID))
        style.addLayer(
            CircleLayer(LOCATION_LAYER_ID, LOCATION_SOURCE_ID).withProperties(
                circleRadius(8f),
                circleColor("#F44336"),
                circleStrokeWidth(2f),
                circleStrokeColor("#FFFFFF"),
            ),
        )
    }
}

private fun updateTrackLayer(style: Style, segments: List<TrackSegment>) {
    val source = style.getSourceAs<GeoJsonSource>(TRACK_SOURCE_ID) ?: return
    val drawableSegments = segments.filter { it.points.size >= 2 }
    if (drawableSegments.isEmpty()) {
        source.setGeoJson(FeatureCollection.fromFeatures(emptyList()))
        return
    }
    val features = drawableSegments.map { segment ->
        val coordinates = segment.points.map { Point.fromLngLat(it.longitude, it.latitude) }
        Feature.fromGeometry(LineString.fromLngLats(coordinates))
    }
    source.setGeoJson(FeatureCollection.fromFeatures(features))
}

private fun updateLocationLayer(style: Style, location: LocationSample?) {
    val source = style.getSourceAs<GeoJsonSource>(LOCATION_SOURCE_ID) ?: return
    if (location == null) {
        source.setGeoJson(Feature.fromGeometry(Point.fromLngLat(0.0, 0.0)))
        return
    }
    source.setGeoJson(
        Feature.fromGeometry(
            Point.fromLngLat(location.longitude, location.latitude),
        ),
    )
}
