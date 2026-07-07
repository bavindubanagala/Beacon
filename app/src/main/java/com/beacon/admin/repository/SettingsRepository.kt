package com.beacon.admin.repository

import com.beacon.shared.constants.FirestoreCollections
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await

class SettingsRepository(
    private val firestore: FirebaseFirestore
) {

    suspend fun getSettings(): Result<Map<String, Any>> {
        return try {
            val doc = firestore.collection(FirestoreCollections.ADMIN_SETTINGS)
                .document("default")
                .get()
                .await()

            Result.success(doc.data ?: emptyMap())
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun updateSettings(settings: Map<String, Any>): Result<Unit> {
        return try {
            firestore.collection(FirestoreCollections.ADMIN_SETTINGS)
                .document("default")
                .set(settings, com.google.firebase.firestore.SetOptions.merge())
                .await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
