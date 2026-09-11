package com.beacon.admin.repository

import com.beacon.shared.models.DeviceGroup
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FirebaseGroupRepository @Inject constructor(
    private val firestore: FirebaseFirestore
) {
    private val collection = firestore.collection("device_groups")
    private val auth = FirebaseAuth.getInstance()

    fun getGroups(): Flow<List<DeviceGroup>> {
        if (auth.currentUser == null) return flowOf(emptyList())

        return callbackFlow {
            val subscription = collection.addSnapshotListener { snapshot, error ->
                if (error != null) {
                    android.util.Log.e("GroupRepo", "getGroups error", error)
                    trySend(emptyList())
                    close()
                    return@addSnapshotListener
                }
                val groups = snapshot?.documents?.mapNotNull { doc ->
                    doc.toObject(DeviceGroup::class.java)?.copy(id = doc.id)
                } ?: emptyList()
                trySend(groups)
            }
            awaitClose { subscription.remove() }
        }
    }

    suspend fun createGroup(group: DeviceGroup): Result<Unit> = runCatching {
        collection.add(group).await()
    }.map { Unit }

    suspend fun updateGroup(group: DeviceGroup): Result<Unit> = runCatching {
        collection.document(group.id).set(group).await()
    }.map { Unit }

    suspend fun deleteGroup(groupId: String): Result<Unit> = runCatching {
        collection.document(groupId).delete().await()
    }.map { Unit }

    suspend fun assignDeviceToGroup(deviceId: String, groupId: String): Result<Unit> = runCatching {
        collection.document(groupId).update("deviceIds", FieldValue.arrayUnion(deviceId)).await()
    }.map { Unit }

    suspend fun removeDeviceFromGroup(deviceId: String, groupId: String): Result<Unit> = runCatching {
        collection.document(groupId).update("deviceIds", FieldValue.arrayRemove(deviceId)).await()
    }.map { Unit }
}
