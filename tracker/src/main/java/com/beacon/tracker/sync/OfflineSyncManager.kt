package com.beacon.tracker.sync

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.util.Log
import com.beacon.shared.constants.FirebaseCollections
import com.beacon.shared.constants.FirestoreCollections
import com.beacon.tracker.db.OfflineBufferDao
import com.beacon.tracker.db.OfflineLocationEntity
import com.beacon.tracker.db.OfflineGeofenceEventEntity
import com.beacon.tracker.repository.FirebaseTrackerRepository
import com.google.firebase.firestore.FirebaseFirestore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class OfflineSyncManager @Inject constructor(
    private val bufferDao: OfflineBufferDao,
    private val firestore: FirebaseFirestore,
    private val trackerRepository: FirebaseTrackerRepository,
    @ApplicationContext private val context: Context,
    private val scope: CoroutineScope
) {
    private val TAG = "OfflineSyncManager"
    private val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
    private val syncMutex = Mutex()

    private val _isOnline = MutableStateFlow(false)
    val isOnline: StateFlow<Boolean> = _isOnline.asStateFlow()

    private val networkCallback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) {
            _isOnline.value = true
            Log.d(TAG, "Network Available - Starting Sync")
            syncPendingData()
        }

        override fun onLost(network: Network) {
            _isOnline.value = false
            Log.d(TAG, "Network Lost - Buffering Enabled")
        }
    }

    init {
        connectivityManager.registerDefaultNetworkCallback(networkCallback)
        // Initial state check
        val activeNetwork = connectivityManager.activeNetwork
        _isOnline.value = activeNetwork != null
    }

    fun syncPendingData() {
        scope.launch(Dispatchers.IO) {
            syncMutex.withLock {
                try {
                    while (bufferDao.getUnsyncedLocationsCount() > 0) {
                        syncLocationBatch()
                    }
                    while (bufferDao.getUnsyncedGeofenceEventsCount() > 0) {
                        syncGeofenceEventBatch()
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Critical sync failure", e)
                }
            }
        }
    }

    private suspend fun syncLocationBatch() {
        val batchList = bufferDao.getUnsyncedLocations().take(500)
        if (batchList.isEmpty()) return

        Log.d(TAG, "Syncing ${batchList.size} locations")
        val batch = firestore.batch()
        
        batchList.forEach { entity ->
            val docRef = firestore.collection(FirebaseCollections.DEVICES)
                .document(entity.deviceId)
                .collection(FirebaseCollections.LOCATION_HISTORY)
                .document(entity.id) // Use local UUID for idempotency
            
            val locationMap = mapOf(
                "deviceId" to entity.deviceId,
                "latitude" to entity.latitude,
                "longitude" to entity.longitude,
                "altitude" to entity.altitude,
                "speed" to entity.speed,
                "bearing" to entity.bearing,
                "accuracy" to entity.accuracy,
                "batteryLevel" to entity.batteryLevel,
                "timestamp" to entity.timestamp
            )
            batch.set(docRef, locationMap)
        }

        try {
            batch.commit().await()
            // Ensure deletion is atomic and non-cancellable
            withContext(NonCancellable) {
                bufferDao.deleteLocationsByIds(batchList.map { it.id })
            }
            Log.d(TAG, "Batch of ${batchList.size} locations synced successfully")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to commit location batch", e)
            throw e // Reraise to break while loop
        }
    }

    private suspend fun syncGeofenceEventBatch() {
        val batchList = bufferDao.getUnsyncedGeofenceEvents().take(500)
        if (batchList.isEmpty()) return

        Log.d(TAG, "Syncing ${batchList.size} geofence events")
        val batch = firestore.batch()

        batchList.forEach { entity ->
            // Use local entity UUID as doc ID for idempotent replay protection
            val docRef = firestore.collection(FirestoreCollections.GEOFENCE_EVENTS).document(entity.id)
            val eventMap = mapOf(
                "id" to entity.id,
                "geofenceId" to entity.geofenceId,
                "deviceId" to entity.deviceId,
                "geofenceName" to entity.geofenceName,
                "eventType" to entity.eventType.name,
                "latitude" to entity.latitude,
                "longitude" to entity.longitude,
                "timestamp" to entity.timestamp
            )
            batch.set(docRef, eventMap)
        }

        try {
            batch.commit().await()
            withContext(NonCancellable) {
                bufferDao.deleteGeofenceEventsByIds(batchList.map { it.id })
            }
            Log.d(TAG, "Batch of ${batchList.size} geofence events synced successfully")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to commit geofence event batch", e)
            throw e
        }
    }

    fun cleanup() {
        try {
            connectivityManager.unregisterNetworkCallback(networkCallback)
        } catch (e: Exception) {
            // Callback not registered
        }
    }
}
