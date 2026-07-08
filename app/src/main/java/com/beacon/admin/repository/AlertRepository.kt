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
}
