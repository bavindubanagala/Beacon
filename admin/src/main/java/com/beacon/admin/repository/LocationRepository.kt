package com.beacon.admin.repository

import android.content.Context
import com.beacon.shared.constants.FirebaseCollections
import com.beacon.shared.models.Location
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import kotlinx.coroutines.tasks.await
import java.util.Date

class LocationRepository(context: Context) {
    private val firestore = FirebaseFirestore.getInstance()

    suspend fun getLocationHistory(
        deviceId: String,
        startDate: Date,
        endDate: Date,
        limit: Int = 100
    ): Result<List<Location>> {
        return try {
            val locations = firestore.collection(FirebaseCollections.DEVICES)
                .document(deviceId)
                .collection(FirebaseCollections.LOCATION_HISTORY)
                .whereGreaterThanOrEqualTo("timestamp", startDate.time)
                .whereLessThanOrEqualTo("timestamp", endDate.time)
                .orderBy("timestamp", Query.Direction.DESCENDING)
                .limit(limit.toLong())
                .get()
                .await()
                .toObjects(Location::class.java)
            Result.success(locations)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun getRecentLocations(deviceId: String, limit: Int = 50): Result<List<Location>> {
        return try {
            val locations = firestore.collection(FirebaseCollections.DEVICES)
                .document(deviceId)
                .collection(FirebaseCollections.LOCATION_HISTORY)
                .orderBy("timestamp", Query.Direction.DESCENDING)
                .limit(limit.toLong())
                .get()
                .await()
                .toObjects(Location::class.java)
            Result.success(locations)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun getLocationById(deviceId: String, timestamp: Long): Result<Location> {
        return try {
            val doc = firestore.collection(FirebaseCollections.DEVICES)
                .document(deviceId)
                .collection(FirebaseCollections.LOCATION_HISTORY)
                .document(timestamp.toString())
                .get()
                .await()
            val location = doc.toObject(Location::class.java)
            if (location != null) {
                Result.success(location)
            } else {
                Result.failure(Exception("Location not found"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
