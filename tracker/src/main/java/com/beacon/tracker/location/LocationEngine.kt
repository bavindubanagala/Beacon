package com.beacon.tracker.location

import android.annotation.SuppressLint
import android.location.Location
import android.os.Looper
import com.google.android.gms.location.*
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class LocationEngine @Inject constructor(
    private val fusedLocationClient: FusedLocationProviderClient
) {
    @SuppressLint("MissingPermission")
    fun getLocationUpdates(configFlow: Flow<LocationRequestConfig>): Flow<Location> {
        return configFlow
            .distinctUntilChanged()
            .flatMapLatest { config ->
                callbackFlow {
                    val locationRequest = LocationRequest.Builder(config.priority, config.intervalMillis)
                        .setMinUpdateIntervalMillis(config.intervalMillis / 2)
                        .setMinUpdateDistanceMeters(config.minUpdateDistanceMeters)
                        .build()

                    val callback = object : LocationCallback() {
                        override fun onLocationResult(result: LocationResult) {
                            result.lastLocation?.let { trySend(it) }
                        }
                    }

                    fusedLocationClient.requestLocationUpdates(
                        locationRequest,
                        callback,
                        Looper.getMainLooper()
                    )

                    awaitClose {
                        fusedLocationClient.removeLocationUpdates(callback)
                    }
                }
            }
    }
}
