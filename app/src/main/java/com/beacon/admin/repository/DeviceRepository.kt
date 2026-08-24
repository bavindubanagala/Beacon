package com.beacon.admin.repository

import com.beacon.shared.mapper.toDevice
import com.beacon.shared.models.Device
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Filter
import kotlinx.coroutines.tasks.await

class DeviceRepository(
    private val firestore: FirebaseFirestore
) {
    private val collection = firestore.collection("devices")

    suspend fun getAllDevices(ownerId: String): Result<List<Device>> {
        return try {
            val snapshot = collection
                .where(Filter.or(
                    Filter.equalTo("ownerId", ownerId),
                    Filter.equalTo("owner_id", ownerId)
                ))
                .get()
                .await()
            val devices = snapshot.documents.mapNotNull { it.toDevice() }
            Result.success(devices)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun pairDevice(code: String, friendlyName: String, ownerId: String): Result<Unit> {
        return try {
            // 1. Find the pairing code
            val pairingDoc = firestore.collection("pairing_codes")
                .document(code)
                .get()
                .await()

            if (!pairingDoc.exists()) {
                return Result.failure(Exception("Invalid pairing code"))
            }

            val deviceId = pairingDoc.getString("deviceId") 
                ?: return Result.failure(Exception("Invalid code data"))
            val trackerAuthUid = pairingDoc.getString("trackerAuthUid")

            // 2. Update the device document (use set with merge in case it doesn't exist yet)
            val updates = mapOf(
                "is_paired" to true,
                "deviceId" to deviceId,
                "device_id" to deviceId,
                "ownerId" to ownerId,
                "owner_id" to ownerId,
                "trackerAuthUid" to trackerAuthUid,
                "deviceName" to friendlyName.ifEmpty { "New Device" },
                "device_name" to friendlyName.ifEmpty { "New Device" },
                "status" to "online"
            )

            firestore.collection("devices")
                .document(deviceId)
                .set(updates, com.google.firebase.firestore.SetOptions.merge())
                .await()

            // 3. Cleanup pairing code
            firestore.collection("pairing_codes")
                .document(code)
                .delete()
                .await()

            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun updateDeviceSettings(
        deviceId: String,
        mode: String,
        intervalSeconds: Int,
        autoRevertSeconds: Int,
        isEmergency: Boolean,
        batterySavingEnabled: Boolean,
        stationaryIntervalMinutes: Int,
        lowBatteryPercent: Int,
        offlineThresholdMinutes: Int,
        sosFallbackPhone: String
    ): Result<Unit> {
        return try {
            val updates = mapOf(
                "command_mode" to mode,
                "commandMode" to mode,
                "interval_seconds" to intervalSeconds,
                "intervalSeconds" to intervalSeconds,
                "auto_revert_seconds" to autoRevertSeconds,
                "autoRevertSeconds" to autoRevertSeconds,
                "is_emergency_mode" to isEmergency,
                "isEmergencyMode" to isEmergency,
                "battery_saving_enabled" to batterySavingEnabled,
                "batterySavingEnabled" to batterySavingEnabled,
                "stationary_interval_minutes" to stationaryIntervalMinutes,
                "stationaryIntervalMinutes" to stationaryIntervalMinutes,
                "alertThresholds.lowBatteryPercent" to lowBatteryPercent,
                "alertThresholds.offlineThresholdMinutes" to offlineThresholdMinutes,
                "sosFallbackPhone" to sosFallbackPhone,
                "command_timestamp" to System.currentTimeMillis(),
                "commandTimestamp" to System.currentTimeMillis()
            )
            firestore.collection("devices").document(deviceId).update(updates).await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun removeDevice(deviceId: String): Result<Unit> {
        return try {
            firestore.collection("devices").document(deviceId).delete().await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun renameDevice(deviceId: String, newName: String): Result<Unit> {
        return try {
            val updates = mapOf(
                "deviceName" to newName,
                "device_name" to newName
            )
            firestore.collection("devices").document(deviceId).update(updates).await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun cleanupInactiveDevices(ownerId: String): Result<Int> {
        return try {
            val snapshot = collection
                .where(Filter.or(
                    Filter.equalTo("ownerId", ownerId),
                    Filter.equalTo("owner_id", ownerId)
                ))
                .get()
                .await()
            
            val now = System.currentTimeMillis()
            val thirtyDaysMs = 30L * 24 * 60 * 60 * 1000L
            var count = 0
            
            for (doc in snapshot.documents) {
                val lastSeen = doc.getLong("last_seen") ?: doc.getLong("lastSeen") ?: 0L
                if (now - lastSeen > thirtyDaysMs) {
                    doc.reference.delete().await()
                    count++
                }
            }
            Result.success(count)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun getDevicesListener(
        ownerId: String,
        onUpdate: (List<Device>) -> Unit,
        onError: (Exception) -> Unit
    ): com.google.firebase.firestore.ListenerRegistration {
        return collection
            .where(Filter.or(
                Filter.equalTo("ownerId", ownerId),
                Filter.equalTo("owner_id", ownerId)
            ))
            .addSnapshotListener { snapshot, e ->
                if (e != null) {
                    onError(e)
                    return@addSnapshotListener
                }
                val devices = snapshot?.documents?.mapNotNull { it.toDevice() } ?: emptyList()
                onUpdate(devices)
            }
    }
}
