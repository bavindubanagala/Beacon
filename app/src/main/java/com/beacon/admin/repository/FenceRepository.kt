package com.beacon.admin.repository

import com.beacon.shared.models.Fence
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await

class FenceRepository(private val firestore: FirebaseFirestore) {
    private val collection = firestore.collection("fences")

    suspend fun getAllFences(): Result<List<Fence>> = try {
        val snapshot = collection.get().await()
        val fences = snapshot.documents.mapNotNull { it.toObject(Fence::class.java)?.copy(id = it.id) }
        Result.success(fences)
    } catch (e: Exception) {
        Result.failure(e)
    }

    suspend fun saveFence(fence: Fence): Result<Unit> = try {
        if (fence.id.isEmpty()) {
            collection.add(fence.toMap()).await()
        } else {
            collection.document(fence.id).set(fence.toMap()).await()
        }
        Result.success(Unit)
    } catch (e: Exception) {
        Result.failure(e)
    }

    suspend fun deleteFence(id: String): Result<Unit> = try {
        collection.document(id).delete().await()
        Result.success(Unit)
    } catch (e: Exception) {
        Result.failure(e)
    }
}
