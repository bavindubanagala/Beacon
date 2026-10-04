package com.beacon.data.repository

import android.util.Log
import com.beacon.data.auth.AuthManager
import com.beacon.shared.mapper.toDevice
import com.beacon.shared.models.Device
import com.beacon.shared.models.ModeHistoryEntry
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Filter
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

interface DeviceRepository {
    val devices: Flow<List<Device>>
    fun getDevicesStream(ownerId: String): Flow<List<Device>>
    suspend fun getAllDevices(ownerId: String): Result<List<Device>>
    suspend fun pairDevice(code: String, friendlyName: String, ownerId: String): Result<Unit>
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
    ): Result<Unit>
    suspend fun removeDevice(deviceId: String): Result<Unit>
    suspend fun renameDevice(deviceId: String, newName: String): Result<Unit>
    suspend fun cleanupInactiveDevices(ownerId: String): Result<Int>
    suspend fun requestManualPing(deviceId: String): Result<Long>
    suspend fun updateAlertThresholds(
        deviceId: String,
        lowBatteryThreshold: Int,
        crashDetectionEnabled: Boolean,
        shockAlertEnabled: Boolean
    ): Result<Unit>
    suspend fun unpairDevice(deviceId: String): Result<Unit>
    suspend fun applyTrackingSettings(
        deviceId: String,
        mode: String,
        intervalMillis: Long?,
        revertAfterMillis: Long?,
        currentMode: String,
        currentRevertToMode: String
    ): Result<Unit>
    fun getModeHistory(deviceId: String): Flow<List<ModeHistoryEntry>>
}

@Singleton
class FirestoreDeviceRepositoryImpl @Inject constructor(
    private val firestore: FirebaseFirestore,
    private val authManager: AuthManager
) : DeviceRepository {

    private val collection = firestore.collection("devices")

    override val devices: Flow<List<Device>> = authManager.authState.flatMapLatest { user ->
        if (user == null) {
            Log.w("PairDebug", "FirestoreDeviceRepositoryImpl: No active auth session, emitting empty device list")
            flowOf(emptyList())
        } else {
            val userId = user.uid
            Log.d("PairDebug", "FirestoreDeviceRepositoryImpl: Initiating auth-guarded devices listener for: $userId")
            
            callbackFlow {
                // 1. Verify valid session prior to listener setup as per security requirements
                if (FirebaseAuth.getInstance().currentUser == null) {
                    Log.e("PairDebug", "FirestoreDeviceRepositoryImpl: callbackFlow triggered without active Firebase user")
                    trySend(emptyList())
                    return@callbackFlow
                }

                val listenerRegistration = collection
                    .where(
                        Filter.or(
                            Filter.equalTo("ownerId", userId),
                            Filter.equalTo("owner_id", userId)
                        )
                    )
                    .addSnapshotListener { snapshot, error ->
                        if (error != null) {
                            Log.e("PairDebug", "FirestoreDeviceRepositoryImpl: Snapshot error in devices flow", error)
                            return@addSnapshotListener
                        }

                        if (snapshot != null) {
                            val deviceList = snapshot.documents.mapNotNull { it.toDevice() }
                            Log.d("PairDebug", "FirestoreDeviceRepositoryImpl: Received ${deviceList.size} devices")
                            trySend(deviceList)
                        }
                    }

                // 3. Safe cancellation
                awaitClose {
                    Log.d("PairDebug", "FirestoreDeviceRepositoryImpl: Cleaning up devices snapshot listener")
                    listenerRegistration.remove()
                }
            }
        }
    }

    override fun getDevicesStream(ownerId: String): Flow<List<Device>> = callbackFlow {
        // 1. Verify valid session and owner before listener setup
        if (ownerId.isBlank() || FirebaseAuth.getInstance().currentUser == null) {
            Log.e("PairDebug", "FirestoreDeviceRepositoryImpl: getDevicesStream called without valid owner or session")
            trySend(emptyList())
            return@callbackFlow
        }

        val listenerRegistration = collection
            .where(
                Filter.or(
                    Filter.equalTo("ownerId", ownerId),
                    Filter.equalTo("owner_id", ownerId)
                )
            )
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Log.e("PairDebug", "FirestoreDeviceRepositoryImpl: Snapshot error in getDevicesStream", error)
                    return@addSnapshotListener
                }
                val devices = snapshot?.documents?.mapNotNull { it.toDevice() } ?: emptyList()
                trySend(devices)
            }

        // 3. Safe cancellation
        awaitClose { 
            Log.d("PairDebug", "FirestoreDeviceRepositoryImpl: Cleaning up getDevicesStream listener")
            listenerRegistration.remove() 
        }
    }

    override suspend fun getAllDevices(ownerId: String): Result<List<Device>> {
        return try {
            val snapshot = collection
                .where(
                    Filter.or(
                        Filter.equalTo("ownerId", ownerId),
                        Filter.equalTo("owner_id", ownerId)
                    )
                )
                .get()
                .await()
            val devices = snapshot.documents.mapNotNull { it.toDevice() }
            Result.success(devices)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun pairDevice(
        code: String,
        friendlyName: String,
        ownerId: String
    ): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            if (ownerId.isBlank()) {
                return@withContext Result.failure(IllegalStateException("User not authenticated."))
            }

            Log.d("PairDebug", "Starting atomic pairing transaction for code $code and ownerId$ownerId")

            // 1. Query for the document reference matching the code
            val querySnapshot = firestore.collection("pairing_codes")
                .whereEqualTo("code", code)
                .get()
                .await()

            if (querySnapshot.isEmpty) {
                return@withContext Result.failure(IllegalArgumentException("Invalid or expired pairing code."))
            }

            val codeDocRef = querySnapshot.documents.first().reference

            // 2. Execute atomic transaction
            firestore.runTransaction { transaction ->
                // Re-read code document inside transaction to guarantee atomic claim
                val codeSnapshot = transaction.get(codeDocRef)
                if (!codeSnapshot.exists()) {
                    throw IllegalStateException("Pairing code has already been claimed or deleted.")
                }

                val expiresAt = codeSnapshot.getLong("expiresAt") ?: 0L
                if (expiresAt > 0L && System.currentTimeMillis() > expiresAt + 5 * 60 * 1000L) {
                    throw IllegalStateException("Pairing code has expired. Generate a new one on the Tracker.")
                }

                val deviceId = codeSnapshot.getString("deviceId")
                    ?: throw IllegalStateException("Invalid device metadata in pairing code.")
                val trackerAuthUid = codeSnapshot.getString("trackerAuthUid")

                val deviceRef = collection.document(deviceId)
                val updates = mapOf(
                    "is_paired" to true,
                    "deviceId" to deviceId,
                    "device_id" to deviceId,
                    "ownerId" to ownerId,
                    "trackerAuthUid" to trackerAuthUid,
                    "deviceName" to friendlyName.ifBlank { "New Device" },
                    "device_name" to friendlyName.ifBlank { "New Device" },
                    "status" to "online",
                    "pairedAt" to com.google.firebase.Timestamp.now()
                )

                // Atomic writes: claim device and delete pairing code
                transaction.set(deviceRef, updates, SetOptions.merge())
                transaction.delete(codeDocRef)
            }.await()

            Log.d("PairDebug", "Successfully paired device to $ownerId")
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e("PairDebug", "Error during atomic pairing transaction", e)
            Result.failure(e)
        }
    }

    override suspend fun updateDeviceSettings(
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
            collection.document(deviceId).update(updates).await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun removeDevice(deviceId: String): Result<Unit> {
        return try {
            collection.document(deviceId).delete().await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun renameDevice(deviceId: String, newName: String): Result<Unit> {
        return try {
            val updates = mapOf(
                "deviceName" to newName,
                "device_name" to newName
            )
            collection.document(deviceId).update(updates).await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun cleanupInactiveDevices(ownerId: String): Result<Int> {
        return try {
            val snapshot = collection
                .where(
                    Filter.or(
                        Filter.equalTo("ownerId", ownerId),
                        Filter.equalTo("owner_id", ownerId)
                    )
                )
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

    override suspend fun requestManualPing(deviceId: String): Result<Long> {
        return try {
            val requestTimestamp = System.currentTimeMillis()
            val updates = mapOf(
                "pingRequested" to true,
                "forceSyncRequestedAt" to requestTimestamp,
                "commandTimestamp" to requestTimestamp
            )
            collection.document(deviceId).update(updates).await()
            Result.success(requestTimestamp)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun updateAlertThresholds(
        deviceId: String,
        lowBatteryThreshold: Int,
        crashDetectionEnabled: Boolean,
        shockAlertEnabled: Boolean
    ): Result<Unit> {
        return try {
            val updates = mapOf(
                "alertThresholds.lowBatteryPercent" to lowBatteryThreshold,
                "alertThresholds.isCrashDetectionEnabled" to crashDetectionEnabled,
                "alertThresholds.isShockAlertEnabled" to shockAlertEnabled,
                "commandTimestamp" to System.currentTimeMillis()
            )
            collection.document(deviceId).update(updates).await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun unpairDevice(deviceId: String): Result<Unit> {
        return try {
            val updates = mapOf(
                "is_paired" to false,
                "isPaired" to false,
                "status" to "unpaired",
                "commandTimestamp" to System.currentTimeMillis()
            )
            collection.document(deviceId).update(updates).await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun applyTrackingSettings(
        deviceId: String,
        mode: String,
        intervalMillis: Long?,
        revertAfterMillis: Long?,
        currentMode: String,
        currentRevertToMode: String
    ): Result<Unit> {
        return try {
            val upperMode = mode.trim().uppercase()
            if (upperMode != "SCHEDULED" && upperMode != "LIVE" && upperMode != "ONLINE") {
                return Result.failure(IllegalArgumentException("Unknown tracking mode"))
            }

            // These limits must match TrackingConfig in the Tracker app
            if (upperMode == "SCHEDULED") {
                if (intervalMillis == null || intervalMillis !in 15_000L..2_592_000_000L) {
                    return Result.failure(IllegalArgumentException("Scheduled delay must be between 15 seconds and 30 days"))
                }
            } else if (upperMode == "LIVE") {
                if (intervalMillis == null || intervalMillis !in 5_000L..60_000L) {
                    return Result.failure(IllegalArgumentException("Live delay must be between 5 and 60 seconds"))
                }
                val revertVal = revertAfterMillis ?: 0L
                if (revertVal != 0L && revertVal !in 60_000L..604_800_000L) {
                    return Result.failure(IllegalArgumentException("Switch-back time must be between 1 minute and 7 days, or Never"))
                }
            }

            val trimmedCurrent = currentMode.trim().uppercase()
            val previousMode = when (trimmedCurrent) {
                "LIVE" -> "LIVE"
                "ONLINE" -> "ONLINE"
                else -> "SCHEDULED"
            }

            val now = System.currentTimeMillis()

            val updates = when (upperMode) {
                "SCHEDULED" -> mapOf(
                    "trackingMode" to upperMode,
                    "scheduledIntervalMillis" to intervalMillis!!,
                    "revertToMode" to "",
                    "trackingChangedAt" to now
                )
                "LIVE" -> {
                    val targetRevertToMode = if (previousMode == "LIVE") {
                        val trimmedRevert = currentRevertToMode.trim().uppercase()
                        if (trimmedRevert == "ONLINE") "ONLINE" else "SCHEDULED"
                    } else {
                        previousMode
                    }
                    mapOf(
                        "trackingMode" to upperMode,
                        "liveIntervalMillis" to intervalMillis!!,
                        "liveRevertAfterMillis" to (revertAfterMillis ?: 0L),
                        "revertToMode" to targetRevertToMode,
                        "trackingChangedAt" to now
                    )
                }
                else -> mapOf(
                    "trackingMode" to upperMode,
                    "revertToMode" to "",
                    "trackingChangedAt" to now
                )
            }

            val deviceRef = collection.document(deviceId)
            val batch = firestore.batch()
            batch.update(deviceRef, updates)
            batch.set(
                deviceRef.collection("mode_history").document(),
                mapOf(
                    "mode" to upperMode,
                    "intervalMillis" to (intervalMillis ?: 0L),
                    "changedBy" to "Admin",
                    "timestamp" to System.currentTimeMillis()
                )
            )
            batch.commit().await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override fun getModeHistory(deviceId: String): Flow<List<ModeHistoryEntry>> = callbackFlow {
        if (deviceId.isBlank() || FirebaseAuth.getInstance().currentUser == null) {
            Log.e("DeviceRepo", "getModeHistory called without valid deviceId or session")
            trySend(emptyList())
            return@callbackFlow
        }

        val listenerRegistration = collection.document(deviceId)
            .collection("mode_history")
            .orderBy("timestamp", Query.Direction.DESCENDING)
            .limit(20)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Log.e("DeviceRepo", "Snapshot error in getModeHistory", error)
                    trySend(emptyList())
                    return@addSnapshotListener
                }
                val entries = snapshot?.documents?.mapNotNull {
                    it.toObject(ModeHistoryEntry::class.java)?.copy(id = it.id)
                } ?: emptyList()
                trySend(entries)
            }

        awaitClose {
            listenerRegistration.remove()
        }
    }
}
