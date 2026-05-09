package com.beacon.tracker.repository

import android.util.Log
import com.beacon.shared.constants.FirebaseCollections
import com.beacon.shared.constants.RealtimeDBPaths
import com.beacon.shared.models.Location
import com.beacon.tracker.auth.DeviceAuthManager
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.database.FirebaseDatabase
import kotlinx.coroutines.tasks.await
import java.util.Date

class FirebaseTrackerRepository(
    private val firestore: FirebaseFirestore,
    private val realtimeDb: FirebaseDatabase,
    private val deviceAuthManager: DeviceAuthManager
) {
    private val tag = "FirebaseTrackerRepository"

    /**
     * Upload location data to Firestore location_history subcollection
     */
    suspend fun uploadLocationToHistory(location: Location): Result<Unit> {
        return try {
            val deviceId = deviceAuthManager.getDeviceId() ?: return Result.failure(
            	Exception("Device ID is null")
            )
            val locationMap: Map<String, Any> = mapOf(
                "timestamp" to location.timestamp,
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

            // Use timestamp as document ID for sorting/querying
            val timestampMs = location.timestamp.time
            firestore.collection(FirebaseCollections.DEVICES)
                .document(deviceId)
                .collection(FirebaseCollections.LOCATION_HISTORY)
                .document(timestampMs.toString())
                .set(locationMap)
                .await()

            Log.d(tag, "Location uploaded: $deviceId at $timestampMs")
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(tag, "Failed to upload location", e)
            Result.failure(e)
        }
    }

    /**
     * Update device's last_location and status in main device document
     */
    suspend fun updateDeviceStatus(
        latitude: Double,
        longitude: Double,
        accuracy: Float,
        batteryLevel: Int,
        signalStrength: Int,
        deviceMotionStatus: String
    ): Result<Unit> {
        return try {
            val deviceId = deviceAuthManager.getDeviceId() ?: return Result.failure(
            	Exception("Device ID is null")
            )
            val now = Date()

            val updateMap: Map<String, Any> = mapOf(
                "last_location" to mapOf(
                    "latitude" to latitude,
                    "longitude" to longitude,
                    "accuracy" to accuracy,
                    "timestamp" to now,
                    "provider" to "gps"
                ),
                "battery_level" to batteryLevel,
                "signal_strength" to signalStrength,
                "device_motion_status" to deviceMotionStatus,
                "last_seen" to now,
                "status" to "online"
            )

            firestore.collection(FirebaseCollections.DEVICES)
                .document(deviceId)
                .update(updateMap)
                .await()

            Log.d(tag, "Device status updated: $deviceId")
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(tag, "Failed to update device status", e)
            Result.failure(e)
        }
    }

    /**
     * Update live location in Realtime Database for real-time admin updates
     */
    suspend fun updateLiveLocation(
        latitude: Double,
        longitude: Double,
        accuracy: Float,
        batteryLevel: Int,
        signalStrength: Int,
        deviceMotionStatus: String
    ): Result<Unit> {
        return try {
            val deviceId = deviceAuthManager.getDeviceId() ?: return Result.failure(
            	Exception("Device ID is null")
            )
            val liveLocationData = mapOf(
                "latitude" to latitude,
                "longitude" to longitude,
                "accuracy" to accuracy,
                "battery_level" to batteryLevel,
                "signal_strength" to signalStrength,
                "status" to "online",
                "last_update_timestamp" to System.currentTimeMillis(),
                "device_motion_status" to deviceMotionStatus
            )

            realtimeDb.reference
                .child(RealtimeDBPaths.LIVE_LOCATIONS)
                .child(deviceId)
                .setValue(liveLocationData)
                .await()

            Log.d(tag, "Live location updated in RTDB: $deviceId")
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(tag, "Failed to update live location", e)
            Result.failure(e)
        }
    }

    /**
     * Fetch device settings from Firestore
     */
    suspend fun getDeviceSettings(): Result<Map<String, Any?>> {
        return try {
            val deviceId = deviceAuthManager.getDeviceId() ?: return Result.failure(
            	Exception("Device ID is null")
            )
            val snapshot = firestore.collection(FirebaseCollections.DEVICES)
                .document(deviceId)
                .get()
                .await()

            if (snapshot.exists()) {
                Result.success(snapshot.data ?: emptyMap())
            } else {
                Result.failure(Exception("Device not found"))
            }
        } catch (e: Exception) {
            Log.e(tag, "Failed to fetch device settings", e)
            Result.failure(e)
        }
    }

    /**
     * Check if device is still authorized (exists in Firebase)
     */
    suspend fun isDeviceAuthorized(): Result<Boolean> {
        return try {
            val deviceId = deviceAuthManager.getDeviceId() ?: return Result.failure(
            	Exception("Device ID is null")
            )
            val snapshot = firestore.collection(FirebaseCollections.DEVICES)
                .document(deviceId)
                .get()
                .await()

            Result.success(snapshot.exists())
        } catch (e: Exception) {
            Log.e(tag, "Failed to check device authorization", e)
            Result.failure(Exception("Device not authorized"))
        }
    }

    /**
     * Update tracking pause state
     */
    suspend fun updateTrackingPauseState(paused: Boolean): Result<Unit> {
        return try {
            val deviceId = deviceAuthManager.getDeviceId() ?: return Result.failure(
            	Exception("Device ID is null")
            )
            firestore.collection(FirebaseCollections.DEVICES)
                .document(deviceId)
                .update("tracking_enabled",!paused)
                .await()

            Log.d(tag, "Tracking pause state updated: paused=$paused")
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(tag, "Failed to update tracking pause state", e)
            Result.failure(e)
        }
    }
}
