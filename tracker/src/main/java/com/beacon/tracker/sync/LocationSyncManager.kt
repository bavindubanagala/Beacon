package com.beacon.tracker.sync

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.util.Log
import com.beacon.shared.constants.FirestoreCollections
import com.beacon.tracker.data.LocationDao
import com.beacon.tracker.data.LocationEntity
import com.google.firebase.firestore.FirebaseFirestore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class LocationSyncManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val firestore: FirebaseFirestore,
    private val locationDao: LocationDao,
    private val scope: CoroutineScope
) {
    private val TAG = "LocationSyncManager"
    private val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
    
    private val _isOnline = MutableStateFlow(false)
    val isOnline: StateFlow<Boolean> = _isOnline.asStateFlow()

    private val networkCallback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) {
            _isOnline.value = true
            Log.d(TAG, "Internet Restored - Flushing Cache")
            flushOfflineCache()
        }

        override fun onLost(network: Network) {
            _isOnline.value = false
            Log.d(TAG, "Internet Lost - Store-and-Forward Enabled")
        }
    }

    init {
        val request = NetworkRequest.Builder()
            .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .build()
        connectivityManager.registerNetworkCallback(request, networkCallback)
        
        val activeNetwork = connectivityManager.activeNetwork
        val caps = connectivityManager.getNetworkCapabilities(activeNetwork)
        _isOnline.value = caps?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) == true
    }

    fun dispatchLocation(location: android.location.Location, deviceId: String) {
        val entity = LocationEntity(
            deviceId = deviceId,
            latitude = location.latitude,
            longitude = location.longitude,
            altitude = location.altitude,
            speed = location.speed,
            accuracy = location.accuracy,
            timestamp = System.currentTimeMillis(),
            isSynced = false
        )

        scope.launch(Dispatchers.IO) {
            if (_isOnline.value) {
                // Online Path: Direct dispatch
                try {
                    sendToFirestore(entity)
                    // Optionally log to local DB as synced for local history
                    locationDao.insertLocation(entity.copy(isSynced = true))
                } catch (e: Exception) {
                    Log.e(TAG, "Direct dispatch failed, falling back to local storage", e)
                    locationDao.insertLocation(entity)
                }
            } else {
                // Offline Path: Buffer in Room
                locationDao.insertLocation(entity)
            }
        }
    }

    private suspend fun sendToFirestore(entity: LocationEntity) {
        val docData = mapOf(
            "latitude" to entity.latitude,
            "longitude" to entity.longitude,
            "altitude" to entity.altitude,
            "speed" to entity.speed,
            "accuracy" to entity.accuracy,
            "timestamp" to entity.timestamp,
            "deviceId" to entity.deviceId
        )

        firestore.collection(FirestoreCollections.DEVICES)
            .document(entity.deviceId)
            .collection(FirestoreCollections.LOCATIONS)
            .add(docData)
            .await()
    }

    fun flushOfflineCache() {
        scope.launch(Dispatchers.IO) {
            val unsynced = locationDao.getUnsyncedLocations()
            if (unsynced.isEmpty()) return@launch

            Log.d(TAG, "Flushing ${unsynced.size} cached locations to Firestore")
            
            unsynced.forEach { entity ->
                try {
                    sendToFirestore(entity)
                    locationDao.markAsSynced(listOf(entity.id))
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to sync cached location ${entity.id}", e)
                }
            }
            
            // Maintenance: cleanup old synced logs (older than 7 days)
            val threshold = System.currentTimeMillis() - (7 * 24 * 60 * 60 * 1000L)
            locationDao.deleteOldSyncedLogs(threshold)
        }
    }

    fun cleanup() {
        connectivityManager.unregisterNetworkCallback(networkCallback)
    }
}
