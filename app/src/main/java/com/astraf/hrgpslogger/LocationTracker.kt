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

data class LocationSample(
    val latitude: Double,
    val longitude: Double,
    val accuracyMeters: Float,
    val timestampMillis: Long,
)

class LocationTracker(context: Context) {

    private val fusedClient = LocationServices.getFusedLocationProviderClient(context)
    private val speedCalculator = SpeedCalculator()

    private val _location = MutableStateFlow<LocationSample?>(null)
    val location: StateFlow<LocationSample?> = _location.asStateFlow()

    val speedKmh: StateFlow<Float?> = speedCalculator.speedKmh

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
                accuracyMeters = location.accuracy,
                timestampMillis = location.time,
            )
            val gpsSpeedMps = if (location.hasSpeed()) location.speed else null
            speedCalculator.update(sample, gpsSpeedMps)
            _location.value = sample
            _statusMessage.value = "GPS: ${"%.5f".format(location.latitude)}, ${"%.5f".format(location.longitude)}"
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
        speedCalculator.reset()
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
