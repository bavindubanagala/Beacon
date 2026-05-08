package com.beacon.admin.repository

import android.util.Log
import com.beacon.shared.constants.FirebaseCollections
import com.beacon.shared.models.Device
import com.beacon.shared.models.Group
import com.beacon.shared.models.AdminSettings
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.database.FirebaseDatabase
import kotlinx.coroutines.tasks.await
import java.util.Date
import java.util.UUID

class DeviceRepository(
    private val firestore: FirebaseFirestore,
    private val realtimeDb: FirebaseDatabase,
    private val auth: FirebaseAuth
) {
    private val tag = "DeviceRepository"

    /**
     * Add a new device (pair a tracker device)
     */
    suspend fun addDevice(
        deviceId: String,
        iconId: String,
        groupId: String = "default"
    ): Result<Device> {
        return try {
            val device = Device(
                deviceId = deviceId,
                groupId = groupId,
                iconId = iconId,
                createdAt = Date(),
                trackingEnabled = true,
                trackingInterval = 60,
                status = com.beacon.shared.models.DeviceStatus.ONLINE
            )

            firestore.collection(FirebaseCollections.DEVICES)
                .document(deviceId)
                .set(device.toMap())
                .await()

            Log.d(tag, "Device added: $deviceId")
            Result.success(device)
        } catch (e: Exception) {
            Log.e(tag, "Failed to add device", e)
            Result.failure(e)
        }
    }

    /**
     * Get all devices
     */
    suspend fun getAllDevices(): Result<List<Device>> {
        return try {
            val snapshot = firestore.collection(FirebaseCollections.DEVICES)
                .get()
                .await()

            val devices = snapshot.documents.mapNotNull { doc ->
                try {
                    Device.fromMap(doc.data ?: emptyMap())
                } catch (e: Exception) {
                    Log.e(tag, "Failed to parse device: ${doc.id}", e)
                    null
                }
            }

            Log.d(tag, "Fetched ${devices.size} devices")
            Result.success(devices)
        } catch (e: Exception) {
            Log.e(tag, "Failed to fetch devices", e)
            Result.failure(e)
        }
    }

    /**
     * Get a specific device
     */
    suspend fun getDevice(deviceId: String): Result<Device> {
        return try {
            val snapshot = firestore.collection(FirebaseCollections.DEVICES)
                .document(deviceId)
                .get()
                .await()

            if (snapshot.exists()) {
                val device = Device.fromMap(snapshot.data ?: emptyMap())
                Result.success(device)
            } else {
                Result.failure(Exception("Device not found"))
            }
        } catch (e: Exception) {
            Log.e(tag, "Failed to fetch device", e)
            Result.failure(e)
        }
    }

    /**
     * Remove a device
     */
    suspend fun removeDevice(deviceId: String): Result<Unit> {
        return try {
            firestore.collection(FirebaseCollections.DEVICES)
                .document(deviceId)
                .delete()
                .await()

            Log.d(tag, "Device removed: $deviceId")
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(tag, "Failed to remove device", e)
            Result.failure(e)
        }
    }

    /**
     * Update device settings
     */
    suspend fun updateDeviceSettings(
        deviceId: String,
        updates: Map<String, Any?>
    ): Result<Unit> {
        return try {
            firestore.collection(FirebaseCollections.DEVICES)
                .document(deviceId)
                .update(updates)
                .await()

            Log.d(tag, "Device settings updated: $deviceId")
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(tag, "Failed to update device settings", e)
            Result.failure(e)
        }
    }

    /**
     * Pause/resume tracking for a device
     */
    suspend fun setTrackingEnabled(deviceId: String, enabled: Boolean): Result<Unit> {
        return updateDeviceSettings(deviceId, mapOf("tracking_enabled" to enabled))
    }

    /**
     * Update tracking interval for a device
     */
    suspend fun setTrackingInterval(deviceId: String, intervalSeconds: Int): Result<Unit> {
        return updateDeviceSettings(deviceId, mapOf("tracking_interval" to intervalSeconds))
    }
}
