package com.beacon.shared.repository

import android.util.Log
import com.beacon.shared.constants.FirestoreCollections
import com.beacon.shared.models.GeofenceEvent
import com.beacon.shared.models.GeofenceZone
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FirebaseGeofenceRepository @Inject constructor(
    private val firestore: FirebaseFirestore
) {
    private val zoneCollection = firestore.collection(FirestoreCollections.GEOFENCES)
    private val eventCollection = firestore.collection(FirestoreCollections.GEOFENCE_EVENTS)
    private val auth = FirebaseAuth.getInstance()

    fun getGeofencesForDevice(deviceId: String): Flow<List<GeofenceZone>> {
        if (auth.currentUser == null) return flowOf(emptyList())

        return callbackFlow {
            val subscription = zoneCollection
                .whereArrayContains("assignedDeviceIds", deviceId)
                .addSnapshotListener { snapshot, error ->
                    if (error != null) {
                        Log.e("GeofenceRepo", "getGeofencesForDevice error", error)
                        trySend(emptyList())
                        return@addSnapshotListener
                    }
                    val zones = snapshot?.documents?.mapNotNull { it.toObject(GeofenceZone::class.java)?.copy(id = it.id) } ?: emptyList()
                    trySend(zones)
                }
            awaitClose { subscription.remove() }
        }
    }

    fun getGeofencesForGroup(groupId: String): Flow<List<GeofenceZone>> {
        if (auth.currentUser == null) return flowOf(emptyList())

        return callbackFlow {
            val subscription = zoneCollection
                .whereArrayContains("assignedGroupIds", groupId)
                .addSnapshotListener { snapshot, error ->
                    if (error != null) {
                        Log.e("GeofenceRepo", "getGeofencesForGroup error", error)
                        trySend(emptyList())
                        return@addSnapshotListener
                    }
                    val zones = snapshot?.documents?.mapNotNull { it.toObject(GeofenceZone::class.java)?.copy(id = it.id) } ?: emptyList()
                    trySend(zones)
                }
            awaitClose { subscription.remove() }
        }
    }

    suspend fun saveGeofence(geofence: GeofenceZone): Result<Unit> {
        return try {
            val docRef = if (geofence.id.isEmpty()) {
                zoneCollection.document()
            } else {
                zoneCollection.document(geofence.id)
            }
            docRef.set(geofence.copy(id = docRef.id), SetOptions.merge()).await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun deleteGeofence(geofenceId: String): Result<Unit> {
        return try {
            zoneCollection.document(geofenceId).delete().await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun logGeofenceEvent(event: GeofenceEvent): Result<Unit> {
        return try {
            val docRef = eventCollection.document()
            eventCollection.document(docRef.id).set(event.copy(id = docRef.id)).await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun getGeofenceEventsForDevice(deviceId: String): Flow<List<GeofenceEvent>> {
        if (auth.currentUser == null) return flowOf(emptyList())

        return callbackFlow {
            val subscription = eventCollection
                .whereEqualTo("deviceId", deviceId)
                .orderBy("timestamp", Query.Direction.DESCENDING)
                .limit(50)
                .addSnapshotListener { snapshot, error ->
                    if (error != null) {
                        Log.e("GeofenceRepo", "getGeofenceEventsForDevice error", error)
                        trySend(emptyList())
                        return@addSnapshotListener
                    }
                    val events = snapshot?.documents?.mapNotNull { it.toObject(GeofenceEvent::class.java)?.copy(id = it.id) } ?: emptyList()
                    trySend(events)
                }
            awaitClose { subscription.remove() }
        }
    }

    fun getAllGeofenceEvents(): Flow<List<GeofenceEvent>> {
        if (auth.currentUser == null) return flowOf(emptyList())

        return callbackFlow {
            val subscription = eventCollection
                .orderBy("timestamp", Query.Direction.DESCENDING)
                .limit(100)
                .addSnapshotListener { snapshot, error ->
                    if (error != null) {
                        Log.e("GeofenceRepo", "getAllGeofenceEvents error", error)
                        trySend(emptyList())
                        return@addSnapshotListener
                    }
                    val events = snapshot?.documents?.mapNotNull { it.toObject(GeofenceEvent::class.java)?.copy(id = it.id) } ?: emptyList()
                    trySend(events)
                }
            awaitClose { subscription.remove() }
        }
    }
}
