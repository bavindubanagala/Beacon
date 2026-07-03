package com.beacon.tracker.repository

import android.util.Log
import com.beacon.shared.constants.FirebaseCollections
import com.beacon.shared.constants.RealtimeDBPaths
import com.beacon.shared.models.Location
import com.beacon.tracker.auth.DeviceAuthManager
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await

class FirebaseTrackerRepository(
    private val firestore: FirebaseFirestore,
    private val realtimeDb: FirebaseDatabase,
    private val deviceAuthManager: DeviceAuthManager
) {
    private val tag = "FirebaseTrackerRepository"

    suspend fun uploadLocationToHistory(location: Location): Result<Unit> {
        return try {

            val deviceId = deviceAuthManager.getDeviceId()
                ?: return Result.failure(Exception("Device ID is null"))

            val locationMap = mapOf(
                "timestamp" to location.timestamp, // ✅ LONG ONLY
                "latitude" to location.latitude,
                "longitude" to location.longitude,
                "accuracy" to location.accuracy,
                "provider" to location.provider,
                "speed" to location.speed,
                "heading" to location.heading,
                "battery_level" to location.batteryLevel,
                "signal_strength" to location.signalStrength,
                "device_motion_status" to location.deviceMotionStatus
            )

            firestore.collection(FirebaseCollections.DEVICES)
                .document(deviceId)
                .collection(FirebaseCollections.LOCATION_HISTORY)
                .document(location.timestamp.toString())
                .set(locationMap)
                .await()

            Log.d(tag, "Location uploaded successfully: $deviceId")
            Result.success(Unit)

        } catch (e: Exception) {
            Log.e(tag, "Upload failed", e)
            Result.failure(e)
        }
    }
}
