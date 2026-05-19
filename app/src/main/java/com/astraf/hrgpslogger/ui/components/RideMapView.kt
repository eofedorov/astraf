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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.astraf.hrgpslogger.GeoPoint
import com.astraf.hrgpslogger.LocationSample
import com.astraf.hrgpslogger.R
import org.maplibre.geojson.Feature
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
    trackPoints: StateFlow<List<GeoPoint>>,
    isMapActive: Boolean,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val locationSample by location.collectAsStateWithLifecycle()
    val track by trackPoints.collectAsStateWithLifecycle()

    var mapLibreMap by remember { mutableStateOf<MapLibreMap?>(null) }
    var style by remember { mutableStateOf<Style?>(null) }
    var userHasPanned by remember { mutableStateOf(false) }
    var didInitialCenter by remember { mutableStateOf(false) }
    var isDisposed by remember { mutableStateOf(false) }

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

    LaunchedEffect(style, locationSample, track) {
        if (isDisposed) return@LaunchedEffect
        val currentStyle = style ?: return@LaunchedEffect
        updateTrackLayer(currentStyle, track)
        updateLocationLayer(currentStyle, locationSample)
        val map = mapLibreMap ?: return@LaunchedEffect
        val sample = locationSample
        if (sample != null && !didInitialCenter && !userHasPanned) {
            map.animateCamera(
                CameraUpdateFactory.newLatLngZoom(
                    LatLng(sample.latitude, sample.longitude),
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
                val sample = locationSample ?: return@FloatingActionButton
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
                .padding(12.dp),
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

private fun updateTrackLayer(style: Style, track: List<GeoPoint>) {
    val source = style.getSourceAs<GeoJsonSource>(TRACK_SOURCE_ID) ?: return
    if (track.size < 2) {
        source.setGeoJson("{\"type\":\"FeatureCollection\",\"features\":[]}")
        return
    }
    val coordinates = track.map { Point.fromLngLat(it.longitude, it.latitude) }
    source.setGeoJson(Feature.fromGeometry(LineString.fromLngLats(coordinates)))
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
