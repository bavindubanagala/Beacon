package com.beacon.admin.repository

import com.beacon.shared.constants.FirebaseCollections
import com.beacon.shared.mapper.toAlert
import com.beacon.shared.models.Alert
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Filter
import com.google.firebase.firestore.Query
import kotlinx.coroutines.tasks.await

class AlertRepository(
    private val firestore: FirebaseFirestore
) {

    suspend fun getActiveAlerts(ownerId: String): Result<List<Alert>> {
        return try {
            // Fetch alerts only for devices owned by this user
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

    suspend fun getAlertsForDevice(deviceId: String, limit: Int = 50): Result<List<Alert>> {
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

    suspend fun resolveSos(deviceId: String): Result<Unit> {
        return try {
            firestore.collection("devices").document(deviceId)
                .update(mapOf("sosActive" to false))
                .await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun clearAllAlerts(ownerId: String): Result<Unit> {
        return try {
            // 1. Get all device IDs for this owner
            val devicesSnapshot = firestore.collection("devices")
                .where(Filter.or(
                    Filter.equalTo("ownerId", ownerId),
                    Filter.equalTo("owner_id", ownerId)
                ))
                .get()
                .await()
            
            val deviceIds = devicesSnapshot.documents.map { it.id }
            if (deviceIds.isEmpty()) return Result.success(Unit)

            // 2. Fetch and delete alerts for each device
            // Using a batch for efficiency
            val batch = firestore.batch()
            
            // Note: firestore.collectionGroup("alerts") is read-only for queries, 
            // so we must delete from each sub-collection.
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
