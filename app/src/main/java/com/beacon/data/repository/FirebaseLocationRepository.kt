package com.beacon.data.repository

import com.beacon.shared.constants.FirebaseCollections
import com.beacon.shared.models.Location
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FirebaseLocationRepository @Inject constructor(
    private val firestore: FirebaseFirestore
) : LocationRepository {

    override suspend fun getRecentLocations(deviceId: String, limit: Int): Result<List<Location>> {
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

    override suspend fun getLocationsInTimeRange(
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
}
