package com.astraf.hrgpslogger

import android.annotation.SuppressLint
import android.content.Context
import android.os.Looper
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class LocationTracker(context: Context) {

    private val fusedClient = LocationServices.getFusedLocationProviderClient(context)

    private val _location = MutableStateFlow<LocationSample?>(null)
    val location: StateFlow<LocationSample?> = _location.asStateFlow()

    private val _statusMessage = MutableStateFlow("GPS не запущен")
    val statusMessage: StateFlow<String> = _statusMessage.asStateFlow()

    private var isTracking = false

    private val locationRequest = LocationRequest.Builder(
        Priority.PRIORITY_HIGH_ACCURACY,
        UPDATE_INTERVAL_MS,
    )
        .setMinUpdateIntervalMillis(FASTEST_INTERVAL_MS)
        .setMinUpdateDistanceMeters(MIN_DISTANCE_METERS)
        .build()

    private val callback = object : LocationCallback() {
        override fun onLocationResult(result: LocationResult) {
            val location = result.lastLocation ?: return
            val sample = LocationSample(
                latitude = location.latitude,
                longitude = location.longitude,
                timestampMillis = location.time,
                accuracyMeters = location.accuracy.takeIf { location.hasAccuracy() },
                altitudeMeters = location.altitude.takeIf { location.hasAltitude() },
                speedMps = location.speed.takeIf { location.hasSpeed() },
                bearingDegrees = location.bearing.takeIf { location.hasBearing() },
            )
            _location.value = sample
            val accuracyText = sample.accuracyMeters?.let { " ±${"%.0f".format(it)}м" }.orEmpty()
            _statusMessage.value =
                "GPS: ${"%.5f".format(location.latitude)}, ${"%.5f".format(location.longitude)}$accuracyText"
        }
    }

    @SuppressLint("MissingPermission")
    fun start() {
        if (isTracking) return
        isTracking = true
        _statusMessage.value = "Запуск GPS..."
        fusedClient.requestLocationUpdates(
            locationRequest,
            callback,
            Looper.getMainLooper(),
        )
    }

    fun stop() {
        if (!isTracking) return
        fusedClient.removeLocationUpdates(callback)
        isTracking = false
        _statusMessage.value = "GPS остановлен"
    }

    fun release() {
        stop()
    }

    companion object {
        private const val UPDATE_INTERVAL_MS = 2_000L
        private const val FASTEST_INTERVAL_MS = 1_000L
        private const val MIN_DISTANCE_METERS = 1f
    }
}
