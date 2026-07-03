package com.beacon.admin.repository

import com.beacon.shared.mapper.toDevice
import com.beacon.shared.models.Device
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await

class DeviceRepository(
    private val firestore: FirebaseFirestore
) {
    private val collection = firestore.collection("devices")

    suspend fun getAllDevices(): Result<List<Device>> {
        return try {
            val snapshot = collection.get().await()
            val devices = snapshot.documents.mapNotNull { it.toDevice() }
            Result.success(devices)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
