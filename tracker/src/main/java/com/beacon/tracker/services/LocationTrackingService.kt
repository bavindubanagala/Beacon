package com.beacon.tracker.services

import android.app.Service
import android.content.Intent
import android.location.Location
import android.os.IBinder
import android.util.Log
import com.beacon.shared.models.Location as BeaconLocation
import com.beacon.tracker.auth.DeviceAuthManager
import com.beacon.tracker.repository.FirebaseTrackerRepository
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.*

class LocationTrackingService : Service() {

    private lateinit var deviceAuthManager: DeviceAuthManager
    private lateinit var repository: FirebaseTrackerRepository

    private val serviceScope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    private var lastBatteryLevel = 0
    private var lastSignalStrength = 0

    companion object {
        private const val TAG = "LocationTrackingService"
    }

    override fun onCreate() {
        super.onCreate()

        deviceAuthManager = DeviceAuthManager(this)

        repository = FirebaseTrackerRepository(
            FirebaseFirestore.getInstance(),
            FirebaseDatabase.getInstance(),
            deviceAuthManager
        )
    }

    private fun handleLocationUpdate(location: Location) {

        val deviceId = deviceAuthManager.getDeviceId()

        Log.d(TAG, "Location received: ${location.latitude}, ${location.longitude}")

        serviceScope.launch {

            val locationData = BeaconLocation(
                deviceId = deviceId,
                timestamp = System.currentTimeMillis(), // ✅ OPTION A FIX
                latitude = location.latitude,
                longitude = location.longitude,
                accuracy = location.accuracy,
                provider = location.provider ?: "gps",
                speed = location.speed,
                heading = location.bearing,
                batteryLevel = lastBatteryLevel,
                signalStrength = lastSignalStrength,
                deviceMotionStatus = "idle"
            )

            repository.uploadLocationToHistory(locationData)
        }
    }

    override fun onDestroy() {
        serviceScope.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
