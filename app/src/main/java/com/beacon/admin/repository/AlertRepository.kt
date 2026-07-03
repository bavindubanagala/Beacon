package com.beacon.admin.repository

import com.beacon.shared.mapper.toAlert
import com.beacon.shared.models.Alert
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await

class AlertRepository(
    private val firestore: FirebaseFirestore
) {
    private val collection = firestore.collection("alerts")

    suspend fun getActiveAlerts(): Result<List<Alert>> {
        return try {
            val snapshot = collection
                .whereEqualTo("active", true)
                .get()
                .await()

            val alerts = snapshot.documents.mapNotNull { it.toAlert() }
            Result.success(alerts)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
