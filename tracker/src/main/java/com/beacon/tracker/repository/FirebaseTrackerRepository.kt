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

            firestore.collection(FirebaseCollections.DEVICES)
                .document(deviceId)
                .collection(FirebaseCollections.LOCATION_HISTORY)
                .document(location.timestamp.toString())
                .set(location.toMap())
                .await()

            Log.d(tag, "Location history uploaded successfully: $deviceId")
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(tag, "Upload to history failed", e)
            Result.failure(e)
        }
    }

    suspend fun updateDeviceStatus(
        latitude: Double,
        longitude: Double,
        accuracy: Float,
        batteryLevel: Int,
        signalStrength: Int,
        deviceMotionStatus: String
    ): Result<Unit> {
        return try {
            val deviceId = deviceAuthManager.getDeviceId()
                ?: return Result.failure(Exception("Device ID is null"))

            val statusUpdate = mapOf(
                "last_location" to mapOf(
                    "latitude" to latitude,
                    "longitude" to longitude,
                    "accuracy" to accuracy,
                    "timestamp" to System.currentTimeMillis()
                ),
                "batteryLevel" to batteryLevel,
                "battery_level" to batteryLevel,
                "signal_strength" to signalStrength,
                "device_motion_status" to deviceMotionStatus,
                "last_seen" to System.currentTimeMillis(),
                "status" to "online"
            )

            firestore.collection(FirebaseCollections.DEVICES)
                .document(deviceId)
                .update(statusUpdate)
                .await()

            Log.d(tag, "Device status updated successfully: $deviceId")
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(tag, "Update device status failed", e)
            Result.failure(e)
        }
    }

    suspend fun updateLiveLocation(
        latitude: Double,
        longitude: Double,
        accuracy: Float,
        batteryLevel: Int,
        signalStrength: Int,
        deviceMotionStatus: String
    ): Result<Unit> {
        return try {
            val deviceId = deviceAuthManager.getDeviceId()
                ?: return Result.failure(Exception("Device ID is null"))

            val liveData = mapOf(
                "latitude" to latitude,
                "longitude" to longitude,
                "accuracy" to accuracy,
                "battery_level" to batteryLevel,
                "signal_strength" to signalStrength,
                "device_motion_status" to deviceMotionStatus,
                "last_update_timestamp" to System.currentTimeMillis(),
                "status" to "online"
            )

            realtimeDb.getReference(RealtimeDBPaths.LIVE_LOCATIONS)
                .child(deviceId)
                .setValue(liveData)
                .await()

            Log.d(tag, "Live location updated successfully: $deviceId")
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(tag, "Update live location failed", e)
            Result.failure(e)
        }
    }
}
