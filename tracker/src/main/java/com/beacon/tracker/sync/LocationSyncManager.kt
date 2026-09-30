package com.beacon.tracker.sync

import android.util.Log
import com.beacon.shared.models.Location as SharedLocation
import com.beacon.tracker.data.LocationDao
import com.beacon.tracker.data.LocationEntity
import com.beacon.tracker.network.NetworkStatusObserver
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.abs
import kotlin.math.max

@Singleton
class LocationSyncManager @Inject constructor(
    private val locationDao: LocationDao,
    private val firestore: FirebaseFirestore,
    private val networkStatusObserver: NetworkStatusObserver
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val throttleLock = Any()
    private var lastHistoryLat: Double? = null
    private var lastHistoryLng: Double? = null
    private var lastHistoryTime: Long = 0L
    private var lastDeviceLat: Double? = null
    private var lastDeviceLng: Double? = null
    private var lastDeviceTime: Long = 0L
    private var lastDeviceBattery: Int = -1

    init {
        observeNetworkAndSync()
    }

    private fun observeNetworkAndSync() {
        scope.launch {
            networkStatusObserver.isOnline.collectLatest { isOnline ->
                if (isOnline) {
                    syncPendingLocations()
                }
            }
        }
    }

    suspend fun processLocationUpdate(userId: String, entity: LocationEntity, force: Boolean = false) {
        val isOnline = networkStatusObserver.isCurrentlyOnline()
        Log.d("LocationSyncManager", "processLocationUpdate: isOnline=$isOnline")
        if (isOnline) {
            try {
                uploadLocationToFirestore(userId, entity, force = force)
                Log.d("LocationSyncManager", "processLocationUpdate: uploadLocationToFirestore succeeded")
            } catch (e: Exception) {
                Log.e("LocationSyncManager", "processLocationUpdate: uploadLocationToFirestore failed", e)
                locationDao.insertLocation(entity.copy(isSynced = false))
            }
        } else {
            Log.w("LocationSyncManager", "processLocationUpdate: device reported offline, queuing locally instead of uploading")
            locationDao.insertLocation(entity.copy(isSynced = false))
        }
    }

    suspend fun processGeofenceEvent(
        userId: String,
        geofenceId: String,
        transitionType: String,
        timestamp: Long
    ) {
        val eventData = hashMapOf(
            "geofenceId" to geofenceId,
            "transitionType" to transitionType,
            "timestamp" to timestamp
        )

        if (networkStatusObserver.isCurrentlyOnline()) {
            try {
                firestore.collection("users")
                    .document(userId)
                    .collection("geofence_events")
                    .add(eventData)
                    .await()
            } catch (e: Exception) {
                // Buffer locally as location entity flag or geofence store
            }
        }
    }

    suspend fun syncPendingLocations() {
        val unsyncedLocations = locationDao.getUnsyncedLocations()
        if (unsyncedLocations.isEmpty()) return

        for (i in unsyncedLocations.indices) {
            val location = unsyncedLocations[i]
            val isLast = i == unsyncedLocations.lastIndex
            try {
                uploadLocationToFirestore(
                    userId = location.userId,
                    location = location,
                    force = true,
                    updateDevice = isLast
                )
                locationDao.markAsSynced(location.id)
            } catch (e: Exception) {
                break
            }
        }
    }

    private suspend fun uploadLocationToFirestore(
        userId: String,
        location: LocationEntity,
        force: Boolean,
        updateDevice: Boolean = true
    ) {
        val targetDeviceId = location.deviceId.ifBlank { userId }
        val now = System.currentTimeMillis()

        val shouldWriteDevice: Boolean
        val shouldWriteHistory: Boolean

        synchronized(throttleLock) {
            shouldWriteDevice = updateDevice && targetDeviceId.isNotBlank() && (
                force ||
                lastDeviceTime == 0L ||
                (now - lastDeviceTime) >= 60_000L ||
                (lastDeviceLat != null && lastDeviceLng != null && calculateDistanceMeters(lastDeviceLat!!, lastDeviceLng!!, location.latitude, location.longitude) >= 10f) ||
                abs(location.batteryLevel - lastDeviceBattery) >= 2
            )

            shouldWriteHistory = targetDeviceId.isNotBlank() && (
                force ||
                lastHistoryTime == 0L ||
                (now - lastHistoryTime) >= 300_000L ||
                (lastHistoryLat != null && lastHistoryLng != null && calculateDistanceMeters(lastHistoryLat!!, lastHistoryLng!!, location.latitude, location.longitude) >= max(25f, location.accuracy))
            )
        }

        // Device document write
        if (shouldWriteDevice) {
            val syncedAt = System.currentTimeMillis()
            val deviceUpdate = hashMapOf(
                "latitude" to location.latitude,
                "longitude" to location.longitude,
                "accuracy" to location.accuracy,
                "speed" to location.speed,
                "batteryLevel" to location.batteryLevel,
                "battery_level" to location.batteryLevel,
                "signal_strength" to location.signalStrength,
                "signalStrength" to location.signalStrength,
                "lastSeenTimestamp" to syncedAt,
                "last_seen" to syncedAt,
                "status" to "online"
            )

            firestore.collection("devices")
                .document(targetDeviceId)
                .set(deviceUpdate, SetOptions.merge())
                .await()

            synchronized(throttleLock) {
                lastDeviceLat = location.latitude
                lastDeviceLng = location.longitude
                lastDeviceTime = now
                lastDeviceBattery = location.batteryLevel
            }
        }

        // History point write
        if (shouldWriteHistory) {
            val sharedLocation = SharedLocation(
                deviceId = targetDeviceId,
                timestamp = location.timestamp,
                latitude = location.latitude,
                longitude = location.longitude,
                accuracy = location.accuracy,
                provider = "fused",
                speed = location.speed,
                heading = location.bearing,
                batteryLevel = location.batteryLevel,
                signalStrength = location.signalStrength,
                deviceMotionStatus = location.deviceMotionStatus
            )

            firestore.collection("devices")
                .document(targetDeviceId)
                .collection("location_history")
                .document(location.timestamp.toString())
                .set(sharedLocation.toMap())
                .await()

            synchronized(throttleLock) {
                lastHistoryLat = location.latitude
                lastHistoryLng = location.longitude
                lastHistoryTime = now
            }
        }
    }

    private fun calculateDistanceMeters(
        startLat: Double,
        startLng: Double,
        endLat: Double,
        endLng: Double
    ): Float {
        val results = FloatArray(1)
        android.location.Location.distanceBetween(startLat, startLng, endLat, endLng, results)
        return results[0]
    }
}
