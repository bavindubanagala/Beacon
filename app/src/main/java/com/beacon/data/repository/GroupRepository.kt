package com.beacon.data.repository

import com.beacon.shared.mapper.toGroup
import com.beacon.shared.models.DeviceGroup
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class GroupRepository @Inject constructor(
    private val firestore: FirebaseFirestore
) {
    private val collection = firestore.collection("groups")

    suspend fun getAllGroups(): Result<List<DeviceGroup>> {
        return try {
            val snapshot = collection.get().await()
            val groups = snapshot.documents.mapNotNull { it.toGroup() }
            Result.success(groups)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
