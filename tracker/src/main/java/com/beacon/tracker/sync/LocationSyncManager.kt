package com.beacon.tracker.sync

import com.beacon.tracker.data.LocationDao
import com.beacon.tracker.data.LocationEntity
import com.beacon.tracker.network.NetworkStatusObserver
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class LocationSyncManager @Inject constructor(
    private val locationDao: LocationDao,
    private val firestore: FirebaseFirestore,
    private val networkStatusObserver: NetworkStatusObserver
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

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

    suspend fun processLocationUpdate(userId: String, entity: LocationEntity) {
        if (networkStatusObserver.isCurrentlyOnline()) {
            try {
                uploadLocationToFirestore(userId, entity)
            } catch (e: Exception) {
                locationDao.insertLocation(entity.copy(isSynced = false))
            }
        } else {
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

        for (location in unsyncedLocations) {
            try {
                uploadLocationToFirestore(location.userId, location)
                locationDao.markAsSynced(location.id)
            } catch (e: Exception) {
                break
            }
        }
    }

    private suspend fun uploadLocationToFirestore(userId: String, location: LocationEntity) {
        val locationMap = hashMapOf(
            "latitude" to location.latitude,
            "longitude" to location.longitude,
            "timestamp" to location.timestamp,
            "speed" to location.speed,
            "accuracy" to location.accuracy
        )

        firestore.collection("users")
            .document(userId)
            .collection("locations")
            .document(location.id.toString())
            .set(locationMap)
            .await()
    }
}