package com.beacon.admin.repository

import com.beacon.shared.models.Location
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await

class LocationRepository(
    private val firestore: FirebaseFirestore
) {

    suspend fun getLocationsForDevice(deviceId: String): Result<List<Location>> {
        return try {
            val snapshot = firestore
                .collection("locations")
                .whereEqualTo("device_id", deviceId)
                .get()
                .await()

            val locations = snapshot.documents.mapNotNull {
                it.toObject(Location::class.java)
            }

            Result.success(locations)

        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun getLocationsByDate(
        deviceId: String,
        datePrefix: String
    ): Result<List<Location>> {

        return try {
            val snapshot = firestore
                .collection("locations")
                .whereEqualTo("device_id", deviceId)
                .get()
                .await()

            val locations = snapshot.documents.mapNotNull {
                it.toObject(Location::class.java)
            }.filter {
                it.timestamp.toString().startsWith(datePrefix)
            }

            Result.success(locations)

        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
