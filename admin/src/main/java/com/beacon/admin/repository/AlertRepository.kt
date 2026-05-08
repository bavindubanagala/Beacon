package com.beacon.admin.repository

import android.content.Context
import com.beacon.shared.constants.FirebaseCollections
import com.beacon.shared.models.Alert
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import kotlinx.coroutines.tasks.await

class AlertRepository(context: Context) {
    private val firestore = FirebaseFirestore.getInstance()

    suspend fun getActiveAlerts(deviceId: String? = null): Result<List<Alert>> {
        return try {
            val query = if (deviceId != null) {
                firestore.collection(FirebaseCollections.DEVICES)
                    .document(deviceId)
                    .collection(FirebaseCollections.ALERTS)
                    .whereEqualTo("resolved", false)
            } else {
                firestore.collectionGroup(FirebaseCollections.ALERTS)
                    .whereEqualTo("resolved", false)
            }

            val alerts = query
                .orderBy("created_at", Query.Direction.DESCENDING)
                .get()
                .await()
                .toObjects(Alert::class.java)

            Result.success(alerts)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun getAlertHistory(deviceId: String, limit: Int = 50): Result<List<Alert>> {
        return try {
            val alerts = firestore.collection(FirebaseCollections.DEVICES)
                .document(deviceId)
                .collection(FirebaseCollections.ALERTS)
                .orderBy("created_at", Query.Direction.DESCENDING)
                .limit(limit.toLong())
                .get()
                .await()
                .toObjects(Alert::class.java)

            Result.success(alerts)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun resolveAlert(deviceId: String, alertId: String): Result<Unit> {
        return try {
            firestore.collection(FirebaseCollections.DEVICES)
                .document(deviceId)
                .collection(FirebaseCollections.ALERTS)
                .document(alertId)
                .update(
                    "resolved", true,
                    "resolved_at", System.currentTimeMillis()
                )
                .await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun dismissAlert(deviceId: String, alertId: String): Result<Unit> {
        return try {
            firestore.collection(FirebaseCollections.DEVICES)
                .document(deviceId)
                .collection(FirebaseCollections.ALERTS)
                .document(alertId)
                .update(
                    "dismissed", true,
                    "dismissed_at", System.currentTimeMillis()
                )
                .await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
