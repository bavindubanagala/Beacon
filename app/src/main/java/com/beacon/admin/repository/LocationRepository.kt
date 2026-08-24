package com.beacon.admin.repository

import com.beacon.shared.constants.FirebaseCollections
import com.beacon.shared.models.Location
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import kotlinx.coroutines.tasks.await

class LocationRepository(
    private val firestore: FirebaseFirestore
) {

    suspend fun getRecentLocations(deviceId: String, limit: Int = 100): Result<List<Location>> {
        return try {
            val snapshot = firestore
                .collection(FirebaseCollections.DEVICES)
                .document(deviceId)
                .collection(FirebaseCollections.LOCATION_HISTORY)
                .orderBy("timestamp", Query.Direction.DESCENDING)
                .limit(limit.toLong())
                .get()
                .await()

            val locations = snapshot.documents.map { doc ->
                Location.fromMap(doc.data ?: emptyMap())
            }

            Result.success(locations)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun getLocationsInTimeRange(
        deviceId: String,
        startTime: Long,
        endTime: Long
    ): Result<List<Location>> {
        return try {
            val snapshot = firestore
                .collection(FirebaseCollections.DEVICES)
                .document(deviceId)
                .collection(FirebaseCollections.LOCATION_HISTORY)
                .whereGreaterThanOrEqualTo("timestamp", startTime)
                .whereLessThanOrEqualTo("timestamp", endTime)
                .orderBy("timestamp", Query.Direction.ASCENDING)
                .get()
                .await()

            val locations = snapshot.documents.map { doc ->
                Location.fromMap(doc.data ?: emptyMap())
            }

            Result.success(locations)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun pruneOldHistory(deviceId: String, retentionDays: Int): Result<Int> {
        return try {
            val cutoff = System.currentTimeMillis() - (retentionDays.toLong() * 24 * 60 * 60 * 1000L)
            val snapshot = firestore
                .collection(FirebaseCollections.DEVICES)
                .document(deviceId)
                .collection(FirebaseCollections.LOCATION_HISTORY)
                .whereLessThan("timestamp", cutoff)
                .get()
                .await()

            var count = 0
            val batch = firestore.batch()
            for (doc in snapshot.documents) {
                batch.delete(doc.reference)
                count++
            }
            batch.commit().await()
            Result.success(count)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
