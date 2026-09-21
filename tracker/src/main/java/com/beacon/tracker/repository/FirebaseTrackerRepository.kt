package com.beacon.tracker.repository

import android.util.Log
import com.beacon.shared.constants.FirebaseCollections
import com.beacon.shared.constants.RealtimeDBPaths
import com.beacon.shared.models.Location
import com.beacon.tracker.auth.DeviceAuthManager
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.tasks.await
import java.util.UUID

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
                "latitude" to latitude,
                "longitude" to longitude,
                "accuracy" to accuracy,
                "batteryLevel" to batteryLevel,
                "signal_strength" to signalStrength,
                "device_motion_status" to deviceMotionStatus,
                "last_seen" to System.currentTimeMillis(),
                "status" to "online"
            )

            firestore.collection(FirebaseCollections.DEVICES)
                .document(deviceId)
                .set(statusUpdate, SetOptions.merge())
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

    suspend fun triggerSos(deviceId: String, isEmergency: Boolean): Result<Unit> {
        return try {
            val timestamp = System.currentTimeMillis()
            val updates = mutableMapOf<String, Any>(
                "sosActive" to isEmergency,
                "sosTimestamp" to timestamp,
                "isEmergencyMode" to isEmergency,
                "is_emergency_mode" to isEmergency,
                "commandTimestamp" to timestamp,
                "command_timestamp" to timestamp
            )

            if (isEmergency) {
                updates["commandMode"] = "live"
                updates["command_mode"] = "live"
            }

            firestore.collection(FirebaseCollections.DEVICES)
                .document(deviceId)
                .update(updates)
                .await()

            if (isEmergency) {
                val alertId = UUID.randomUUID().toString()
                val alert = mapOf(
                    "id" to alertId,
                    "alert_type" to "SOS_ACTIVE",
                    "device_id" to deviceId,
                    "alert_severity" to "CRITICAL",
                    "message" to "SOS EMERGENCY ACTIVATED",
                    "created_at" to timestamp
                )
                firestore.collection(FirebaseCollections.DEVICES)
                    .document(deviceId)
                    .collection(FirebaseCollections.ALERTS)
                    .document(alertId)
                    .set(alert)
                    .await()
            }
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(tag, "SOS trigger failed", e)
            Result.failure(e)
        }
    }

    suspend fun pairDevice(pairingCode: String): Result<Unit> {
        return try {
            val deviceId = deviceAuthManager.getDeviceId()
                ?: return Result.failure(Exception("Device ID not found"))

            val trackerAuthUid = FirebaseAuth.getInstance().currentUser?.uid ?: ""
            val expiresAt = System.currentTimeMillis() + 15 * 60 * 1000L

            // 1. Reset device pairing status
            val deviceUpdates = mapOf(
                "is_paired" to false,
                "trackerAuthUid" to trackerAuthUid
            )
            firestore.collection(FirebaseCollections.DEVICES)
                .document(deviceId)
                .set(deviceUpdates, SetOptions.merge())
                .await()

            // 2. Create pairing code entry
            val pairingData = mapOf(
                "code" to pairingCode,
                "deviceId" to deviceId,
                "trackerAuthUid" to trackerAuthUid,
                "createdAt" to System.currentTimeMillis(),
                "expiresAt" to expiresAt
            )
            firestore.collection("pairing_codes")
                .document(pairingCode)
                .set(pairingData)
                .await()

            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(tag, "Pairing registration failed", e)
            Result.failure(e)
        }
    }
}
