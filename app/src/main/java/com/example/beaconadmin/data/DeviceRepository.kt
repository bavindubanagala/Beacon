package com.example.beaconadmin.data

import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await

class DeviceRepository(
    private val firestore: FirebaseFirestore = FirebaseFirestore.getInstance()
) {
    suspend fun unpairDevice(deviceId: String): Result<Unit> {
        return try {
            firestore.collection("devices")
                .document(deviceId)
                .update(
                    mapOf(
                        "paired" to false,
                        "adminUid" to null
                    )
                )
                .await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
