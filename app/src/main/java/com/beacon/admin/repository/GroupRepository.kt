package com.beacon.admin.repository

import com.beacon.shared.mapper.toGroup
import com.beacon.shared.models.Group
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await

class GroupRepository(
    private val firestore: FirebaseFirestore
) {
    private val collection = firestore.collection("groups")

    suspend fun getAllGroups(): Result<List<Group>> {
        return try {
            val snapshot = collection.get().await()
            val groups = snapshot.documents.mapNotNull { it.toGroup() }
            Result.success(groups)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
