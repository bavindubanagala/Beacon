package com.beacon.data.repository

import android.util.Log
import com.beacon.data.auth.AuthManager
import com.beacon.shared.constants.FirebaseCollections
import com.beacon.shared.mapper.toAlert
import com.beacon.shared.models.Alert
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Filter
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

interface AlertRepository {
    val alerts: Flow<List<Alert>>
    fun getAlertsStream(ownerId: String): Flow<List<Alert>>
    suspend fun getActiveAlerts(ownerId: String): Result<List<Alert>>
    suspend fun getAlertsForDevice(deviceId: String, limit: Int = 50): Result<List<Alert>>
    suspend fun resolveSos(deviceId: String): Result<Unit>
    suspend fun clearAllAlerts(ownerId: String): Result<Unit>
}

@Singleton
class FirestoreAlertRepositoryImpl @Inject constructor(
    private val firestore: FirebaseFirestore,
    private val authManager: AuthManager
) : AlertRepository {

    override val alerts: Flow<List<Alert>> = authManager.authState.flatMapLatest { user ->
        if (user == null) {
            Log.w("AlertDebug", "No active user ID available for alerts snapshot listener")
            flowOf(emptyList())
        } else {
            val userId = user.uid
            Log.d("AlertDebug", "Attaching real-time alerts snapshot listener for ownerId: $userId")

            callbackFlow {
                var alertsListener: ListenerRegistration? = null

                // First get device IDs to filter collection group
                val devicesListener = firestore.collection("devices")
                    .where(Filter.or(
                        Filter.equalTo("ownerId", userId),
                        Filter.equalTo("owner_id", userId)
                    ))
                    .addSnapshotListener { devicesSnapshot, error ->
                        if (error != null) {
                            Log.e("AlertDebug", "Error fetching devices for alerts filter", error)
                            trySend(emptyList())
                            close()
                            return@addSnapshotListener
                        }
                        
                        val deviceIds = devicesSnapshot?.documents?.map { it.id } ?: emptyList()
                        if (deviceIds.isEmpty()) {
                            trySend(emptyList())
                            return@addSnapshotListener
                        }

                        // Remove previous alerts listener if it exists (re-attaching with new deviceIds filter)
                        alertsListener?.remove()

                        // Listen to alerts across all devices
                        alertsListener = firestore.collectionGroup("alerts")
                            .orderBy("created_at", Query.Direction.DESCENDING)
                            .limit(100)
                            .addSnapshotListener { alertsSnapshot, alertsError ->
                                if (alertsError != null) {
                                    Log.e("AlertDebug", "Error fetching alerts snapshot", alertsError)
                                    trySend(emptyList())
                                    close()
                                    return@addSnapshotListener
                                }
                                
                                if (alertsSnapshot != null) {
                                    val rawAlerts = alertsSnapshot.documents
                                        .filter { doc -> deviceIds.contains(doc.getString("device_id")) }
                                        .map { it.toAlert() }

                                    val deduplicatedAlerts = deduplicateAlerts(rawAlerts)
                                    Log.d("AlertDebug", "Received ${rawAlerts.size} raw alerts -> Deduplicated to ${deduplicatedAlerts.size}")
                                    trySend(deduplicatedAlerts)
                                }
                            }
                    }

                awaitClose {
                    Log.d("AlertDebug", "Removing alerts snapshot listeners")
                    devicesListener.remove()
                    alertsListener?.remove()
                }
            }
        }
    }

    private fun deduplicateAlerts(alerts: List<Alert>): List<Alert> {
        val seenKeys = mutableSetOf<String>()
        val result = mutableListOf<Alert>()

        for (alert in alerts) {
            // Key based on deviceId and alert type/title, or resolution state
            val key = "${alert.device_id}_${alert.alert_type}_${alert.is_read}"
            if (!seenKeys.contains(key)) {
                seenKeys.add(key)
                result.add(alert)
            }
        }
        return result
    }

    override fun getAlertsStream(ownerId: String): Flow<List<Alert>> = callbackFlow {
        val listener = firestore.collectionGroup("alerts")
            .orderBy("created_at", Query.Direction.DESCENDING)
            .limit(100)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Log.e("AlertDebug", "getAlertsStream error", error)
                    trySend(emptyList())
                    close()
                    return@addSnapshotListener
                }
                
                val alerts = snapshot?.documents?.map { it.toAlert() } ?: emptyList()
                trySend(alerts)
            }

        awaitClose { 
            Log.d("AlertDebug", "Removing alerts stream listener")
            listener.remove() 
        }
    }

    override suspend fun getActiveAlerts(ownerId: String): Result<List<Alert>> {
        return try {
            val devicesSnapshot = firestore.collection("devices")
                .where(Filter.or(
                    Filter.equalTo("ownerId", ownerId),
                    Filter.equalTo("owner_id", ownerId)
                ))
                .get()
                .await()
            
            val deviceIds = devicesSnapshot.documents.map { it.id }
            if (deviceIds.isEmpty()) return Result.success(emptyList())

            val snapshot = firestore
                .collectionGroup("alerts")
                .orderBy("created_at", Query.Direction.DESCENDING)
                .limit(50L)
                .get()
                .await()

            val alerts = snapshot.documents
                .filter { doc ->
                    val dId = doc.getString("device_id")
                    deviceIds.contains(dId)
                }
                .map { it.toAlert() }
            Result.success(alerts)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun getAlertsForDevice(deviceId: String, limit: Int): Result<List<Alert>> {
        return try {
            val snapshot = firestore
                .collection(FirebaseCollections.DEVICES)
                .document(deviceId)
                .collection(FirebaseCollections.ALERTS)
                .orderBy("created_at", Query.Direction.DESCENDING)
                .limit(limit.toLong())
                .get()
                .await()

            val alerts = snapshot.documents.map { it.toAlert() }
            Result.success(alerts)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun resolveSos(deviceId: String): Result<Unit> {
        return try {
            firestore.collection("devices").document(deviceId)
                .update(mapOf("sosActive" to false))
                .await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun clearAllAlerts(ownerId: String): Result<Unit> {
        return try {
            val devicesSnapshot = firestore.collection("devices")
                .where(Filter.or(
                    Filter.equalTo("ownerId", ownerId),
                    Filter.equalTo("owner_id", ownerId)
                ))
                .get()
                .await()
            
            val deviceIds = devicesSnapshot.documents.map { it.id }
            if (deviceIds.isEmpty()) return Result.success(Unit)

            val batch = firestore.batch()
            for (deviceId in deviceIds) {
                val alertsSnapshot = firestore.collection("devices")
                    .document(deviceId)
                    .collection("alerts")
                    .get()
                    .await()
                
                for (doc in alertsSnapshot.documents) {
                    batch.delete(doc.reference)
                }
            }
            
            batch.commit().await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
